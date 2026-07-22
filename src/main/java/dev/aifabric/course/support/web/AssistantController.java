package dev.aifabric.course.support.web;

import dev.aifabric.course.support.assistant.SupportAnswer;
import dev.aifabric.course.support.assistant.SupportAssistantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final SupportAssistantService assistantService;

    public AssistantController(SupportAssistantService assistantService) {
        this.assistantService = assistantService;
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
