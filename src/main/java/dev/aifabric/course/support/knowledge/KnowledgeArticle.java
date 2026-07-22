package dev.aifabric.course.support.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_article")
public class KnowledgeArticle {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 8_000)
    private String body;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String status;

    @Column(length = 2_000)
    private String internalNotes;

    protected KnowledgeArticle() {
    }

    public KnowledgeArticle(String id, String title, String body, String category, String tenantId,
                            String status, String internalNotes) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.category = category;
        this.tenantId = tenantId;
        this.status = status;
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

    public String getInternalNotes() {
        return internalNotes;
    }
}
