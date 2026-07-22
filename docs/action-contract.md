# Governed Support Action Contract

AI Fabric may select only the actions registered in `AIActionRegistry`. The application supplies
identity, tenant context, authorization, transactions, and safe result projection.

| Action | Access mode | Model-visible parameters | Application-owned context | Confirmation |
| --- | --- | --- | --- | --- |
| `get_my_ticket_status` | `READ` | `ticketNumber` | subject, tenant, session | No |
| `create_support_ticket` | `WRITE_ONLY` | `subject`, `description`, optional `priority` | subject, tenant, conversation | Yes |

The action schema must never expose `userId`, `tenantId`, `customerId`, `conversationId`, or
`sessionId` as model-populated parameters. `CoursePrincipalProvider` supplies a fixed trusted
learner identity at this checkpoint. The security checkpoint replaces that adapter with verified
request identity without changing either action contract.

`CourseAccessControlConfiguration` grants only the framework orchestration entry resource
`rag:intent` with operation `READ` to an identified request subject or anonymous session. This is a
query-entry gate. `@ActionAllowed` then denies anonymous and invalid customer contexts, while
`SupportTicketService` independently enforces customer and tenant ownership inside each database
transaction.

The current core `InMemoryPendingActionStore` is sufficient to demonstrate confirmation, but it is
process-local and not durable. The next checkpoint replaces it with `ai-fabric-chat-session`, which
owns pending actions and conversation turns in backend storage.
