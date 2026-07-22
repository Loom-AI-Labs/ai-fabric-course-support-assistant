package dev.aifabric.course.support.knowledge;

import ai.fabric.datasync.dto.DataSyncBatchRequest;
import ai.fabric.datasync.dto.DataSyncBatchResponse;
import ai.fabric.datasync.dto.DataSyncDeleteRequest;
import ai.fabric.datasync.dto.DataSyncIdentity;
import ai.fabric.datasync.dto.DataSyncOperation;
import ai.fabric.datasync.dto.DataSyncOperationResponse;
import ai.fabric.datasync.dto.DataSyncOperationType;
import ai.fabric.datasync.dto.DataSyncTrace;
import ai.fabric.datasync.dto.DataSyncUpsertRequest;
import ai.fabric.datasync.dto.DataSyncVerifiedAuthContext;
import ai.fabric.datasync.service.DataSyncService;
import ai.fabric.rag.VectorDatabaseService;
import dev.aifabric.course.support.identity.CourseAuthorizationService;
import dev.aifabric.course.support.identity.CoursePrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeDataSyncService {

    private static final String UPSERT_SCOPE = "data-sync:upsert";
    private static final String DELETE_SCOPE = "data-sync:delete";

    private final KnowledgeArticleRepository articleRepository;
    private final DataSyncService dataSyncService;
    private final VectorDatabaseService vectorDatabaseService;
    private final CourseAuthorizationService authorizationService;

    public KnowledgeDataSyncService(KnowledgeArticleRepository articleRepository,
                                    DataSyncService dataSyncService,
                                    VectorDatabaseService vectorDatabaseService,
                                    CourseAuthorizationService authorizationService) {
        this.articleRepository = articleRepository;
        this.dataSyncService = dataSyncService;
        this.vectorDatabaseService = vectorDatabaseService;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public SyncedArticle create(CreateArticleRequest request, CoursePrincipal principal) {
        CoursePrincipal authorized = authorizationService.requireScope(principal, UPSERT_SCOPE);
        if (articleRepository.existsById(request.id())) {
            throw new DataSyncOperationException("SOURCE_CONFLICT", "Article already exists: " + request.id());
        }
        KnowledgeArticle article = articleRepository.saveAndFlush(new KnowledgeArticle(
            request.id(), request.title(), request.body(), request.category(), authorized.tenantId(),
            "PUBLISHED", "INTERNAL", true, null, LocalDateTime.now()
        ));
        DataSyncUpsertRequest syncRequest = upsert(article, authorized);
        SyncEvidence sync = requireSuccess(
            dataSyncService.upsert(syncRequest), syncRequest.getTrace().getRequestId());
        requireVector(article.getId());
        return new SyncedArticle(KnowledgeEvidenceService.PublicArticle.from(article), sync);
    }

    @Transactional
    public SyncedArticle update(String id, UpdateArticleRequest request, CoursePrincipal principal) {
        CoursePrincipal authorized = authorizationService.requireScope(principal, UPSERT_SCOPE);
        KnowledgeArticle article = articleRepository.findByIdAndTenantId(id, authorized.tenantId())
            .orElseThrow(() -> new ArticleNotFoundException(id));
        article.setTitle(request.title());
        article.setBody(request.body());
        articleRepository.saveAndFlush(article);
        DataSyncUpsertRequest syncRequest = upsert(article, authorized);
        SyncEvidence sync = requireSuccess(
            dataSyncService.upsert(syncRequest), syncRequest.getTrace().getRequestId());
        requireVector(article.getId());
        return new SyncedArticle(KnowledgeEvidenceService.PublicArticle.from(article), sync);
    }

    @Transactional
    public SyncEvidence delete(String id, CoursePrincipal principal) {
        CoursePrincipal authorized = authorizationService.requireScope(principal, DELETE_SCOPE);
        KnowledgeArticle article = articleRepository.findByIdAndTenantId(id, authorized.tenantId())
            .orElseThrow(() -> new ArticleNotFoundException(id));
        DataSyncDeleteRequest syncRequest = deleteRequest(article, authorized);
        SyncEvidence sync = requireSuccess(
            dataSyncService.delete(syncRequest), syncRequest.getTrace().getRequestId());
        articleRepository.delete(article);
        articleRepository.flush();
        if (vectorDatabaseService.vectorExists(KnowledgeArticle.ENTITY_TYPE, id)) {
            throw new EvidenceOperationException("Vector still exists after data-sync delete for " + id);
        }
        return sync;
    }

    @Transactional(readOnly = true)
    public BatchSyncView reconcile(ReconcileRequest request, CoursePrincipal principal) {
        CoursePrincipal authorized = authorizationService.requireScope(principal, UPSERT_SCOPE);
        List<KnowledgeArticle> articles = request.articleIds().stream()
            .map(String::trim)
            .distinct()
            .map(id -> articleRepository.findByIdAndTenantId(id, authorized.tenantId())
                .orElseThrow(() -> new ArticleNotFoundException(id)))
            .toList();
        DataSyncTrace trace = trace(authorized);
        List<DataSyncOperation> operations = articles.stream()
            .map(article -> operation(article))
            .toList();
        return batchView(dataSyncService.batch(new DataSyncBatchRequest(trace, operations)));
    }

    private DataSyncUpsertRequest upsert(KnowledgeArticle article, CoursePrincipal principal) {
        return new DataSyncUpsertRequest(
            KnowledgeArticle.ENTITY_TYPE,
            article.getId(),
            null,
            entity(article),
            metadata(article),
            identity(article),
            trace(principal)
        );
    }

    private DataSyncDeleteRequest deleteRequest(KnowledgeArticle article, CoursePrincipal principal) {
        return new DataSyncDeleteRequest(
            KnowledgeArticle.ENTITY_TYPE,
            article.getId(),
            identity(article),
            trace(principal)
        );
    }

    private DataSyncOperation operation(KnowledgeArticle article) {
        return new DataSyncOperation(
            DataSyncOperationType.UPSERT,
            KnowledgeArticle.ENTITY_TYPE,
            article.getId(),
            null,
            entity(article),
            metadata(article),
            identity(article)
        );
    }

    private Map<String, Object> entity(KnowledgeArticle article) {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("id", article.getId());
        entity.put("title", article.getTitle());
        entity.put("body", article.getBody());
        entity.put("category", article.getCategory());
        entity.put("tenantId", article.getTenantId());
        entity.put("status", article.getStatus());
        entity.put("visibility", article.getVisibility());
        entity.put("visibleToUser", article.isVisibleToUser());
        return Map.copyOf(entity);
    }

    private Map<String, Object> metadata(KnowledgeArticle article) {
        return Map.of(
            "tenantId", article.getTenantId(),
            "visibleToUser", article.isVisibleToUser(),
            "status", article.getStatus(),
            "visibility", article.getVisibility(),
            "category", article.getCategory(),
            "title", article.getTitle()
        );
    }

    private DataSyncIdentity identity(KnowledgeArticle article) {
        return new DataSyncIdentity(
            article.getId(),
            Long.toString(article.getVersion()),
            null,
            null,
            sha256(article.getTitle() + "\n" + article.getBody())
        );
    }

    private DataSyncTrace trace(CoursePrincipal principal) {
        DataSyncVerifiedAuthContext auth = new DataSyncVerifiedAuthContext();
        auth.setSubjectId(principal.userId());
        auth.setSubjectType("USER");
        auth.setAuthMode("BEARER");
        auth.setCallerType("APPLICATION_USER");
        auth.setSessionId(principal.sessionId());
        auth.setTenantId(principal.tenantId());
        auth.setIssuer("course-support-assistant");
        auth.setGrantedScopes(principal.scopes());

        DataSyncTrace trace = new DataSyncTrace();
        trace.setRequestId("course-sync-" + UUID.randomUUID());
        trace.setMetadata(Map.of("source", "application-owned-knowledge-api"));
        trace.setAuthContext(auth);
        return trace;
    }

    private SyncEvidence requireSuccess(DataSyncOperationResponse response, String requestId) {
        SyncEvidence evidence = syncEvidence(response, requestId);
        if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
            throw new DataSyncOperationException(
                response != null ? response.getErrorCode() : "DATA_SYNC_FAILED",
                response != null ? response.getMessage() : "Data sync returned no response"
            );
        }
        return evidence;
    }

    private void requireVector(String id) {
        if (!vectorDatabaseService.vectorExists(KnowledgeArticle.ENTITY_TYPE, id)) {
            throw new EvidenceOperationException("Data sync did not store article vector " + id);
        }
    }

    private BatchSyncView batchView(DataSyncBatchResponse response) {
        if (response == null) {
            throw new DataSyncOperationException("DATA_SYNC_FAILED", "Data sync returned no batch response");
        }
        List<SyncEvidence> results = response.getResults() == null
            ? List.of()
            : response.getResults().stream().map(result -> syncEvidence(result, null)).toList();
        return new BatchSyncView(
            Boolean.TRUE.equals(response.getSuccess()),
            response.getErrorCode(),
            response.getMessage(),
            response.getProviderRequestId(),
            response.getTotalOperations(),
            response.getSucceededOperations(),
            response.getFailedOperations(),
            results
        );
    }

    private SyncEvidence syncEvidence(DataSyncOperationResponse response, String requestId) {
        if (response == null) {
            return new SyncEvidence(false, "DATA_SYNC_FAILED", "No operation response", null,
                KnowledgeArticle.ENTITY_TYPE, null, null, requestId, null, null);
        }
        String idempotencyKey = null;
        String sourceVersion = null;
        if (response.getMetadata() != null) {
            Object idempotency = response.getMetadata().get("_dataSyncIdempotencyKey");
            Object version = response.getMetadata().get("_dataSyncSourceRecordVersion");
            idempotencyKey = idempotency != null ? String.valueOf(idempotency) : null;
            sourceVersion = version != null ? String.valueOf(version) : null;
        }
        return new SyncEvidence(
            Boolean.TRUE.equals(response.getSuccess()),
            response.getErrorCode(),
            response.getMessage(),
            response.getType() != null ? response.getType().name() : null,
            response.getVectorSpace(),
            response.getId(),
            response.getVectorId(),
            requestId,
            idempotencyKey,
            sourceVersion
        );
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record CreateArticleRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{2,63}") String id,
        @NotBlank @Size(max = 500) String title,
        @NotBlank @Size(max = 8_000) String body,
        @NotBlank @Size(max = 100) String category
    ) { }

    public record UpdateArticleRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank @Size(max = 8_000) String body
    ) { }

    public record ReconcileRequest(@NotEmpty List<@NotBlank String> articleIds) { }

    public record SyncedArticle(KnowledgeEvidenceService.PublicArticle article, SyncEvidence sync) { }

    public record SyncEvidence(boolean success, String errorCode, String message, String operation,
                               String vectorSpace, String id, String vectorId, String requestId,
                               String idempotencyKey, String sourceVersion) { }

    public record BatchSyncView(boolean success, String errorCode, String message, String requestId,
                                Integer totalOperations, Integer succeededOperations,
                                Integer failedOperations, List<SyncEvidence> results) { }
}
