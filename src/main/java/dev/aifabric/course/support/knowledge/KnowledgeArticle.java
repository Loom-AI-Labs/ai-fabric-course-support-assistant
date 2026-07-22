package dev.aifabric.course.support.knowledge;

import ai.fabric.annotation.AICapable;
import ai.fabric.annotation.AIContext;
import ai.fabric.annotation.AISearchable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_article")
@AICapable(entityType = KnowledgeArticle.ENTITY_TYPE)
public class KnowledgeArticle {

    public static final String ENTITY_TYPE = "knowledge-article";

    @Id
    @AIContext(dataType = "id", priority = 10)
    private String id;

    @Column(nullable = false)
    @AISearchable(weight = 2.0, required = true)
    @AIContext(description = "Public evidence title")
    private String title;

    @Column(nullable = false, length = 8_000)
    @AISearchable(maxLength = 8_000, required = true)
    private String body;

    @Column(nullable = false)
    @AIContext(description = "Public support category")
    private String category;

    @Column(nullable = false)
    @AIContext(dataType = "id", description = "Application-owned tenant identifier")
    private String tenantId;

    @Column(nullable = false)
    @AIContext(dataType = "enum")
    private String status;

    @Column(nullable = false)
    @AIContext(dataType = "enum", description = "Application-reviewed evidence visibility")
    private String visibility;

    @Column(nullable = false)
    @AIContext(description = "Positive application-owned user visibility decision")
    private boolean visibleToUser;

    @Column(length = 2_000)
    private String internalNotes;

    protected KnowledgeArticle() {
    }

    public KnowledgeArticle(String id, String title, String body, String category, String tenantId,
                            String status, String internalNotes) {
        this(id, title, body, category, tenantId, status, "INTERNAL", true, internalNotes);
    }

    public KnowledgeArticle(String id, String title, String body, String category, String tenantId,
                            String status, String visibility, boolean visibleToUser, String internalNotes) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.category = category;
        this.tenantId = tenantId;
        this.status = status;
        this.visibility = visibility;
        this.visibleToUser = visibleToUser;
        this.internalNotes = internalNotes;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCategory() {
        return category;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getStatus() {
        return status;
    }

    public String getVisibility() {
        return visibility;
    }

    public boolean isVisibleToUser() {
        return visibleToUser;
    }

    public String getInternalNotes() {
        return internalNotes;
    }
}
