package dev.aifabric.course.support.action;

import ai.fabric.intent.action.ActionAccessMode;
import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.action.ActionResult;
import ai.fabric.intent.action.ActionResultContracts;
import ai.fabric.intent.action.ActionTargetRef;
import ai.fabric.intent.action.annotation.AIAction;
import ai.fabric.intent.action.annotation.ActionAllowed;
import ai.fabric.intent.action.annotation.ActionConfirmation;
import ai.fabric.intent.action.annotation.ActionExecute;
import ai.fabric.intent.action.annotation.Param;
import dev.aifabric.course.support.ticket.SupportTicketService;
import dev.aifabric.course.support.ticket.TicketView;
import java.util.List;
import java.util.Map;

@AIAction(
    name = "escalate_support_ticket",
    description = "Escalate one support ticket owned by the current customer to tier-two support",
    category = "support",
    accessMode = ActionAccessMode.WRITE_ONLY,
    requiresConfirmation = true
)
public class EscalateSupportTicketActionHandler {

    private final SupportTicketService ticketService;

    public EscalateSupportTicketActionHandler(SupportTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @ActionAllowed
    public boolean allowed(ActionContext context) {
        String ticketNumber = ActionAuthorizationSupport.actionParameter(context, "ticketNumber");
        return ActionAuthorizationSupport.hasScopeAndRole(context, "support:write", "CUSTOMER")
            && (ticketNumber == null || ticketService.canAccessTicket(
                ticketNumber, context.userId(), context.authContext().getTenantId()));
    }

    @ActionConfirmation
    public String confirmation(@Param(value = "ticketNumber", required = true) String ticketNumber) {
        return "Escalate ticket " + ticketNumber + " to tier-two support?";
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
            TicketView ticket = ticketService.escalateForCurrentCustomer(
                ticketNumber, context.userId(), context.authContext().getTenantId());
            return ActionResult.builder()
                .success(true)
                .message("Support ticket escalated")
                .data(ActionResultContracts.object(Map.of(
                    "ticketNumber", ticket.ticketNumber(),
                    "status", ticket.status(),
                    "priority", ticket.priority()
                )))
                .pinnedTargets(List.of(new ActionTargetRef(
                    ticket.ticketNumber(),
                    "support-ticket",
                    ticket.subject(),
                    Map.of("ticketNumber", ticket.ticketNumber(), "status", ticket.status())
                )))
                .build();
        } catch (RuntimeException exception) {
            return ActionResult.builder()
                .success(false)
                .message("Support ticket could not be escalated")
                .errorCode("ESCALATE_TICKET_FAILED")
                .build();
        }
    }
}
