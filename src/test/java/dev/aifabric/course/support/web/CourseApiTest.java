package dev.aifabric.course.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.aifabric.course.support.testsupport.CourseTestAIConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CourseTestAIConfiguration.class)
class CourseApiTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void reset() throws Exception {
        mockMvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
    }

    @Test
    void sourceDataAndVectorEvidenceHaveSeparateLifecycle() throws Exception {
        mockMvc.perform(post("/api/demo/seed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.articles").value(5))
            .andExpect(jsonPath("$.policies").value(2));

        mockMvc.perform(get("/api/demo/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkpoint").value("course-0.3.3-01-first-search"))
            .andExpect(jsonPath("$.sourceRecords.articles").value(5))
            .andExpect(jsonPath("$.indexedVectors").value(0))
            .andExpect(jsonPath("$.capabilities.semanticSearch").value(true));

        mockMvc.perform(get("/api/knowledge/search").param("q", "I cannot sign in after too many attempts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCount").value(0));

        mockMvc.perform(post("/api/demo/index"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.indexedArticles").value(5))
            .andExpect(jsonPath("$.indexedVectors").value(5));

        mockMvc.perform(get("/api/knowledge/search").param("q", "I cannot sign in after too many attempts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evidence[0].evidenceId").value("article-account-lockout"))
            .andExpect(jsonPath("$.evidence[0].metadata.category").value("authentication"))
            .andExpect(jsonPath("$.evidence[0].metadata.tenantId").value("tenant-blue"))
            .andExpect(jsonPath("$.evidence[0].content").value(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("fraud review"))));
    }

    @Test
    void updateAndDeleteReplaceTheEvidenceWithoutLeavingStaleVectors() throws Exception {
        mockMvc.perform(post("/api/demo/seed")).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/index")).andExpect(status().isOk());

        mockMvc.perform(put("/api/knowledge/articles/article-billing-method")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Download a billing invoice",
                      "body": "Open Billing History and download the invoice or receipt for the selected renewal."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Download a billing invoice"));

        mockMvc.perform(get("/api/knowledge/search").param("q", "Where can I download my invoice receipt?"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evidence[0].evidenceId").value("article-billing-method"))
            .andExpect(jsonPath("$.evidence[0].content").value(
                org.hamcrest.Matchers.containsString("download the invoice")))
            .andExpect(jsonPath("$.evidence[0].content").value(
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("replacement method"))));

        mockMvc.perform(delete("/api/knowledge/articles/article-billing-method"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/demo/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceRecords.articles").value(4))
            .andExpect(jsonPath("$.indexedVectors").value(4));
    }
}
