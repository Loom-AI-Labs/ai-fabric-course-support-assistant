package dev.aifabric.course.support.web;

import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.demo.CourseReadinessService;
import dev.aifabric.course.support.knowledge.KnowledgeEvidenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final CourseDataService dataService;
    private final CourseReadinessService readinessService;
    private final KnowledgeEvidenceService evidenceService;

    public DemoController(CourseDataService dataService, CourseReadinessService readinessService,
                          KnowledgeEvidenceService evidenceService) {
        this.dataService = dataService;
        this.readinessService = readinessService;
        this.evidenceService = evidenceService;
    }

    @PostMapping("/reset")
    public CourseDataService.DatasetSnapshot reset() {
        evidenceService.clear();
        return dataService.reset();
    }

    @PostMapping("/seed")
    public CourseDataService.DatasetSnapshot seed() {
        return dataService.seed();
    }

    @PostMapping("/index")
    public KnowledgeEvidenceService.IndexResponse index() {
        return evidenceService.indexAll();
    }

    @PostMapping("/vectors/clear")
    public CourseReadinessService.ReadinessResponse clearVectors() {
        evidenceService.clear();
        return readinessService.readiness();
    }

    @GetMapping("/readiness")
    public CourseReadinessService.ReadinessResponse readiness() {
        return readinessService.readiness();
    }
}
