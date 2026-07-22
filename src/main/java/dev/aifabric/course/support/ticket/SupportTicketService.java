package dev.aifabric.course.support.ticket;

import dev.aifabric.course.support.account.CustomerAccount;
import dev.aifabric.course.support.account.CustomerAccountRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SupportTicketService {

    private static final int MAX_SUBJECT_LENGTH = 180;
    private static final int MAX_DESCRIPTION_LENGTH = 4_000;

    private final SupportTicketRepository ticketRepository;
    private final CustomerAccountRepository accountRepository;

    public SupportTicketService(SupportTicketRepository ticketRepository,
                                CustomerAccountRepository accountRepository) {
        this.ticketRepository = ticketRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public boolean isKnownCustomerContext(String customerId, String tenantId) {
        if (!StringUtils.hasText(customerId) || !StringUtils.hasText(tenantId)) {
            return false;
        }
        return accountRepository.findById(customerId.trim())
            .map(account -> tenantId.trim().equals(account.getTenantId()))
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public TicketView getForCurrentCustomer(String ticketNumber, String customerId, String tenantId) {
        requireCurrentCustomer(customerId, tenantId);
        if (!StringUtils.hasText(ticketNumber)) {
            throw new IllegalArgumentException("ticket number is required");
        }
        SupportTicket ticket = ticketRepository.findByIdAndTenantIdAndCustomerId(
                ticketNumber.trim().toUpperCase(Locale.ROOT), tenantId.trim(), customerId.trim())
            .orElseThrow(() -> new TicketAccessDeniedException("Ticket was not found for the current customer"));
        return project(ticket);
    }

    @Transactional
    public TicketView createForCurrentCustomer(String subject, String description, String priority,
                                               String customerId, String tenantId) {
        requireCurrentCustomer(customerId, tenantId);
        String safeSubject = requireBoundedText(subject, "subject", MAX_SUBJECT_LENGTH);
        String safeDescription = requireBoundedText(description, "description", MAX_DESCRIPTION_LENGTH);
        String safePriority = normalizePriority(priority);

        SupportTicket ticket = new SupportTicket(
            nextTicketNumber(),
            tenantId.trim(),
            customerId.trim(),
            safeSubject,
            safeDescription,
            "OPEN",
            safePriority,
            Instant.now()
        );
        return project(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketView escalateForCurrentCustomer(String ticketNumber, String customerId, String tenantId) {
        requireCurrentCustomer(customerId, tenantId);
        if (!StringUtils.hasText(ticketNumber)) {
            throw new IllegalArgumentException("ticket number is required");
        }
        SupportTicket ticket = ticketRepository.findByIdAndTenantIdAndCustomerId(
                ticketNumber.trim().toUpperCase(Locale.ROOT), tenantId.trim(), customerId.trim())
            .orElseThrow(() -> new TicketAccessDeniedException("Ticket was not found for the current customer"));
        if (!"ESCALATED".equals(ticket.getStatus())) {
            ticket.setStatus("ESCALATED");
        }
        return project(ticketRepository.save(ticket));
    }

    private CustomerAccount requireCurrentCustomer(String customerId, String tenantId) {
        if (!StringUtils.hasText(customerId) || !StringUtils.hasText(tenantId)) {
            throw new TicketAccessDeniedException("Authenticated customer and tenant are required");
        }
        CustomerAccount account = accountRepository.findById(customerId.trim())
            .orElseThrow(() -> new TicketAccessDeniedException("Authenticated customer was not found"));
        if (!tenantId.trim().equals(account.getTenantId())) {
            throw new TicketAccessDeniedException("Customer is not authorized for this tenant");
        }
        return account;
    }

    private String requireBoundedText(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private String normalizePriority(String priority) {
        String normalized = StringUtils.hasText(priority)
            ? priority.trim().toUpperCase(Locale.ROOT)
            : "NORMAL";
        return switch (normalized) {
            case "LOW", "NORMAL", "HIGH" -> normalized;
            default -> throw new IllegalArgumentException("priority must be LOW, NORMAL, or HIGH");
        };
    }

    private String nextTicketNumber() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "T-" + Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits());
            if (!ticketRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique ticket number");
    }

    private TicketView project(SupportTicket ticket) {
        return new TicketView(
            ticket.getId(),
            ticket.getSubject(),
            ticket.getStatus(),
            ticket.getPriority(),
            ticket.getUpdatedAt()
        );
    }
}
