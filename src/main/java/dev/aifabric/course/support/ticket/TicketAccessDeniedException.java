package dev.aifabric.course.support.ticket;

public class TicketAccessDeniedException extends RuntimeException {

    public TicketAccessDeniedException(String message) {
        super(message);
    }
}
