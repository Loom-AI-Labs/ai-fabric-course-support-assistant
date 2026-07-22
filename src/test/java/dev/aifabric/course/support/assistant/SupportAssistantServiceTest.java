package dev.aifabric.course.support.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.fabric.core.AICoreService;
import ai.fabric.core.LlmPurpose;
import ai.fabric.dto.AIGenerationResponse;
import ai.fabric.dto.RAGRequest;
import ai.fabric.dto.RAGResponse;
import ai.fabric.llm.structured.DefaultStructuredJsonCallExecutor;
import ai.fabric.llm.structured.StructuredJsonExtractor;
import ai.fabric.spi.RAGProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.identity.CourseAuthorizationService;
import dev.aifabric.course.support.identity.CoursePrincipal;
import dev.aifabric.course.support.privacy.SafePIIProcessor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SupportAssistantServiceTest {

    private static final CoursePrincipal PRINCIPAL = new CoursePrincipal(
        CourseDataService.COURSE_CUSTOMER,
        CourseDataService.COURSE_TENANT,
        "course-test-session"
    );

    private final RAGProvider ragProvider = mock(RAGProvider.class);
    private final AICoreService aiCoreService = mock(AICoreService.class);
    private final CourseAuthorizationService authorizationService = mock(CourseAuthorizationService.class);
    private final SafePIIProcessor piiProcessor = mock(SafePIIProcessor.class);
    private final SupportAssistantService service = new SupportAssistantService(
        ragProvider,
        aiCoreService,
        new DefaultStructuredJsonCallExecutor(new StructuredJsonExtractor(), new ObjectMapper()),
        authorizationService,
        piiProcessor
    );

    @BeforeEach
    void setUpSecurityBoundary() {
        when(authorizationService.requireScope(PRINCIPAL, "support:read")).thenReturn(PRINCIPAL);
        when(piiProcessor.process(any(String.class))).thenAnswer(invocation ->
            new SafePIIProcessor.SafeText(invocation.getArgument(0), false, List.of()));
    }

    @Test
    void retrievalFailureIsVisibleAndSkipsGeneration() {
        when(ragProvider.performRAGQuery(any())).thenReturn(RAGResponse.builder()
            .success(false)
            .errorMessage("private adapter details")
            .documents(List.of())
            .build());

        SupportAnswer answer = service.answer("How do I recover access?", PRINCIPAL);

        assertThat(answer.status()).isEqualTo(SupportAnswer.Status.RETRIEVAL_FAILED);
        assertThat(answer.answer()).isNull();
        assertThat(answer.diagnostics().retrievalSucceeded()).isFalse();
        assertThat(answer.diagnostics().generationAttempted()).isFalse();
        assertThat(answer.diagnostics().errorCode()).isEqualTo("RAG_RETRIEVAL_FAILED");
        assertThat(answer.message()).doesNotContain("private adapter details");
        verify(aiCoreService, never()).generateContent(any(), any());
    }

    @Test
    void invalidModelCitationFailsClosedAfterStructuredRetry() {
        when(ragProvider.performRAGQuery(any())).thenReturn(successfulRetrieval());
        when(aiCoreService.generateContent(any(), eq(LlmPurpose.GENERATION))).thenReturn(
            AIGenerationResponse.builder()
                .requestId("provider-request")
                .content("{\"answer\":\"Invented answer\",\"citationIds\":[\"not-retrieved\"]}")
                .status("SUCCESS")
                .build()
        );

        SupportAnswer answer = service.answer("How do I recover access?", PRINCIPAL);

        assertThat(answer.status()).isEqualTo(SupportAnswer.Status.GENERATION_FAILED);
        assertThat(answer.answer()).isNull();
        assertThat(answer.evidence()).isEmpty();
        assertThat(answer.diagnostics().generationAttempted()).isTrue();
        verify(aiCoreService, org.mockito.Mockito.times(2)).generateContent(any(), eq(LlmPurpose.GENERATION));

        ArgumentCaptor<RAGRequest> request = ArgumentCaptor.forClass(RAGRequest.class);
        verify(ragProvider).performRAGQuery(request.capture());
        assertThat(request.getValue().getEntityType()).isEqualTo("knowledge-article");
        assertThat(request.getValue().getLimit()).isEqualTo(5);
        assertThat(request.getValue().getThreshold()).isEqualTo(0.55);
        assertThat(request.getValue().getFilters())
            .containsEntry("tenantId", CourseDataService.COURSE_TENANT)
            .containsEntry("visibleToUser", true)
            .containsEntry("status", "PUBLISHED");
    }

    @Test
    void crossTenantVectorResultFailsClosedBeforeGeneration() {
        when(ragProvider.performRAGQuery(any())).thenReturn(RAGResponse.builder()
            .success(true)
            .requestId("retrieval-request")
            .documents(List.of(RAGResponse.RAGDocument.builder()
                .id("article-vpn-red")
                .content("Tenant Red private instructions")
                .score(0.99)
                .metadata(Map.of(
                    "title", "VPN recovery",
                    "category", "network-access",
                    "tenantId", CourseDataService.SECOND_TENANT,
                    "visibleToUser", true,
                    "status", "PUBLISHED"
                ))
                .build()))
            .build());

        SupportAnswer answer = service.answer("How do I restore VPN access?", PRINCIPAL);

        assertThat(answer.status()).isEqualTo(SupportAnswer.Status.RETRIEVAL_FAILED);
        assertThat(answer.answer()).isNull();
        assertThat(answer.evidence()).isEmpty();
        verify(aiCoreService, never()).generateContent(any(), any());
    }

    private RAGResponse successfulRetrieval() {
        return RAGResponse.builder()
            .success(true)
            .requestId("retrieval-request")
            .documents(List.of(RAGResponse.RAGDocument.builder()
                .id("policy-account-lockout-01")
                .content("Wait fifteen minutes, then recover the account.")
                .score(0.91)
                .metadata(Map.of(
                    "title", "Account lockout recovery policy",
                    "category", "authentication-policy",
                    "tenantId", CourseDataService.COURSE_TENANT,
                    "visibleToUser", true,
                    "status", "PUBLISHED",
                    "internalNotes", "must not leave the server"
                ))
                .build()))
            .build();
    }
}
