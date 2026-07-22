package dev.aifabric.course.support.web;

import dev.aifabric.course.support.common.FeatureUnavailableException;
import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.knowledge.KnowledgeArticle;
import dev.aifabric.course.support.knowledge.KnowledgeArticleRepository;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeArticleRepository articleRepository;
    private final CourseDataService dataService;

    public KnowledgeController(KnowledgeArticleRepository articleRepository, CourseDataService dataService) {
        this.articleRepository = articleRepository;
        this.dataService = dataService;
    }

    @GetMapping("/articles")
    public List<KnowledgeArticle> articles() {
        return articleRepository.findAllByOrderByIdAsc();
    }

    @PostMapping("/seed-without-index")
    public CourseDataService.DatasetSnapshot seedWithoutIndex() {
        return dataService.seed();
    }

    @GetMapping("/search")
    public void search(@RequestParam("q") @NotBlank String query) {
        throw new FeatureUnavailableException("semantic search");
    }
}
