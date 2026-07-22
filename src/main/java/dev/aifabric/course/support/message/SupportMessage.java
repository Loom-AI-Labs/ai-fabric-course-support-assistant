package dev.aifabric.course.support.message;

import ai.fabric.annotation.AICapable;
import ai.fabric.annotation.AIContext;
import ai.fabric.annotation.AISearchable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "support_message")
@AICapable(entityType = SupportMessage.ENTITY_TYPE)
public class SupportMessage {

    public static final String ENTITY_TYPE = "support-message";

    @Id
    @AIContext(dataType = "id")
    private String id;

    @Column(nullable = false)
    @AIContext(dataType = "id")
    private String tenantId;

    @Column(nullable = false)
    @AIContext(dataType = "id")
    private String customerId;

    @Column(nullable = false, length = 4_000)
    @AISearchable(maxLength = 4_000, required = true)
    private String safeContent;

    @Column(nullable = false)
    @AIContext(dataType = "enum")
    private String detectedPiiTypes;

    @Column(nullable = false)
    @AIContext
    private boolean visibleToUser;

    @Column(nullable = false)
    private Instant createdAt;

    protected SupportMessage() {
    }

    public SupportMessage(String id, String tenantId, String customerId, String safeContent,
                          String detectedPiiTypes, boolean visibleToUser, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.safeContent = safeContent;
        this.detectedPiiTypes = detectedPiiTypes;
        this.visibleToUser = visibleToUser;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getSafeContent() {
        return safeContent;
    }

    public String getDetectedPiiTypes() {
        return detectedPiiTypes;
    }

    public boolean isVisibleToUser() {
        return visibleToUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
