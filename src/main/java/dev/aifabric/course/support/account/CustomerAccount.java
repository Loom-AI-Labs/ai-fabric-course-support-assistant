package dev.aifabric.course.support.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_account")
public class CustomerAccount {

    @Id
    private String id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String plan;

    @Column(nullable = false)
    private String roles;

    protected CustomerAccount() {
    }

    public CustomerAccount(String id, String tenantId, String email, String plan, String roles) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.plan = plan;
        this.roles = roles;
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getPlan() {
        return plan;
    }

    public String getRoles() {
        return roles;
    }
}
