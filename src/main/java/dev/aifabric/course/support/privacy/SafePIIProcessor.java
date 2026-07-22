package dev.aifabric.course.support.privacy;

import ai.fabric.dto.PIIDetection;
import ai.fabric.dto.PIIDetectionResult;
import ai.fabric.dto.PIIMode;
import ai.fabric.privacy.pii.PIIDetectionService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SafePIIProcessor {

    private final PIIDetectionService piiDetectionService;

    public SafePIIProcessor(PIIDetectionService piiDetectionService) {
        this.piiDetectionService = piiDetectionService;
    }

    public SafeText process(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            throw new PrivacyBoundaryException("Non-blank text is required for privacy processing");
        }

        PIIDetectionResult result;
        try {
            result = piiDetectionService.detectAndProcess(rawText);
        } catch (RuntimeException exception) {
            throw new PrivacyBoundaryException("PII processing failed closed", exception);
        }

        if (result == null
            || result.getModeApplied() != PIIMode.REDACT
            || !StringUtils.hasText(result.getProcessedQuery())) {
            throw new PrivacyBoundaryException("PII redaction could not be proved");
        }
        if (result.isPiiDetected()
            && (rawText.equals(result.getProcessedQuery()) || result.getOriginalQuery() != null)) {
            throw new PrivacyBoundaryException("Detected PII was not safely redacted");
        }

        List<String> detectedTypes = result.getDetections() == null
            ? List.of()
            : result.getDetections().stream()
                .map(PIIDetection::getType)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return new SafeText(result.getProcessedQuery(), result.isPiiDetected(), detectedTypes);
    }

    public record SafeText(String value, boolean piiDetected, List<String> detectedTypes) {
        public SafeText {
            detectedTypes = detectedTypes == null ? List.of() : List.copyOf(detectedTypes);
        }
    }
}
