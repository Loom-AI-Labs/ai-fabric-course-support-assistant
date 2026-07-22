package dev.aifabric.course.support.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class OperationsProfileConfigurationTest {

    @Test
    void operationsProfileUsesDurableDatabaseAndExplicitMaintenanceControls() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
            .load("course-operations", new ClassPathResource("application-operations.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        assertThat(resolver.getProperty("spring.datasource.url"))
            .startsWith("jdbc:h2:file:")
            .contains("course-support");
        assertThat(resolver.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(resolver.getProperty("course.release.runtime-mode")).isEqualTo("production-keyless");
        assertThat(resolver.getProperty("course.operations.maintenance-enabled", Boolean.class)).isTrue();
        assertThat(resolver.getProperty("course.operations.release-probes-enabled", Boolean.class)).isTrue();
        assertThat(resolver.getProperty("course.operations.completed-record-retention")).isEqualTo("PT168H");
        assertThat(resolver.getProperty("course.operations.conversation-retention")).isEqualTo("PT168H");
    }
}
