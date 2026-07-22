package dev.aifabric.course.support.assistant;

import ai.fabric.intent.orchestration.OrchestrationResult;

public record SupportOrchestrationResponse(
    String conversationId,
    OrchestrationResult result
) {
}
