package dev.aifabric.course.support.demo;

import dev.aifabric.course.support.account.CustomerAccount;
import dev.aifabric.course.support.account.CustomerAccountRepository;
import dev.aifabric.course.support.knowledge.KnowledgeArticle;
import dev.aifabric.course.support.knowledge.KnowledgeArticleRepository;
import dev.aifabric.course.support.policy.SupportPolicy;
import dev.aifabric.course.support.policy.SupportPolicyRepository;
import dev.aifabric.course.support.ticket.SupportTicket;
import dev.aifabric.course.support.ticket.SupportTicketRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseDataService {

    public static final String COURSE_TENANT = "tenant-blue";
    public static final String COURSE_CUSTOMER = "customer-alex";

    private final KnowledgeArticleRepository articleRepository;
    private final SupportPolicyRepository policyRepository;
    private final CustomerAccountRepository accountRepository;
    private final SupportTicketRepository ticketRepository;

    public CourseDataService(KnowledgeArticleRepository articleRepository,
                             SupportPolicyRepository policyRepository,
                             CustomerAccountRepository accountRepository,
                             SupportTicketRepository ticketRepository) {
        this.articleRepository = articleRepository;
        this.policyRepository = policyRepository;
        this.accountRepository = accountRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public DatasetSnapshot reset() {
        ticketRepository.deleteAll();
        policyRepository.deleteAll();
        articleRepository.deleteAll();
        accountRepository.deleteAll();
        return snapshot();
    }

    @Transactional
    public DatasetSnapshot seed() {
        reset();
        accountRepository.save(new CustomerAccount(
            COURSE_CUSTOMER,
            COURSE_TENANT,
            "alex@example.test",
            "PRO",
            "CUSTOMER"
        ));
        articleRepository.saveAll(seedArticles());
        policyRepository.saveAll(seedPolicies());
        ticketRepository.save(new SupportTicket(
            "ticket-1001",
            COURSE_TENANT,
            COURSE_CUSTOMER,
            "Verification email has not arrived",
            "The account verification message has not arrived after two attempts.",
            "OPEN",
            "MEDIUM",
            Instant.parse("2026-07-01T10:00:00Z")
        ));
        return snapshot();
    }

    @Transactional(readOnly = true)
    public DatasetSnapshot snapshot() {
        return new DatasetSnapshot(
            articleRepository.count(),
            policyRepository.count(),
            accountRepository.count(),
            ticketRepository.count()
        );
    }

    private List<KnowledgeArticle> seedArticles() {
        return List.of(
            new KnowledgeArticle(
                "article-account-lockout",
                "Recover a locked account",
                "Wait fifteen minutes, then use account recovery to verify your email and reset access.",
                "authentication",
                COURSE_TENANT,
                "PUBLISHED",
                "Internal fraud review rules must never enter AI evidence."
            ),
            new KnowledgeArticle(
                "article-two-factor",
                "Replace a lost two-factor device",
                "Use a recovery code or contact support after completing identity verification.",
                "authentication",
                COURSE_TENANT,
                "PUBLISHED",
                "Do not expose manual identity-review notes."
            ),
            new KnowledgeArticle(
                "article-billing-method",
                "Update a billing method",
                "Open Billing Settings, add the replacement method, and verify it before the next renewal.",
                "billing",
                COURSE_TENANT,
                "PUBLISHED",
                "Payment processor references are private."
            ),
            new KnowledgeArticle(
                "article-cancel-plan",
                "Cancel a subscription",
                "Open Plan Settings and request cancellation. Access remains available until the paid period ends.",
                "subscriptions",
                COURSE_TENANT,
                "PUBLISHED",
                "Retention scoring is staff-only."
            ),
            new KnowledgeArticle(
                "article-api-key",
                "Rotate an API key",
                "Create a replacement key, update dependent services, then revoke the previous key.",
                "developer-tools",
                COURSE_TENANT,
                "PUBLISHED",
                "Never store actual keys in an article."
            )
        );
    }

    private List<SupportPolicy> seedPolicies() {
        return List.of(
            new SupportPolicy(
                "policy-escalation",
                "Ticket escalation policy",
                "Customers may escalate an open ticket after troubleshooting steps have been attempted.",
                COURSE_TENANT,
                "PUBLISHED"
            ),
            new SupportPolicy(
                "policy-privacy",
                "Sensitive information policy",
                "Support requests must redact credentials, payment data, and government identifiers before storage.",
                COURSE_TENANT,
                "PUBLISHED"
            )
        );
    }

    public record DatasetSnapshot(long articles, long policies, long accounts, long tickets) {
    }
}
