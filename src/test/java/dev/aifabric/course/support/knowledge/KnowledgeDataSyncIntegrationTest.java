package dev.aifabric.course.support.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.fabric.rag.VectorDatabaseService;
import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CourseTestAIConfiguration.class)
class KnowledgeDataSyncIntegrationTest {

    private static final String ALEX_BEARER = "Bearer course-alex-local-token";
    private static final String RILEY_BEARER = "Bearer course-riley-local-token";

    @Autowired private MockMvc mockMvc;
    @Autowired private KnowledgeArticleRepository articleRepository;
    @Autowired private VectorDatabaseService vectorDatabaseService;

    @BeforeEach
    void reset() throws Exception {
        mockMvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/seed")).andExpect(status().isOk());
    }

    @Test
    void createUpdateAndDeleteKeepStableEvidenceIdentityWithoutStaleContent() throws Exception {
        mockMvc.perform(post("/api/knowledge/articles")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "id": "article-passwordless-login",
                      "title": "Enable passwordless login",
                      "body": "Register a passkey in Security Settings before removing your password.",
                      "category": "authentication"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.article.id").value("article-passwordless-login"))
            .andExpect(jsonPath("$.sync.success").value(true))
            .andExpect(jsonPath("$.sync.operation").value("UPSERT"))
            .andExpect(jsonPath("$.sync.vectorSpace").value(KnowledgeArticle.ENTITY_TYPE))
            .andExpect(jsonPath("$.sync.id").value("article-passwordless-login"))
            .andExpect(jsonPath("$.sync.requestId").value(
                org.hamcrest.Matchers.startsWith("course-sync-")));

        assertThat(articleRepository.findById("article-passwordless-login")).isPresent();
        assertThat(vectorDatabaseService.vectorExists(KnowledgeArticle.ENTITY_TYPE,
            "article-passwordless-login")).isTrue();
        assertThat(vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE)).isEqualTo(1);

        mockMvc.perform(put("/api/knowledge/articles/article-passwordless-login")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Replace a password with a security key",
                      "body": "Register the hardware security key, verify it, then revoke the previous login method."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.article.id").value("article-passwordless-login"))
            .andExpect(jsonPath("$.sync.id").value("article-passwordless-login"))
            .andExpect(jsonPath("$.sync.sourceVersion").value("1"));

        assertThat(vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE)).isEqualTo(1);
        mockMvc.perform(get("/api/knowledge/search")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .param("q", "How do I revoke the previous security key login?"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evidence[0].evidenceId").value("article-passwordless-login"))
            .andExpect(jsonPath("$.evidence[0].content").value(
                org.hamcrest.Matchers.containsString("hardware security key")))
            .andExpect(jsonPath("$.evidence[0].content").value(
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("removing your password"))));

        mockMvc.perform(delete("/api/knowledge/articles/article-passwordless-login")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.operation").value("DELETE"));

        assertThat(articleRepository.findById("article-passwordless-login")).isEmpty();
        assertThat(vectorDatabaseService.vectorExists(KnowledgeArticle.ENTITY_TYPE,
            "article-passwordless-login")).isFalse();
        assertThat(vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE)).isZero();
    }

    @Test
    void applicationBoundaryDerivesIdentityAndDeniesRawOrUnauthorizedSync() throws Exception {
        mockMvc.perform(post("/api/knowledge/articles")
                .header(HttpHeaders.AUTHORIZATION, RILEY_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "id": "article-riley-forbidden",
                      "title": "Forbidden sync",
                      "body": "This source row must not be created.",
                      "category": "security"
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/internal/ai-data-sync/upsert")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "vectorSpace": "unknown-space",
                      "id": "forged",
                      "content": "forged",
                      "trace": {"authContext": {"subjectId": "customer-alex"}}
                    }
                    """))
            .andExpect(status().isForbidden());

        assertThat(articleRepository.findById("article-riley-forbidden")).isEmpty();
        assertThat(vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE)).isZero();
    }

    @Test
    void invalidProjectionRollsBackSourceAndBatchLimitsAndPartialFailuresStayVisible() throws Exception {
        String oversizedBody = "billing ".repeat(180);
        mockMvc.perform(post("/api/knowledge/articles")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "id": "article-too-large",
                      "title": "Oversized projection",
                      "body": "%s",
                      "category": "billing"
                    }
                    """.formatted(oversizedBody)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        assertThat(articleRepository.findById("article-too-large")).isEmpty();
        assertThat(vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE)).isZero();

        articleRepository.saveAndFlush(new KnowledgeArticle(
            "article-reconcile-too-large",
            "Oversized reconcile projection",
            oversizedBody,
            "billing",
            "tenant-blue",
            "PUBLISHED",
            "INTERNAL",
            true,
            null,
            LocalDateTime.of(2026, 1, 2, 0, 0)
        ));

        mockMvc.perform(post("/api/knowledge/sync/reconcile")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "articleIds": ["article-account-lockout", "article-reconcile-too-large"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.totalOperations").value(2))
            .andExpect(jsonPath("$.succeededOperations").value(1))
            .andExpect(jsonPath("$.failedOperations").value(1))
            .andExpect(jsonPath("$.results[0].success").value(true))
            .andExpect(jsonPath("$.results[1].success").value(false))
            .andExpect(jsonPath("$.results[1].errorCode").value("INVALID_REQUEST"));

        assertThat(vectorDatabaseService.vectorExists(KnowledgeArticle.ENTITY_TYPE,
            "article-account-lockout")).isTrue();
        assertThat(vectorDatabaseService.vectorExists(KnowledgeArticle.ENTITY_TYPE,
            "article-reconcile-too-large")).isFalse();
        long vectorsBeforeRejectedBatch =
            vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE);

        mockMvc.perform(post("/api/knowledge/sync/reconcile")
                .header(HttpHeaders.AUTHORIZATION, ALEX_BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "articleIds": [
                        "article-account-lockout",
                        "article-two-factor",
                        "article-api-key"
                      ]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("BATCH_TOO_LARGE"))
            .andExpect(jsonPath("$.succeededOperations").value(0))
            .andExpect(jsonPath("$.failedOperations").value(3));

        assertThat(vectorDatabaseService.getVectorCountByEntityType(KnowledgeArticle.ENTITY_TYPE))
            .isEqualTo(vectorsBeforeRejectedBatch);
    }
}
