package dev.aifabric.course.support.web;

import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.orchestration.attachment.OrchestrationAttachment;
import dev.aifabric.course.support.assistant.SupportAnswer;
import dev.aifabric.course.support.assistant.SupportAssistantService;
import dev.aifabric.course.support.assistant.SupportOrchestrationResponse;
import dev.aifabric.course.support.assistant.SupportOrchestrationService;
import dev.aifabric.course.support.conversation.ConversationHistoryService;
import dev.aifabric.course.support.identity.CoursePrincipalProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final ConversationHistoryService conversationHistoryService;

    public AssistantController(SupportAssistantService assistantService,
                               SupportOrchestrationService orchestrationService,
                               CoursePrincipalProvider principalProvider,
                               AIActionRegistry actionRegistry,
                               ConversationHistoryService conversationHistoryService) {
        this.assistantService = assistantService;
        this.orchestrationService = orchestrationService;
        this.principalProvider = principalProvider;
        this.actionRegistry = actionRegistry;
        this.conversationHistoryService = conversationHistoryService;
    }

    @PostMapping("/orchestrate")
    public SupportOrchestrationResponse orchestrate(@Valid @RequestBody AssistantQueryRequest request) {
        return orchestrationService.orchestrate(
            request.message(), request.conversationId(), request.attachments(), principalProvider.currentPrincipal());
    }

    @GetMapping("/actions")
    public java.util.List<AIActionMetaData> actions() {
        return actionRegistry.getAllMetadata();
    }

    @GetMapping("/conversations/{conversationId}")
    public ConversationHistoryService.ConversationView conversation(@PathVariable String conversationId) {
        return conversationHistoryService.get(conversationId, principalProvider.currentPrincipal());
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String conversationId) {
        conversationHistoryService.delete(conversationId, principalProvider.currentPrincipal());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/query")
    public ResponseEntity<SupportAnswer> query(@Valid @RequestBody AssistantQueryRequest request) {
        SupportAnswer answer = assistantService.answer(request.message(), principalProvider.currentPrincipal());
        HttpStatus status = switch (answer.status()) {
            case RETRIEVAL_FAILED, GENERATION_FAILED, PRIVACY_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            case ANSWERED, NO_EVIDENCE -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(answer);
    }

    public record AssistantQueryRequest(
        @NotBlank @Size(max = 2_000) String message,
        @Size(max = 128)
        @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "conversationId contains unsupported characters")
        String conversationId,
        @Size(max = 8) List<@Valid OrchestrationAttachment> attachments
    ) {
    }
}
