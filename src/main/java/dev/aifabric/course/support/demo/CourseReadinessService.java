package dev.aifabric.course.support.demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class CourseReadinessService {

    private final CourseDataService dataService;
    private final Environment environment;

    public CourseReadinessService(CourseDataService dataService, Environment environment) {
        this.dataService = dataService;
        this.environment = environment;
    }

    public ReadinessResponse readiness() {
        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        capabilities.put("semanticSearch", false);
        capabilities.put("rag", false);
        capabilities.put("governedActions", false);
        capabilities.put("conversationMemory", false);
        capabilities.put("tenantSecurity", false);
        capabilities.put("piiProtection", false);

        return new ReadinessResponse(
            "course-0.3.3-00-starter",
            dataService.snapshot(),
            0,
            List.of(environment.getActiveProfiles()),
            Map.copyOf(capabilities)
        );
    }

    public record ReadinessResponse(
        String checkpoint,
        CourseDataService.DatasetSnapshot sourceRecords,
        long indexedVectors,
        java.util.List<String> activeProfiles,
        Map<String, Boolean> capabilities
    ) {
    }
}
