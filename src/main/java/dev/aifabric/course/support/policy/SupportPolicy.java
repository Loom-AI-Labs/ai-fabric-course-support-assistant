package dev.aifabric.course.support.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "support_policy")
public class SupportPolicy {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 8_000)
    private String body;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String status;

    protected SupportPolicy() {
    }

    public SupportPolicy(String id, String title, String body, String tenantId, String status) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.tenantId = tenantId;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getStatus() {
        return status;
    }
}
