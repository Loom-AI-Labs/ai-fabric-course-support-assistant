package dev.aifabric.course.support.migration;

public class MigrationTransitionException extends RuntimeException {

    public MigrationTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
