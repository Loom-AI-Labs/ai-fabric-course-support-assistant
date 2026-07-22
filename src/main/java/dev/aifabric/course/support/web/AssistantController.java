package dev.aifabric.course.support.web;

import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.AIActionRegistry;
import dev.aifabric.course.support.assistant.SupportAnswer;
import dev.aifabric.course.support.assistant.SupportAssistantService;
import dev.aifabric.course.support.assistant.SupportOrchestrationResponse;
import dev.aifabric.course.support.assistant.SupportOrchestrationService;
import dev.aifabric.course.support.identity.CoursePrincipalProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final SupportAssistantService assistantService;
    private final SupportOrchestrationService orchestrationService;
    private final CoursePrincipalProvider principalProvider;
    private final AIActionRegistry actionRegistry;

    public AssistantController(SupportAssistantService assistantService,
                               SupportOrchestrationService orchestrationService,
                               CoursePrincipalProvider principalProvider,
                               AIActionRegistry actionRegistry) {
        this.assistantService = assistantService;
        this.orchestrationService = orchestrationService;
        this.principalProvider = principalProvider;
        this.actionRegistry = actionRegistry;
    }

    @PostMapping("/orchestrate")
    public SupportOrchestrationResponse orchestrate(@Valid @RequestBody AssistantQueryRequest request) {
        return orchestrationService.orchestrate(
            request.message(), request.conversationId(), principalProvider.currentPrincipal());
    }

    @GetMapping("/actions")
    public java.util.List<AIActionMetaData> actions() {
        return actionRegistry.getAllMetadata();
    }

    @PostMapping("/query")
    public ResponseEntity<SupportAnswer> query(@Valid @RequestBody AssistantQueryRequest request) {
        SupportAnswer answer = assistantService.answer(request.message());
        HttpStatus status = switch (answer.status()) {
            case RETRIEVAL_FAILED, GENERATION_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            case ANSWERED, NO_EVIDENCE -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(answer);
    }

    public record AssistantQueryRequest(
        @NotBlank @Size(max = 2_000) String message,
        String conversationId
    ) {
    }
}
