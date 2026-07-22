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
    name = "create_support_ticket",
    description = "Create a support ticket for the current authenticated customer",
    category = "support",
    accessMode = ActionAccessMode.WRITE_ONLY,
    requiresConfirmation = true
)
public class CreateSupportTicketActionHandler {

    private final SupportTicketService ticketService;

    public CreateSupportTicketActionHandler(SupportTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @ActionAllowed
    public boolean allowed(ActionContext context) {
        return context != null && ticketService.isKnownCustomerContext(
            context.userId(), context.authContext().getTenantId());
    }

    @ActionConfirmation
    public String confirmation(@Param(value = "subject", required = true) String subject) {
        return "Create a support ticket titled '" + subject + "'?";
    }

    @ActionExecute
    public ActionResult execute(
        @Param(
            value = "subject",
            description = "Short support-ticket subject",
            required = true
        ) String subject,
        @Param(
            value = "description",
            description = "What happened and what help is needed",
            required = true
        ) String description,
        @Param(
            value = "priority",
            description = "Requested priority",
            allowedValues = {"LOW", "NORMAL", "HIGH"}
        ) String priority,
        ActionContext context
    ) {
        try {
            TicketView ticket = ticketService.createForCurrentCustomer(
                subject,
                description,
                priority,
                context.userId(),
                context.authContext().getTenantId()
            );
            return ActionResult.builder()
                .success(true)
                .message("Support ticket created")
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
                .message("Support ticket could not be created")
                .errorCode("CREATE_SUPPORT_TICKET_FAILED")
                .build();
        }
    }
}
