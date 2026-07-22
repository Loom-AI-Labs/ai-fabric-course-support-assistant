package dev.aifabric.course.support.ticket;

import java.time.Instant;

public record TicketView(
    String ticketNumber,
    String subject,
    String status,
    String priority,
    Instant updatedAt
) {
}
