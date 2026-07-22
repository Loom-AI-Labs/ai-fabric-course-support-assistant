package dev.aifabric.course.support.web;

import dev.aifabric.course.support.demo.CoursePromptDiagnosticsService;
import dev.aifabric.course.support.identity.CoursePrincipalProvider;
import dev.aifabric.course.support.quality.RagQualityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quality")
public class RagQualityController {

    private final RagQualityService qualityService;
    private final CoursePromptDiagnosticsService promptDiagnosticsService;
    private final CoursePrincipalProvider principalProvider;

    public RagQualityController(RagQualityService qualityService,
                                CoursePromptDiagnosticsService promptDiagnosticsService,
                                CoursePrincipalProvider principalProvider) {
        this.qualityService = qualityService;
        this.promptDiagnosticsService = promptDiagnosticsService;
        this.principalProvider = principalProvider;
    }

    @GetMapping("/rag/golden")
    public RagQualityService.QualitySuite goldenSuite() {
        return qualityService.evaluateGoldenSuite(principalProvider.currentPrincipal());
    }

    @PostMapping("/rag/evaluate")
    public RagQualityService.CaseResult evaluate(
        @Valid @RequestBody RagQualityService.EvaluationRequest request
    ) {
        return qualityService.evaluate(request, principalProvider.currentPrincipal());
    }

    @GetMapping("/prompts")
    public CoursePromptDiagnosticsService.PromptQualityContract promptContract() {
        return promptDiagnosticsService.qualityContract();
    }
}
