package dev.aifabric.course.support.migration;

import ai.fabric.migration.domain.MigrationFilters;
import ai.fabric.migration.domain.MigrationJob;
import ai.fabric.migration.domain.MigrationProgress;
import ai.fabric.migration.domain.MigrationRequest;
import ai.fabric.migration.domain.MigrationStatus;
import ai.fabric.migration.repository.MigrationJobRepository;
import ai.fabric.migration.service.DataMigrationService;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.indexing.IndexingStatus;
import ai.fabric.repository.IndexingQueueRepository;
import dev.aifabric.course.support.identity.CourseAuthorizationService;
import dev.aifabric.course.support.identity.CoursePrincipal;
import dev.aifabric.course.support.identity.CoursePrincipalProvider;
import dev.aifabric.course.support.knowledge.KnowledgeArticle;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeMigrationService {

    private static final String ADMIN_SCOPE = "migration:admin";

    private final DataMigrationService migrationService;
    private final MigrationJobRepository jobRepository;
    private final VectorDatabaseService vectorDatabaseService;
    private final IndexingQueueRepository queueRepository;
    private final CoursePrincipalProvider principalProvider;
    private final CourseAuthorizationService authorizationService;

    public KnowledgeMigrationService(DataMigrationService migrationService,
                                     MigrationJobRepository jobRepository,
                                     VectorDatabaseService vectorDatabaseService,
                                     IndexingQueueRepository queueRepository,
                                     CoursePrincipalProvider principalProvider,
                                     CourseAuthorizationService authorizationService) {
        this.migrationService = migrationService;
        this.jobRepository = jobRepository;
        this.vectorDatabaseService = vectorDatabaseService;
        this.queueRepository = queueRepository;
        this.principalProvider = principalProvider;
        this.authorizationService = authorizationService;
    }

    public MigrationView start(StartMigrationRequest request) {
        CoursePrincipal admin = requireAdmin();
        StartMigrationRequest safeRequest = request != null ? request : StartMigrationRequest.defaults();
        MigrationJob job = migrationService.startMigration(MigrationRequest.builder()
            .entityType(KnowledgeArticle.ENTITY_TYPE)
            .batchSize(safeRequest.effectiveBatchSize())
            .rateLimit(safeRequest.effectiveRateLimit())
            .reindexExisting(safeRequest.effectiveReindexExisting())
            .filters(safeRequest.filters())
            .createdBy(admin.userId())
            .build());
        return view(job);
    }

    public List<MigrationView> list() {
        requireAdmin();
        return StreamSupport.stream(migrationService.listJobs().spliterator(), false)
            .filter(job -> KnowledgeArticle.ENTITY_TYPE.equals(job.getEntityType()))
            .sorted(Comparator.comparing(MigrationJob::getStartedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .map(this::view)
            .toList();
    }

    public MigrationView get(String jobId) {
        requireAdmin();
        return view(requireKnowledgeJob(jobId));
    }

    public MigrationView pause(String jobId) {
        requireAdmin();
        requireKnowledgeJob(jobId);
        transition(jobId, () -> migrationService.pauseMigration(jobId));
        return view(requireKnowledgeJob(jobId));
    }

    public MigrationView resume(String jobId) {
        requireAdmin();
        requireKnowledgeJob(jobId);
        transition(jobId, () -> migrationService.resumeMigration(jobId));
        return view(requireKnowledgeJob(jobId));
    }

    public MigrationView cancel(String jobId) {
        requireAdmin();
        requireKnowledgeJob(jobId);
        transition(jobId, () -> migrationService.cancelMigration(jobId));
        return view(requireKnowledgeJob(jobId));
    }

    private void transition(String jobId, Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException exception) {
            throw new MigrationTransitionException(
                "Migration job " + jobId + " cannot make the requested transition", exception);
        }
    }

    private MigrationJob requireKnowledgeJob(String jobId) {
        MigrationJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new MigrationJobNotFoundException(jobId));
        if (!KnowledgeArticle.ENTITY_TYPE.equals(job.getEntityType())) {
            throw new MigrationJobNotFoundException(jobId);
        }
        return job;
    }

    private CoursePrincipal requireAdmin() {
        return authorizationService.requireScope(principalProvider.currentPrincipal(), ADMIN_SCOPE);
    }

    private MigrationView view(MigrationJob job) {
        MigrationProgress progress = migrationService.getProgress(job.getId());
        long indexedVectors = vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE);
        long pending = queueRepository.countByStatus(IndexingStatus.PENDING);
        long processing = queueRepository.countByStatus(IndexingStatus.PROCESSING);
        boolean unfiltered = job.getFilters() == null || job.getFilters().isEmpty();
        return new MigrationView(
            job.getId(),
            job.getEntityType(),
            progress.getStatus().name(),
            progress.getTotal(),
            progress.getProcessed(),
            progress.getFailed(),
            progress.getPercentComplete(),
            progress.getEstimatedTimeRemaining(),
            indexedVectors,
            pending,
            processing,
            queueRepository.countByStatus(IndexingStatus.COMPLETED),
            queueRepository.countByStatus(IndexingStatus.DEAD_LETTER),
            progress.getStatus() == MigrationStatus.COMPLETED && pending == 0 && processing == 0,
            unfiltered ? indexedVectors >= progress.getTotal() - progress.getFailed() : null,
            job.getBatchSize(),
            job.getRateLimit(),
            Boolean.TRUE.equals(job.getReindexExisting()),
            job.getCreatedBy(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getErrorMessage(),
            "AI Fabric 0.3.3 reports scanned and failed rows; it does not expose an exact per-job skipped count."
        );
    }

    public record StartMigrationRequest(
        @Min(1) @Max(500) Integer batchSize,
        @Min(0) Integer rateLimit,
        Boolean reindexExisting,
        LocalDate createdAfter,
        LocalDate createdBefore,
        List<@NotBlank String> entityIds
    ) {
        static StartMigrationRequest defaults() {
            return new StartMigrationRequest(25, 0, false, null, null, List.of());
        }

        int effectiveBatchSize() {
            return batchSize != null ? batchSize : 25;
        }

        int effectiveRateLimit() {
            return rateLimit != null ? rateLimit : 0;
        }

        boolean effectiveReindexExisting() {
            return Boolean.TRUE.equals(reindexExisting);
        }

        MigrationFilters filters() {
            List<String> ids = entityIds == null ? List.of() : entityIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
            if (createdAfter == null && createdBefore == null && ids.isEmpty()) {
                return null;
            }
            return MigrationFilters.builder()
                .createdAfter(createdAfter)
                .createdBefore(createdBefore)
                .entityIds(ids)
                .build();
        }
    }

    public record MigrationView(
        String jobId,
        String entityType,
        String status,
        long totalSourceRows,
        long processedSourceRows,
        long failedRows,
        double percentComplete,
        Duration estimatedTimeRemaining,
        long currentIndexedVectors,
        long pendingQueueEntries,
        long processingQueueEntries,
        long completedQueueEntries,
        long deadLetterQueueEntries,
        boolean indexingCaughtUp,
        Boolean fullSourceVectorCoverage,
        int batchSize,
        Integer rateLimit,
        boolean reindexExisting,
        String createdBy,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage,
        String skipAccounting
    ) { }
}
