package dev.aifabric.course.support.message;

import ai.fabric.config.AIEntityConfigurationLoader;
import ai.fabric.dto.AIEntityConfig;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.service.AICapabilityService;
import dev.aifabric.course.support.identity.CourseAuthorizationService;
import dev.aifabric.course.support.identity.CoursePrincipal;
import dev.aifabric.course.support.privacy.PrivacyBoundaryException;
import dev.aifabric.course.support.privacy.SafePIIProcessor;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SupportMessageService {

    private final SupportMessageRepository repository;
    private final CourseAuthorizationService authorizationService;
    private final SafePIIProcessor piiProcessor;
    private final AICapabilityService capabilityService;
    private final AIEntityConfigurationLoader configurationLoader;
    private final VectorDatabaseService vectorDatabaseService;

    public SupportMessageService(SupportMessageRepository repository,
                                 CourseAuthorizationService authorizationService,
                                 SafePIIProcessor piiProcessor,
                                 AICapabilityService capabilityService,
                                 AIEntityConfigurationLoader configurationLoader,
                                 VectorDatabaseService vectorDatabaseService) {
        this.repository = repository;
        this.authorizationService = authorizationService;
        this.piiProcessor = piiProcessor;
        this.capabilityService = capabilityService;
        this.configurationLoader = configurationLoader;
        this.vectorDatabaseService = vectorDatabaseService;
    }

    @Transactional
    public MessageView submit(String rawContent, CoursePrincipal principal) {
        CoursePrincipal authorized = authorizationService.requireScope(principal, "support:write");
        SafePIIProcessor.SafeText safe = piiProcessor.process(rawContent);
        SupportMessage message = repository.saveAndFlush(new SupportMessage(
            "message-" + UUID.randomUUID(),
            authorized.tenantId(),
            authorized.userId(),
            safe.value(),
            String.join(",", safe.detectedTypes()),
            true,
            Instant.now()
        ));
        try {
            index(message);
        } catch (RuntimeException exception) {
            vectorDatabaseService.removeVector(SupportMessage.ENTITY_TYPE, message.getId());
            throw exception;
        }
        return project(message, safe.piiDetected(), safe.detectedTypes());
    }

    @Transactional(readOnly = true)
    public List<MessageView> list(CoursePrincipal principal) {
        CoursePrincipal authorized = authorizationService.requireScope(principal, "support:read");
        return repository.findByTenantIdAndCustomerIdOrderByCreatedAtDesc(
                authorized.tenantId(), authorized.userId()).stream()
            .map(message -> project(message, StringUtils.hasText(message.getDetectedPiiTypes()),
                detectedTypes(message.getDetectedPiiTypes())))
            .toList();
    }

    public void clearVectors() {
        vectorDatabaseService.clearVectorsByEntityType(SupportMessage.ENTITY_TYPE);
    }

    public long indexedCount() {
        return vectorDatabaseService.getVectorCountByEntityType(SupportMessage.ENTITY_TYPE);
    }

    private void index(SupportMessage message) {
        if (!StringUtils.hasText(message.getTenantId()) || !message.isVisibleToUser()) {
            throw new PrivacyBoundaryException("Support message is missing required evidence metadata");
        }
        AIEntityConfig config = configurationLoader.getEntityConfig(SupportMessage.ENTITY_TYPE);
        if (config == null) {
            throw new PrivacyBoundaryException("Support message AI configuration is missing");
        }
        capabilityService.indexForSearch(message, config);
        if (!vectorDatabaseService.vectorExists(SupportMessage.ENTITY_TYPE, message.getId())) {
            throw new PrivacyBoundaryException("Redacted support message was not indexed");
        }
    }

    private List<String> detectedTypes(String storedTypes) {
        if (!StringUtils.hasText(storedTypes)) {
            return List.of();
        }
        return Arrays.stream(storedTypes.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toList();
    }

    private MessageView project(SupportMessage message, boolean piiDetected, List<String> detectedTypes) {
        return new MessageView(
            message.getId(),
            message.getSafeContent(),
            piiDetected,
            detectedTypes,
            message.getCreatedAt()
        );
    }

    public record MessageView(String id, String safeContent, boolean piiDetected,
                              List<String> detectedTypes, Instant createdAt) {
        public MessageView {
            detectedTypes = detectedTypes == null ? List.of() : List.copyOf(detectedTypes);
        }
    }
}
