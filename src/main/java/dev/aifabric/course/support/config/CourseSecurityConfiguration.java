package dev.aifabric.course.support.config;

import dev.aifabric.course.support.identity.CourseBearerAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CourseSecurityProperties.class)
public class CourseSecurityConfiguration {

    @Bean
    SecurityFilterChain courseSecurityFilterChain(HttpSecurity http,
                                                  CourseBearerAuthenticationFilter authenticationFilter)
        throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/info", "/api/demo/**").permitAll()
                .requestMatchers("/api/internal/ai-data-sync/**").denyAll()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    response.sendError(HttpStatus.UNAUTHORIZED.value(), "Authentication required"))
                .accessDeniedHandler((request, response, exception) ->
                    response.sendError(HttpStatus.FORBIDDEN.value(), "Access denied")))
            .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
