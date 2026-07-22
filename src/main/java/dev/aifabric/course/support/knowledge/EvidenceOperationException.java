package dev.aifabric.course.support.knowledge;

public class EvidenceOperationException extends RuntimeException {

    public EvidenceOperationException(String message) {
        super(message);
    }

    public EvidenceOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
