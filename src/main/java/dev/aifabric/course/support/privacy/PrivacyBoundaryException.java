package dev.aifabric.course.support.privacy;

public class PrivacyBoundaryException extends RuntimeException {

    public PrivacyBoundaryException(String message) {
        super(message);
    }

    public PrivacyBoundaryException(String message, Throwable cause) {
        super(message, cause);
    }
}
