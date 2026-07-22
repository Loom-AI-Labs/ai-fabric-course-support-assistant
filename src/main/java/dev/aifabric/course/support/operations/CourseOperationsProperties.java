package dev.aifabric.course.support.operations;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "course.operations")
public class CourseOperationsProperties {

    private boolean maintenanceEnabled;
    private boolean releaseProbesEnabled;

    @NotNull
    private Duration completedRecordRetention = Duration.ofDays(7);

    @NotNull
    private Duration conversationRetention = Duration.ofDays(7);

    public boolean isMaintenanceEnabled() {
        return maintenanceEnabled;
    }

    public void setMaintenanceEnabled(boolean maintenanceEnabled) {
        this.maintenanceEnabled = maintenanceEnabled;
    }

    public boolean isReleaseProbesEnabled() {
        return releaseProbesEnabled;
    }

    public void setReleaseProbesEnabled(boolean releaseProbesEnabled) {
        this.releaseProbesEnabled = releaseProbesEnabled;
    }

    public Duration getCompletedRecordRetention() {
        return completedRecordRetention;
    }

    public void setCompletedRecordRetention(Duration completedRecordRetention) {
        this.completedRecordRetention = completedRecordRetention;
    }

    public Duration getConversationRetention() {
        return conversationRetention;
    }

    public void setConversationRetention(Duration conversationRetention) {
        this.conversationRetention = conversationRetention;
    }
}
