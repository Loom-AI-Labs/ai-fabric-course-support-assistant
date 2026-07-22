package dev.aifabric.course.support.knowledge;

public class DataSyncOperationException extends RuntimeException {

    private final String errorCode;

    public DataSyncOperationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
