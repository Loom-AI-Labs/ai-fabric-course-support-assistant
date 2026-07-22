package dev.aifabric.course.support.ticket;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, String> {

    List<SupportTicket> findByTenantIdAndCustomerIdOrderByUpdatedAtDesc(String tenantId, String customerId);
}
