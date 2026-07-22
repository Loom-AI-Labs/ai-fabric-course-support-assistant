package dev.aifabric.course.support.identity;

import dev.aifabric.course.support.account.CustomerAccount;
import dev.aifabric.course.support.account.CustomerAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CourseAuthorizationService {

    private final CustomerAccountRepository accountRepository;

    public CourseAuthorizationService(CustomerAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public CoursePrincipal requireScope(CoursePrincipal principal, String requiredScope) {
        if (principal == null || !principal.authenticated() || !StringUtils.hasText(requiredScope)) {
            throw new CourseAccessDeniedException("Authenticated tenant context is required");
        }
        CustomerAccount account = accountRepository.findById(principal.userId())
            .orElseThrow(() -> new CourseAccessDeniedException("Authenticated account is not available"));
        if (!principal.tenantId().equals(account.getTenantId())
            || !principal.scopes().contains(requiredScope)
            || !principal.roles().contains(account.getRoles())) {
            throw new CourseAccessDeniedException("The authenticated account is not allowed to use this capability");
        }
        return principal;
    }
}
