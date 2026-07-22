package dev.aifabric.course.support.migration;

public class MigrationJobNotFoundException extends RuntimeException {

    public MigrationJobNotFoundException(String jobId) {
        super("Knowledge migration job was not found: " + jobId);
    }
}
