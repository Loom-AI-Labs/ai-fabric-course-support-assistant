package dev.aifabric.course.support.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.fabric.config.OrchestrationProperties;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupportModeResolverTest {

    private OrchestrationProperties properties;
    private SupportModeResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new OrchestrationProperties();
        properties.setDefaultMode("support_resolver");
        properties.setStrictPositionRouting(true);
        properties.setPositionRouting(new LinkedHashMap<>(java.util.Map.of(
            "knowledge", "support_assistant",
            "ticket", "support_resolver"
        )));
        resolver = new SupportModeResolver(properties);
    }

    @Test
    void mapsPositionWhenNoModeWasExplicitlyRequested() {
        SupportModeResolver.ResolvedRouting routing = resolver.resolve(null, "ticket");

        assertThat(routing.mode()).isEqualTo("support_resolver");
        assertThat(routing.position()).isEqualTo("ticket");
        assertThat(routing.source()).isEqualTo("POSITION_MAP");
    }

    @Test
    void preservesExplicitModeForCoreAllowlistValidation() {
        SupportModeResolver.ResolvedRouting routing = resolver.resolve("SUPPORT_ASSISTANT", "ticket");

        assertThat(routing.mode()).isEqualTo("support_assistant");
        assertThat(routing.source()).isEqualTo("REQUEST_MODE");
    }

    @Test
    void keepsTheEstablishedActionCapableDefaultWhenNoRoutingHintIsProvided() {
        SupportModeResolver.ResolvedRouting routing = resolver.resolve(null, null);

        assertThat(routing.mode()).isEqualTo("support_resolver");
        assertThat(routing.position()).isEqualTo("support");
        assertThat(routing.source()).isEqualTo("DEFAULT_MODE");
    }

    @Test
    void rejectsUnknownPositionInStrictApplicationRouting() {
        assertThatThrownBy(() -> resolver.resolve(null, "admin-console"))
            .isInstanceOf(UnsupportedSupportPositionException.class)
            .hasMessage("Unsupported support position: admin-console");
    }
}
