package dev.aifabric.course.support.assistant;

import java.util.List;

public record SupportAnswer(
    Status status,
    String answer,
    String message,
    String mode,
    List<EvidenceItem> evidence,
    Diagnostics diagnostics
) {

    public enum Status {
        ANSWERED,
        NO_EVIDENCE,
        RETRIEVAL_FAILED,
        GENERATION_FAILED,
        PRIVACY_FAILED
    }

    public record EvidenceItem(
        String id,
        String title,
        String snippet,
        Double score,
        String category
    ) {
    }

    public record Diagnostics(
        String vectorSpace,
        boolean retrievalSucceeded,
        boolean generationAttempted,
        String requestId,
        String errorCode
    ) {
    }
}
