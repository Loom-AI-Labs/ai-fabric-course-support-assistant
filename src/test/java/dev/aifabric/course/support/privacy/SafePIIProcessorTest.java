package dev.aifabric.course.support.privacy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.fabric.dto.PIIDetectionResult;
import ai.fabric.dto.PIIMode;
import ai.fabric.privacy.pii.PIIDetectionService;
import org.junit.jupiter.api.Test;

class SafePIIProcessorTest {

    private static final String RAW = "Email owner@example.com";

    private final PIIDetectionService detectionService = mock(PIIDetectionService.class);
    private final SafePIIProcessor processor = new SafePIIProcessor(detectionService);

    @Test
    void detectorExceptionFailsClosed() {
        when(detectionService.detectAndProcess(RAW))
            .thenThrow(new IllegalStateException("detector unavailable"));

        assertThatThrownBy(() -> processor.process(RAW))
            .isInstanceOf(PrivacyBoundaryException.class)
            .hasMessageContaining("failed closed");
    }

    @Test
    void detectedPiiWithoutAChangedPayloadFailsClosed() {
        when(detectionService.detectAndProcess(RAW)).thenReturn(PIIDetectionResult.builder()
            .originalQuery(null)
            .processedQuery(RAW)
            .piiDetected(true)
            .modeApplied(PIIMode.REDACT)
            .build());

        assertThatThrownBy(() -> processor.process(RAW))
            .isInstanceOf(PrivacyBoundaryException.class)
            .hasMessageContaining("not safely redacted");
    }

    @Test
    void rawOriginalExposureFailsClosedEvenWhenProcessedTextWasChanged() {
        when(detectionService.detectAndProcess(RAW)).thenReturn(PIIDetectionResult.builder()
            .originalQuery(RAW)
            .processedQuery("Email ***@***.***")
            .piiDetected(true)
            .modeApplied(PIIMode.REDACT)
            .build());

        assertThatThrownBy(() -> processor.process(RAW))
            .isInstanceOf(PrivacyBoundaryException.class)
            .hasMessageContaining("not safely redacted");
    }
}
