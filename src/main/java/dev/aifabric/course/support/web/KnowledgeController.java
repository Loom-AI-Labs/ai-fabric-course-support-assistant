package dev.aifabric.course.support.web;

import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.knowledge.KnowledgeEvidenceService;
import dev.aifabric.course.support.knowledge.KnowledgeArticleRepository;
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

@Validated
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeArticleRepository articleRepository;
    private final CourseDataService dataService;
    private final KnowledgeEvidenceService evidenceService;

    public KnowledgeController(KnowledgeArticleRepository articleRepository, CourseDataService dataService,
                               KnowledgeEvidenceService evidenceService) {
        this.articleRepository = articleRepository;
        this.dataService = dataService;
        this.evidenceService = evidenceService;
    }

    @GetMapping("/articles")
    public List<KnowledgeEvidenceService.PublicArticle> articles() {
        return articleRepository.findAllByOrderByIdAsc().stream()
            .map(KnowledgeEvidenceService.PublicArticle::from)
            .toList();
    }

    @PostMapping("/seed-without-index")
    public CourseDataService.DatasetSnapshot seedWithoutIndex() {
        return dataService.seed();
    }

    @GetMapping("/search")
    public KnowledgeEvidenceService.SearchResponse search(@RequestParam("q") @NotBlank String query) {
        return evidenceService.search(query);
    }

    @PutMapping("/articles/{id}")
    public KnowledgeEvidenceService.PublicArticle update(@PathVariable String id,
                                                          @Valid @RequestBody KnowledgeEvidenceService.UpdateArticleRequest request) {
        return evidenceService.update(id, request);
    }

    @DeleteMapping("/articles/{id}")
    public void delete(@PathVariable String id) {
        evidenceService.delete(id);
    }
}
