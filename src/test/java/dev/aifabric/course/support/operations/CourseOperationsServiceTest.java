package dev.aifabric.course.support.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.fabric.chat.domain.ChatSession;
import ai.fabric.chat.domain.ChatTurn;
import ai.fabric.chat.service.ChatSessionService;
import ai.fabric.chat.spi.ChatSessionStorageProvider;
import ai.fabric.indexing.IndexingStatus;
import ai.fabric.intent.action.PendingActionStore;
import ai.fabric.migration.repository.MigrationJobRepository;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.repository.IndexingQueueRepository;
import dev.aifabric.course.support.config.CourseSecurityProperties;
import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.demo.CourseDeploymentInfoService;
import dev.aifabric.course.support.identity.CourseAuthorizationService;
import dev.aifabric.course.support.identity.CoursePrincipal;
import dev.aifabric.course.support.identity.CoursePrincipalProvider;
import dev.aifabric.course.support.knowledge.KnowledgeArticle;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

class CourseOperationsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T18:00:00Z");

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CourseDataService dataService = mock(CourseDataService.class);
    private final CourseDeploymentInfoService deploymentInfoService = mock(CourseDeploymentInfoService.class);
    private final VectorDatabaseService vectorDatabaseService = mock(VectorDatabaseService.class);
    private final ChatSessionService chatSessionService = mock(ChatSessionService.class);
    private final ChatSessionStorageProvider chatStorageProvider = mock(ChatSessionStorageProvider.class);
    private final PendingActionStore pendingActionStore = mock(PendingActionStore.class);
    private final MigrationJobRepository migrationJobRepository = mock(MigrationJobRepository.class);
    private final IndexingQueueRepository indexingQueueRepository = mock(IndexingQueueRepository.class);
    private final CoursePrincipalProvider principalProvider = mock(CoursePrincipalProvider.class);
    private final CourseAuthorizationService authorizationService = mock(CourseAuthorizationService.class);
    private final Environment environment = mock(Environment.class);
    private final CourseOperationsProperties operationsProperties = new CourseOperationsProperties();
    private final CourseSecurityProperties securityProperties = securityProperties();
    private CourseOperationsService service;

    @BeforeEach
    void setUp() {
        service = new CourseOperationsService(
            jdbcTemplate,
            dataService,
            deploymentInfoService,
            vectorDatabaseService,
            chatSessionService,
            chatStorageProvider,
            pendingActionStore,
            migrationJobRepository,
            indexingQueueRepository,
            securityProperties,
            operationsProperties,
            principalProvider,
            authorizationService,
            environment,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        stubHealthyComponents();
    }

    @Test
    void readinessReportsEachRequiredStoreAndKeepsDisabledGenerationOptional() {
        CourseOperationsService.OperationsReadiness readiness = service.readiness();

        assertThat(readiness.status()).isEqualTo("READY");
        assertThat(readiness.components()).containsOnlyKeys(
            "build", "database", "vector", "sessions", "indexing", "migration", "generationProvider");
        assertThat(readiness.components().get("database").status()).isEqualTo("UP");
        assertThat(readiness.components().get("vector").details())
            .containsEntry("provider", "memory")
            .containsEntry("knowledgeVectors", 9L);
        assertThat(readiness.components().get("generationProvider").status()).isEqualTo("DISABLED");
        assertThat(readiness.components().get("generationProvider").required()).isFalse();
        assertThat(readiness.components().get("generationProvider").details())
            .containsEntry("credentialConfigured", false)
            .doesNotContainKey("apiKey");
    }

    @Test
    void vectorFailureDoesNotHideHealthyDatabaseOrOtherComponentResults() {
        when(vectorDatabaseService.adminDiagnostics()).thenThrow(new IllegalStateException("provider unavailable"));

        CourseOperationsService.OperationsReadiness readiness = service.readiness();

        assertThat(readiness.status()).isEqualTo("NOT_READY");
        assertThat(readiness.components().get("database").status()).isEqualTo("UP");
        assertThat(readiness.components().get("vector").status()).isEqualTo("DOWN");
        assertThat(readiness.components().get("vector").errorCode()).isEqualTo("VECTOR_CHECK_FAILED");
        assertThat(readiness.components().get("vector").details()).isEmpty();
    }

    @Test
    void enabledCustomGenerationReportsItsEffectiveProviderWithoutInventingCredentialRequirements() {
        when(environment.getProperty("ai.service.features.enable-generation", Boolean.class, false))
            .thenReturn(true);
        when(environment.getProperty("ai.providers.generation.llm-provider"))
            .thenReturn("course-generation-test");

        CourseOperationsService.ComponentReadiness generation =
            service.readiness().components().get("generationProvider");

        assertThat(generation.status()).isEqualTo("UP");
        assertThat(generation.required()).isTrue();
        assertThat(generation.details())
            .containsEntry("selectedProvider", "course-generation-test")
            .containsEntry("credentialRequired", false)
            .containsEntry("credentialConfigured", false);
    }

    @Test
    void releaseProbeIsExplicitlyNonModelAndStoredInBackendConversationMemory() {
        operationsProperties.setReleaseProbesEnabled(true);
        CoursePrincipal admin = admin();
        when(principalProvider.currentPrincipal()).thenReturn(admin);
        when(authorizationService.requireScope(admin, "migration:admin")).thenReturn(admin);
        ChatSession persisted = session("course-release-probe", admin.userId(), NOW.minusSeconds(1));
        persisted.setTurns(new ArrayList<>(List.of(mock(ChatTurn.class))));
        when(chatStorageProvider.findById(any())).thenReturn(java.util.Optional.of(persisted));

        CourseOperationsService.ReleaseProbe result = service.createReleaseProbe();

        assertThat(result.ownerId()).isEqualTo(admin.userId());
        assertThat(result.storedTurns()).isOne();
        assertThat(result.modelInvoked()).isFalse();
        verify(chatSessionService).recordTurn(
            any(),
            eq(admin.userId()),
            eq("Verify persisted conversation state after restart."),
            eq("Release persistence probe recorded. No language model was invoked."),
            org.mockito.ArgumentMatchers.argThat(metadata ->
                Boolean.TRUE.equals(metadata.get("courseReleaseProbe"))
                    && Boolean.FALSE.equals(metadata.get("modelInvoked")))
        );
    }

    @Test
    void retentionDeletesOnlyExpiredOperationalRecordsAndPreservesSourceAndVectors() {
        operationsProperties.setMaintenanceEnabled(true);
        operationsProperties.setCompletedRecordRetention(Duration.ZERO);
        operationsProperties.setConversationRetention(Duration.ZERO);
        CoursePrincipal admin = admin();
        when(principalProvider.currentPrincipal()).thenReturn(admin);
        when(authorizationService.requireScope(admin, "migration:admin")).thenReturn(admin);

        CourseDataService.DatasetSnapshot source = new CourseDataService.DatasetSnapshot(9, 2, 2, 2);
        when(dataService.snapshot()).thenReturn(source, source);
        when(vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE))
            .thenReturn(9L, 9L);
        when(migrationJobRepository.count()).thenReturn(2L, 0L);
        when(indexingQueueRepository.count()).thenReturn(11L, 0L);

        ChatSession expired = session("expired-course-session", "customer-alex", NOW.minusSeconds(5));
        when(chatStorageProvider.findByOwnerId("customer-alex"))
            .thenReturn(List.of(expired), List.of(expired), List.of());

        CourseOperationsService.RetentionResult result = service.cleanupRetainedOperationalState();

        assertThat(result.sourceRecordsPreserved()).isTrue();
        assertThat(result.vectorsPreserved()).isTrue();
        assertThat(result.migrationJobs().removed()).isEqualTo(2);
        assertThat(result.indexingEntries().removed()).isEqualTo(11);
        assertThat(result.courseSessions().removed()).isOne();
        verify(chatSessionService).deleteConversation("expired-course-session", "customer-alex");
        verify(migrationJobRepository).deleteByCompletedAtBefore(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(indexingQueueRepository).deleteByStatusAndCompletedAtBefore(
            IndexingStatus.COMPLETED, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(indexingQueueRepository).deleteByStatusAndUpdatedAtBefore(
            IndexingStatus.FAILED, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(indexingQueueRepository).deleteByStatusAndUpdatedAtBefore(
            IndexingStatus.DEAD_LETTER, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    private void stubHealthyComponents() {
        when(deploymentInfoService.health()).thenReturn(new CourseDeploymentInfoService.DeploymentHealth(
            "UP", "course-app", "0.3.3-course.1-SNAPSHOT", "0.3.3", "0123456789abcdef", "main",
            "2026-07-22T17:00:00Z", "2026-07-22T17:30:00Z", NOW.toString(),
            List.of("operations", "qdrant"), "course-0.3.3-p08-production-ready",
            new CourseDeploymentInfoService.ProviderPosture(
                "production-keyless", false, "disabled", "disabled", "disabled", "disabled",
                "onnx", "qdrant", false)
        ));
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(dataService.snapshot()).thenReturn(new CourseDataService.DatasetSnapshot(9, 2, 2, 2));
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(Map.of(
            "provider", "memory", "nativeClient", "concurrent-map"));
        when(vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE)).thenReturn(9L);
        when(vectorDatabaseService.vectorDurableStorage()).thenReturn(false);
        when(chatStorageProvider.findByOwnerId(any())).thenReturn(List.of());
        when(migrationJobRepository.findByStatusIn(any())).thenReturn(List.of());
        when(environment.getProperty("ai.service.features.enable-generation", Boolean.class, false))
            .thenReturn(false);
        when(environment.getProperty("ai.providers.llm-provider", "")).thenReturn("");
        when(environment.getProperty("ai.providers.enable-fallback", Boolean.class, false)).thenReturn(false);
    }

    private CoursePrincipal admin() {
        return new CoursePrincipal(
            "customer-alex", "tenant-blue", "course-session-alex",
            List.of("migration:admin"), List.of("CUSTOMER"));
    }

    private ChatSession session(String id, String owner, Instant lastInteraction) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setOwnerId(owner);
        session.setCreatedAt(LocalDateTime.ofInstant(lastInteraction.minusSeconds(30), ZoneOffset.UTC));
        session.setLastInteractionAt(LocalDateTime.ofInstant(lastInteraction, ZoneOffset.UTC));
        session.setTurns(new ArrayList<>());
        return session;
    }

    private CourseSecurityProperties securityProperties() {
        CourseSecurityProperties properties = new CourseSecurityProperties();
        CourseSecurityProperties.PrincipalDefinition definition =
            new CourseSecurityProperties.PrincipalDefinition();
        definition.setToken("token");
        definition.setUserId("customer-alex");
        definition.setTenantId("tenant-blue");
        definition.setSessionId("session");
        definition.setScopes(List.of("migration:admin"));
        definition.setRoles(List.of("CUSTOMER"));
        properties.setPrincipals(List.of(definition));
        return properties;
    }
}
