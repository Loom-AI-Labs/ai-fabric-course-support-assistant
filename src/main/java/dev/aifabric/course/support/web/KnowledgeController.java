package dev.aifabric.course.support.web;

import dev.aifabric.course.support.knowledge.KnowledgeEvidenceService;
import dev.aifabric.course.support.knowledge.KnowledgeDataSyncService;
import dev.aifabric.course.support.identity.CoursePrincipalProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Validated
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeEvidenceService evidenceService;
    private final KnowledgeDataSyncService dataSyncService;
    private final CoursePrincipalProvider principalProvider;

    public KnowledgeController(KnowledgeEvidenceService evidenceService,
                               KnowledgeDataSyncService dataSyncService,
                               CoursePrincipalProvider principalProvider) {
        this.evidenceService = evidenceService;
        this.dataSyncService = dataSyncService;
        this.principalProvider = principalProvider;
    }

    @GetMapping("/articles")
    public List<KnowledgeEvidenceService.PublicArticle> articles() {
        return evidenceService.articles(principalProvider.currentPrincipal());
    }

    @GetMapping("/search")
    public KnowledgeEvidenceService.SearchResponse search(@RequestParam("q") @NotBlank String query) {
        return evidenceService.search(query, principalProvider.currentPrincipal());
    }

    @PostMapping("/articles")
    public ResponseEntity<KnowledgeDataSyncService.SyncedArticle> create(
        @Valid @RequestBody KnowledgeDataSyncService.CreateArticleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(dataSyncService.create(request, principalProvider.currentPrincipal()));
    }

    @PutMapping("/articles/{id}")
    public KnowledgeDataSyncService.SyncedArticle update(
        @PathVariable String id,
        @Valid @RequestBody KnowledgeDataSyncService.UpdateArticleRequest request
    ) {
        return dataSyncService.update(id, request, principalProvider.currentPrincipal());
    }

    @DeleteMapping("/articles/{id}")
    public KnowledgeDataSyncService.SyncEvidence delete(@PathVariable String id) {
        return dataSyncService.delete(id, principalProvider.currentPrincipal());
    }

    @PostMapping("/sync/reconcile")
    public ResponseEntity<KnowledgeDataSyncService.BatchSyncView> reconcile(
        @Valid @RequestBody KnowledgeDataSyncService.ReconcileRequest request
    ) {
        KnowledgeDataSyncService.BatchSyncView response =
            dataSyncService.reconcile(request, principalProvider.currentPrincipal());
        HttpStatus status = response.errorCode() == null ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
}
