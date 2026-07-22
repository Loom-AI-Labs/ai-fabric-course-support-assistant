package dev.aifabric.course.support.web;

import dev.aifabric.course.support.knowledge.KnowledgeEvidenceService;
import dev.aifabric.course.support.identity.CoursePrincipalProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeEvidenceService evidenceService;
    private final CoursePrincipalProvider principalProvider;

    public KnowledgeController(KnowledgeEvidenceService evidenceService,
                               CoursePrincipalProvider principalProvider) {
        this.evidenceService = evidenceService;
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

    @PutMapping("/articles/{id}")
    public KnowledgeEvidenceService.PublicArticle update(@PathVariable String id,
                                                          @Valid @RequestBody KnowledgeEvidenceService.UpdateArticleRequest request) {
        return evidenceService.update(id, request, principalProvider.currentPrincipal());
    }

    @DeleteMapping("/articles/{id}")
    public void delete(@PathVariable String id) {
        evidenceService.delete(id, principalProvider.currentPrincipal());
    }
}
