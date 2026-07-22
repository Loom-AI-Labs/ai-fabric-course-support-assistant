package dev.aifabric.course.support.web;

import dev.aifabric.course.support.common.FeatureUnavailableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    @PostMapping("/query")
    public void query(@Valid @RequestBody AssistantQueryRequest request) {
        throw new FeatureUnavailableException("AI assistant orchestration");
    }

    public record AssistantQueryRequest(
        @NotBlank String message,
        String conversationId
    ) {
    }
}
