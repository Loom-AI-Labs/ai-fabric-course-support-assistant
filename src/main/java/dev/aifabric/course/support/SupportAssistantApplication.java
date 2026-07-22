package dev.aifabric.course.support;

import ai.fabric.annotation.EnableAIInfrastructure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@EnableAIInfrastructure
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SupportAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportAssistantApplication.class, args);
    }
}
