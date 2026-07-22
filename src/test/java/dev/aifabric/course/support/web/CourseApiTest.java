package dev.aifabric.course.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CourseApiTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void reset() throws Exception {
        mockMvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
    }

    @Test
    void seedAndReadinessExposeDomainStateWithoutClaimingAiCapabilities() throws Exception {
        mockMvc.perform(post("/api/demo/seed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.articles").value(5))
            .andExpect(jsonPath("$.policies").value(2));

        mockMvc.perform(get("/api/demo/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkpoint").value("course-0.3.3-00-starter"))
            .andExpect(jsonPath("$.sourceRecords.articles").value(5))
            .andExpect(jsonPath("$.indexedVectors").value(0))
            .andExpect(jsonPath("$.capabilities.semanticSearch").value(false));
    }

    @Test
    void aiEndpointsFailExplicitlyUntilAStudentImplementsThem() throws Exception {
        mockMvc.perform(get("/api/knowledge/search").param("q", "recover account access"))
            .andExpect(status().isNotImplemented())
            .andExpect(jsonPath("$.capability").value("semantic search"));

        mockMvc.perform(post("/api/demo/index"))
            .andExpect(status().isNotImplemented())
            .andExpect(jsonPath("$.capability").value("semantic-search indexing"));
    }
}
