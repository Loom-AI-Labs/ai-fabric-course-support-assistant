package dev.aifabric.course.support.web;

import dev.aifabric.course.support.operations.CourseOperationsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseOperationsController {

    private final CourseOperationsService operationsService;

    public CourseOperationsController(CourseOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping("/api/demo/operations/readiness")
    public CourseOperationsService.OperationsReadiness readiness() {
        return operationsService.readiness();
    }

    @PostMapping("/api/admin/operations/release-probes")
    public CourseOperationsService.ReleaseProbe createReleaseProbe() {
        return operationsService.createReleaseProbe();
    }

    @PostMapping("/api/admin/operations/retention/cleanup")
    public CourseOperationsService.RetentionResult cleanup() {
        return operationsService.cleanupRetainedOperationalState();
    }
}
