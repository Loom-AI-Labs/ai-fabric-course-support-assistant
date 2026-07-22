package dev.aifabric.course.support.assistant;

public class UnsupportedSupportPositionException extends RuntimeException {

    public UnsupportedSupportPositionException(String position) {
        super("Unsupported support position: " + position);
    }
}
