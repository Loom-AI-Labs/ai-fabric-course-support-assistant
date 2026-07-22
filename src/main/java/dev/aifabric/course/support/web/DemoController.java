package dev.aifabric.course.support.web;

import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.demo.CourseDeploymentInfoService;
import dev.aifabric.course.support.demo.CourseReadinessService;
import dev.aifabric.course.support.knowledge.KnowledgeEvidenceService;
import dev.aifabric.course.support.message.SupportMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final CourseDataService dataService;
    private final CourseDeploymentInfoService deploymentInfoService;
    private final CourseReadinessService readinessService;
    private final KnowledgeEvidenceService evidenceService;
    private final SupportMessageService messageService;

    public DemoController(CourseDataService dataService,
                          CourseDeploymentInfoService deploymentInfoService,
                          CourseReadinessService readinessService,
                          KnowledgeEvidenceService evidenceService,
                          SupportMessageService messageService) {
        this.dataService = dataService;
        this.deploymentInfoService = deploymentInfoService;
        this.readinessService = readinessService;
        this.evidenceService = evidenceService;
        this.messageService = messageService;
    }

    @GetMapping("/health")
    public CourseDeploymentInfoService.DeploymentHealth health() {
        return deploymentInfoService.health();
    }

    @PostMapping("/reset")
    public CourseDataService.DatasetSnapshot reset() {
        evidenceService.clear();
        messageService.clearVectors();
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
        messageService.clearVectors();
        return readinessService.readiness();
    }

    @GetMapping("/readiness")
    public CourseReadinessService.ReadinessResponse readiness() {
        return readinessService.readiness();
    }
}
