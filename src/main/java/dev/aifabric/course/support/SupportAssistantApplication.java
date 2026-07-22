package dev.aifabric.course.support;

import ai.fabric.annotation.EnableAIInfrastructure;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableAIInfrastructure
@EntityScan(basePackages = {
    "dev.aifabric.course.support",
    "ai.fabric.chat.domain",
    "ai.fabric.entity",
    "ai.fabric.migration.domain"
})
@EnableJpaRepositories(basePackages = {
    "dev.aifabric.course.support",
    "ai.fabric.repository",
    "ai.fabric.migration.repository"
})
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SupportAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportAssistantApplication.class, args);
    }
}
