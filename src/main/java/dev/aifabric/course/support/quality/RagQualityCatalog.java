package dev.aifabric.course.support.quality;

import dev.aifabric.course.support.demo.CourseDataService;
import dev.aifabric.course.support.identity.CoursePrincipal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RagQualityCatalog {

    public List<RagQualityService.GoldenQuestion> questionsFor(CoursePrincipal principal) {
        if (CourseDataService.SECOND_TENANT.equals(principal.tenantId())) {
            return List.of(new RagQualityService.GoldenQuestion(
                "tenant-red-vpn",
                "How do I reconnect to the VPN after replacing my device?",
                List.of("article-vpn-red"),
                List.of("article-vpn-blue", "article-payroll-red-restricted"),
                List.of("replacement device"),
                List.of("Tenant Blue", "restricted finance")
            ));
        }

        return List.of(
            new RagQualityService.GoldenQuestion(
                "account-lockout",
                "What should I do after repeated failed sign-in attempts locked my account?",
                List.of("policy-account-lockout-01"),
                List.of("article-vpn-red", "article-payroll-red-restricted"),
                List.of("fifteen minutes", "registered email"),
                List.of("fraud-review", "staff-only")
            ),
            new RagQualityService.GoldenQuestion(
                "billing-method",
                "How do I replace my billing method before renewal?",
                List.of("article-billing-method"),
                List.of("article-vpn-red", "article-payroll-red-restricted"),
                List.of("replacement method", "next renewal"),
                List.of("processor references")
            ),
            new RagQualityService.GoldenQuestion(
                "tenant-blue-vpn",
                "How should I reconnect to the VPN?",
                List.of("article-vpn-blue"),
                List.of("article-vpn-red", "article-payroll-red-restricted"),
                List.of("device certificate"),
                List.of("Tenant Red", "restricted finance")
            )
        );
    }
}
