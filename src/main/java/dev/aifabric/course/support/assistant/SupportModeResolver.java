package dev.aifabric.course.support.assistant;

import ai.fabric.config.OrchestrationProperties;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SupportModeResolver {

    private final OrchestrationProperties properties;

    public SupportModeResolver(OrchestrationProperties properties) {
        this.properties = properties;
    }

    public ResolvedRouting resolve(String requestedMode, String requestedPosition) {
        String position = normalize(requestedPosition);
        String mode = normalize(requestedMode);
        if (StringUtils.hasText(mode)) {
            return new ResolvedRouting(StringUtils.hasText(position) ? position : "support", mode, "REQUEST_MODE");
        }

        if (!StringUtils.hasText(position)) {
            return new ResolvedRouting("support", normalize(properties.getDefaultMode()), "DEFAULT_MODE");
        }

        String mappedMode = lookupPosition(position);
        if (StringUtils.hasText(mappedMode)) {
            return new ResolvedRouting(position, normalize(mappedMode), "POSITION_MAP");
        }

        if (properties.isStrictPositionRouting()) {
            throw new UnsupportedSupportPositionException(position);
        }

        return new ResolvedRouting(position, normalize(properties.getDefaultMode()), "DEFAULT_MODE");
    }

    private String lookupPosition(String position) {
        Map<String, String> routes = properties.getPositionRouting();
        if (routes == null) {
            return null;
        }
        return routes.entrySet().stream()
            .filter(entry -> normalize(entry.getKey()).equals(position))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    public record ResolvedRouting(String position, String mode, String source) {
    }
}
