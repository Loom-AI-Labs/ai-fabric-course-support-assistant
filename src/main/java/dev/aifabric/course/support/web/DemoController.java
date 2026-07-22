package dev.aifabric.course.support.web;

import dev.aifabric.course.support.common.FeatureUnavailableException;
import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.demo.CourseReadinessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final CourseDataService dataService;
    private final CourseReadinessService readinessService;

    public DemoController(CourseDataService dataService, CourseReadinessService readinessService) {
        this.dataService = dataService;
        this.readinessService = readinessService;
    }

    @PostMapping("/reset")
    public CourseDataService.DatasetSnapshot reset() {
        return dataService.reset();
    }

    @PostMapping("/seed")
    public CourseDataService.DatasetSnapshot seed() {
        return dataService.seed();
    }

    @PostMapping("/index")
    public void index() {
        throw new FeatureUnavailableException("semantic-search indexing");
    }

    @GetMapping("/readiness")
    public CourseReadinessService.ReadinessResponse readiness() {
        return readinessService.readiness();
    }
}
