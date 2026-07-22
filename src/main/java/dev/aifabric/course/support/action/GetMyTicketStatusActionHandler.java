package dev.aifabric.course.support.action;

import ai.fabric.intent.action.ActionAccessMode;
import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.action.ActionResult;
import ai.fabric.intent.action.ActionResultContracts;
import ai.fabric.intent.action.ActionTargetRef;
import ai.fabric.intent.action.annotation.AIAction;
import ai.fabric.intent.action.annotation.ActionAllowed;
import ai.fabric.intent.action.annotation.ActionExecute;
import ai.fabric.intent.action.annotation.Param;
import dev.aifabric.course.support.ticket.SupportTicketService;
import dev.aifabric.course.support.ticket.TicketView;
import java.util.List;
import java.util.Map;

@AIAction(
    name = "get_my_ticket_status",
    description = "Read the status of one support ticket owned by the current customer",
    category = "support",
    accessMode = ActionAccessMode.READ,
    requiresConfirmation = false,
    readActionResolutionEligible = true
)
public class GetMyTicketStatusActionHandler {

    private final SupportTicketService ticketService;

    public GetMyTicketStatusActionHandler(SupportTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @ActionAllowed
    public boolean allowed(ActionContext context) {
        return context != null && ticketService.isKnownCustomerContext(
            context.userId(), context.authContext().getTenantId());
    }

    @ActionExecute
    public ActionResult execute(
        @Param(
            value = "ticketNumber",
            description = "Ticket number such as T-1001",
            required = true,
            pattern = "T-[0-9]+"
        ) String ticketNumber,
        ActionContext context
    ) {
        try {
            TicketView ticket = ticketService.getForCurrentCustomer(
                ticketNumber, context.userId(), context.authContext().getTenantId());
            return ActionResult.builder()
                .success(true)
                .message("Ticket status loaded")
                .data(ActionResultContracts.object(Map.of(
                    "ticketNumber", ticket.ticketNumber(),
                    "status", ticket.status(),
                    "priority", ticket.priority(),
                    "updatedAt", ticket.updatedAt().toString()
                )))
                .pinnedTargets(List.of(ticketTarget(ticket)))
                .build();
        } catch (RuntimeException exception) {
            return ActionResult.builder()
                .success(false)
                .message("Ticket status is not available for the current customer")
                .errorCode("TICKET_NOT_FOUND_OR_NOT_ALLOWED")
                .build();
        }
    }

    private ActionTargetRef ticketTarget(TicketView ticket) {
        return new ActionTargetRef(
            ticket.ticketNumber(),
            "support-ticket",
            ticket.subject(),
            Map.of("ticketNumber", ticket.ticketNumber(), "status", ticket.status())
        );
    }
}
