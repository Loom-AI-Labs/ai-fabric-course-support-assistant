package dev.aifabric.course.support.message;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, String> {

    List<SupportMessage> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(String tenantId, String customerId);
}
