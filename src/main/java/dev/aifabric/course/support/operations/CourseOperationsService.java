package dev.aifabric.course.support.operations;

import ai.fabric.chat.domain.ChatSession;
import ai.fabric.chat.service.ChatSessionService;
import ai.fabric.chat.spi.ChatSessionStorageProvider;
import ai.fabric.indexing.IndexingStatus;
import ai.fabric.intent.action.PendingActionStore;
import ai.fabric.migration.domain.MigrationStatus;
import ai.fabric.migration.repository.MigrationJobRepository;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.repository.IndexingQueueRepository;
import dev.aifabric.course.support.config.CourseSecurityProperties;
import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.demo.CourseDeploymentInfoService;
import dev.aifabric.course.support.demo.CourseReadinessService;
import dev.aifabric.course.support.identity.CourseAccessDeniedException;
import dev.aifabric.course.support.identity.CourseAuthorizationService;
import dev.aifabric.course.support.identity.CoursePrincipal;
import dev.aifabric.course.support.identity.CoursePrincipalProvider;
import dev.aifabric.course.support.knowledge.KnowledgeArticle;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CourseOperationsService {

    private static final String ADMIN_SCOPE = "migration:admin";
    private static final List<MigrationStatus> ACTIVE_MIGRATIONS = List.of(
        MigrationStatus.PENDING,
        MigrationStatus.RUNNING,
        MigrationStatus.PAUSED
    );

    private final JdbcTemplate jdbcTemplate;
    private final CourseDataService dataService;
    private final CourseDeploymentInfoService deploymentInfoService;
    private final VectorDatabaseService vectorDatabaseService;
    private final ChatSessionService chatSessionService;
    private final ChatSessionStorageProvider chatStorageProvider;
    private final PendingActionStore pendingActionStore;
    private final MigrationJobRepository migrationJobRepository;
    private final IndexingQueueRepository indexingQueueRepository;
    private final CourseSecurityProperties securityProperties;
    private final CourseOperationsProperties operationsProperties;
    private final CoursePrincipalProvider principalProvider;
    private final CourseAuthorizationService authorizationService;
    private final Environment environment;
    private final Clock clock;

    public CourseOperationsService(JdbcTemplate jdbcTemplate,
                                   CourseDataService dataService,
                                   CourseDeploymentInfoService deploymentInfoService,
                                   VectorDatabaseService vectorDatabaseService,
                                   ChatSessionService chatSessionService,
                                   ChatSessionStorageProvider chatStorageProvider,
                                   PendingActionStore pendingActionStore,
                                   MigrationJobRepository migrationJobRepository,
                                   IndexingQueueRepository indexingQueueRepository,
                                   CourseSecurityProperties securityProperties,
                                   CourseOperationsProperties operationsProperties,
                                   CoursePrincipalProvider principalProvider,
                                   CourseAuthorizationService authorizationService,
                                   Environment environment,
                                   Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataService = dataService;
        this.deploymentInfoService = deploymentInfoService;
        this.vectorDatabaseService = vectorDatabaseService;
        this.chatSessionService = chatSessionService;
        this.chatStorageProvider = chatStorageProvider;
        this.pendingActionStore = pendingActionStore;
        this.migrationJobRepository = migrationJobRepository;
        this.indexingQueueRepository = indexingQueueRepository;
        this.securityProperties = securityProperties;
        this.operationsProperties = operationsProperties;
        this.principalProvider = principalProvider;
        this.authorizationService = authorizationService;
        this.environment = environment;
        this.clock = clock;
    }

    public OperationsReadiness readiness() {
        Map<String, ComponentReadiness> components = new LinkedHashMap<>();
        components.put("build", component("BUILD_IDENTITY_UNAVAILABLE", this::buildDetails));
        components.put("database", component("DATABASE_CHECK_FAILED", this::databaseDetails));
        components.put("vector", component("VECTOR_CHECK_FAILED", this::vectorDetails));
        components.put("sessions", component("SESSION_CHECK_FAILED", this::sessionDetails));
        components.put("indexing", component("INDEXING_CHECK_FAILED", this::indexingDetails));
        components.put("migration", component("MIGRATION_CHECK_FAILED", this::migrationDetails));
        components.put("generationProvider", generationReadiness());

        boolean ready = components.values().stream()
            .filter(ComponentReadiness::required)
            .allMatch(item -> "UP".equals(item.status()));
        return new OperationsReadiness(
            CourseReadinessService.CHECKPOINT,
            ready ? "READY" : "NOT_READY",
            Instant.now(clock).toString(),
            Map.copyOf(components)
        );
    }

    @Transactional
    public ReleaseProbe createReleaseProbe() {
        CoursePrincipal admin = requireAdmin();
        if (!operationsProperties.isReleaseProbesEnabled()) {
            throw new CourseAccessDeniedException("Release probes are disabled");
        }
        String conversationId = "course-release-probe-" + UUID.randomUUID();
        chatSessionService.recordTurn(
            conversationId,
            admin.userId(),
            "Verify persisted conversation state after restart.",
            "Release persistence probe recorded. No language model was invoked.",
            Map.of(
                "courseReleaseProbe", true,
                "modelInvoked", false,
                "checkpoint", CourseReadinessService.CHECKPOINT
            )
        );
        ChatSession session = chatStorageProvider.findById(conversationId)
            .orElseThrow(() -> new IllegalStateException("Release probe was not persisted"));
        return new ReleaseProbe(
            conversationId,
            admin.userId(),
            session.getTurns().size(),
            false,
            session.getLastInteractionAt()
        );
    }

    @Transactional
    public RetentionResult cleanupRetainedOperationalState() {
        requireAdmin();
        if (!operationsProperties.isMaintenanceEnabled()) {
            throw new CourseAccessDeniedException("Operations maintenance is disabled");
        }

        CourseDataService.DatasetSnapshot sourceBefore = dataService.snapshot();
        long vectorsBefore = vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE);
        long migrationBefore = migrationJobRepository.count();
        long indexingBefore = indexingQueueRepository.count();
        int sessionsBefore = courseSessions().size();

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDateTime recordCutoff = now.minus(operationsProperties.getCompletedRecordRetention());
        LocalDateTime conversationCutoff = now.minus(operationsProperties.getConversationRetention());

        List<ChatSession> expiredSessions = courseSessions().stream()
            .filter(session -> !session.getLastInteractionAt().isAfter(conversationCutoff))
            .toList();
        expiredSessions.forEach(session -> chatSessionService.deleteConversation(session.getId(), session.getOwnerId()));

        migrationJobRepository.deleteByCompletedAtBefore(recordCutoff);
        indexingQueueRepository.deleteByStatusAndCompletedAtBefore(IndexingStatus.COMPLETED, recordCutoff);
        indexingQueueRepository.deleteByStatusAndUpdatedAtBefore(IndexingStatus.FAILED, recordCutoff);
        indexingQueueRepository.deleteByStatusAndUpdatedAtBefore(IndexingStatus.DEAD_LETTER, recordCutoff);

        CourseDataService.DatasetSnapshot sourceAfter = dataService.snapshot();
        long vectorsAfter = vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE);
        long migrationAfter = migrationJobRepository.count();
        long indexingAfter = indexingQueueRepository.count();
        int sessionsAfter = courseSessions().size();

        return new RetentionResult(
            recordCutoff,
            conversationCutoff,
            new CleanupCount(migrationBefore, migrationAfter, migrationBefore - migrationAfter),
            new CleanupCount(indexingBefore, indexingAfter, indexingBefore - indexingAfter),
            new CleanupCount(sessionsBefore, sessionsAfter, sessionsBefore - sessionsAfter),
            sourceBefore,
            sourceAfter,
            sourceBefore.equals(sourceAfter),
            vectorsBefore,
            vectorsAfter,
            vectorsBefore == vectorsAfter
        );
    }

    private ComponentReadiness component(String errorCode, Supplier<Map<String, Object>> supplier) {
        try {
            return new ComponentReadiness("UP", true, supplier.get(), null);
        } catch (RuntimeException exception) {
            return new ComponentReadiness("DOWN", true, Map.of(), errorCode);
        }
    }

    private Map<String, Object> buildDetails() {
        CourseDeploymentInfoService.DeploymentHealth health = deploymentInfoService.health();
        if (!StringUtils.hasText(health.commit()) || "unknown".equalsIgnoreCase(health.commit())) {
            throw new IllegalStateException("Build commit is unavailable");
        }
        return Map.of(
            "version", health.version(),
            "aiFabricVersion", health.aiFabricVersion(),
            "commit", health.commit(),
            "branch", health.branch(),
            "builtAt", health.builtAt()
        );
    }

    private Map<String, Object> databaseDetails() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        if (!Integer.valueOf(1).equals(result)) {
            throw new IllegalStateException("Database probe returned an unexpected result");
        }
        return Map.of(
            "connection", "verified",
            "sourceRecords", dataService.snapshot(),
            "sourceOfTruth", "application-jpa"
        );
    }

    private Map<String, Object> vectorDetails() {
        Map<String, Object> diagnostics = vectorDatabaseService.adminDiagnostics();
        return Map.of(
            "provider", textOr(diagnostics.get("provider"), vectorDatabaseService.vectorProviderName()),
            "nativeClient", textOr(diagnostics.get("nativeClient"), vectorDatabaseService.vectorNativeClient()),
            "knowledgeVectors", vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE),
            "durable", vectorDatabaseService.vectorDurableStorage(),
            "derivedRebuildableState", true
        );
    }

    private Map<String, Object> sessionDetails() {
        return Map.of(
            "storageProvider", chatStorageProvider.getClass().getSimpleName(),
            "pendingActionStore", pendingActionStore.getClass().getSimpleName(),
            "configuredOwners", securityProperties.getPrincipals().size(),
            "courseSessions", courseSessions().size()
        );
    }

    private Map<String, Object> indexingDetails() {
        return Map.of(
            "total", indexingQueueRepository.count(),
            "pending", indexingQueueRepository.countByStatus(IndexingStatus.PENDING),
            "processing", indexingQueueRepository.countByStatus(IndexingStatus.PROCESSING),
            "completed", indexingQueueRepository.countByStatus(IndexingStatus.COMPLETED),
            "failed", indexingQueueRepository.countByStatus(IndexingStatus.FAILED),
            "deadLetter", indexingQueueRepository.countByStatus(IndexingStatus.DEAD_LETTER)
        );
    }

    private Map<String, Object> migrationDetails() {
        return Map.of(
            "total", migrationJobRepository.count(),
            "active", migrationJobRepository.findByStatusIn(ACTIVE_MIGRATIONS).size(),
            "durableStore", "application-jpa"
        );
    }

    private ComponentReadiness generationReadiness() {
        boolean enabled = environment.getProperty("ai.service.features.enable-generation", Boolean.class, false);
        String selectedProvider = selectedGenerationProvider();
        boolean openAiSelected = "openai".equalsIgnoreCase(selectedProvider);
        boolean keyConfigured = StringUtils.hasText(environment.getProperty("ai.providers.openai.api-key"));
        Map<String, Object> details = Map.of(
            "enabled", enabled,
            "selectedProvider", enabled ? selectedProvider : "disabled",
            "credentialRequired", enabled && openAiSelected,
            "credentialConfigured", keyConfigured,
            "fallbackEnabled", environment.getProperty("ai.providers.enable-fallback", Boolean.class, false)
        );
        if (enabled && openAiSelected && !keyConfigured) {
            return new ComponentReadiness("DOWN", true, details, "OPENAI_CREDENTIAL_MISSING");
        }
        return new ComponentReadiness(enabled ? "UP" : "DISABLED", enabled, details, null);
    }

    private String selectedGenerationProvider() {
        String purposeProvider = environment.getProperty("ai.providers.generation.llm-provider");
        if (StringUtils.hasText(purposeProvider)) {
            return purposeProvider.trim();
        }
        String defaultProvider = environment.getProperty("ai.providers.llm-provider");
        return StringUtils.hasText(defaultProvider) ? defaultProvider.trim() : "unconfigured";
    }

    private List<ChatSession> courseSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        securityProperties.getPrincipals().stream()
            .map(CourseSecurityProperties.PrincipalDefinition::getUserId)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .forEach(owner -> sessions.addAll(chatStorageProvider.findByOwnerId(owner)));
        return List.copyOf(sessions);
    }

    private CoursePrincipal requireAdmin() {
        return authorizationService.requireScope(principalProvider.currentPrincipal(), ADMIN_SCOPE);
    }

    private String textOr(Object value, String fallback) {
        String candidate = value == null ? "" : String.valueOf(value).trim();
        return candidate.isBlank() ? fallback : candidate;
    }

    public record OperationsReadiness(
        String checkpoint,
        String status,
        String checkedAt,
        Map<String, ComponentReadiness> components
    ) {
        public OperationsReadiness {
            components = components == null ? Map.of() : Map.copyOf(components);
        }
    }

    public record ComponentReadiness(
        String status,
        boolean required,
        Map<String, Object> details,
        String errorCode
    ) {
        public ComponentReadiness {
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }

    public record ReleaseProbe(
        String conversationId,
        String ownerId,
        int storedTurns,
        boolean modelInvoked,
        LocalDateTime persistedAt
    ) {
    }

    public record RetentionResult(
        LocalDateTime completedRecordCutoff,
        LocalDateTime conversationCutoff,
        CleanupCount migrationJobs,
        CleanupCount indexingEntries,
        CleanupCount courseSessions,
        CourseDataService.DatasetSnapshot sourceBefore,
        CourseDataService.DatasetSnapshot sourceAfter,
        boolean sourceRecordsPreserved,
        long vectorsBefore,
        long vectorsAfter,
        boolean vectorsPreserved
    ) {
    }

    public record CleanupCount(long before, long after, long removed) {
    }
}
