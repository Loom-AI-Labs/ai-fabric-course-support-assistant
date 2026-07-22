package dev.aifabric.course.support.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.aifabric.course.support.account.CustomerAccountRepository;
import dev.aifabric.course.support.knowledge.KnowledgeArticleRepository;
import dev.aifabric.course.support.policy.SupportPolicyRepository;
import dev.aifabric.course.support.ticket.SupportTicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(CourseDataService.class)
class CourseDataServiceTest {

    @Autowired
    private CourseDataService dataService;

    @Autowired
    private KnowledgeArticleRepository articleRepository;

    @Autowired
    private SupportPolicyRepository policyRepository;

    @Autowired
    private CustomerAccountRepository accountRepository;

    @Autowired
    private SupportTicketRepository ticketRepository;

    @Test
    void seedCreatesTheDeterministicCourseDataset() {
        CourseDataService.DatasetSnapshot snapshot = dataService.seed();

        assertThat(snapshot.articles()).isEqualTo(9);
        assertThat(snapshot.policies()).isEqualTo(2);
        assertThat(snapshot.accounts()).isEqualTo(2);
        assertThat(snapshot.tickets()).isEqualTo(2);
        assertThat(articleRepository.findById("article-account-lockout")).isPresent();
        assertThat(policyRepository.findById("policy-privacy")).isPresent();
        assertThat(accountRepository.findById(CourseDataService.COURSE_CUSTOMER)).isPresent();
        assertThat(ticketRepository.findById("T-1001")).isPresent();
        assertThat(ticketRepository.findById("T-2002")).isPresent();
    }

    @Test
    void resetRemovesOnlyTheCourseDataset() {
        dataService.seed();

        CourseDataService.DatasetSnapshot snapshot = dataService.reset();

        assertThat(snapshot.articles()).isZero();
        assertThat(snapshot.policies()).isZero();
        assertThat(snapshot.accounts()).isZero();
        assertThat(snapshot.tickets()).isZero();
    }
}
