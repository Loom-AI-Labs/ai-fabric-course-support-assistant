# Backend-Owned Conversation Memory Contract

This checkpoint uses `ai-fabric-chat-session` as the conversation authority. The public request
contains only the current message, a stable conversation ID, and optional current attachments.
The backend derives the owner and tenant from `CoursePrincipalProvider`; request JSON cannot supply
history, pending actions, action drafts, owner IDs, or tenant IDs.

## Runtime Flow

```text
POST /api/assistant/orchestrate
  -> backend principal and bounded request validation
  -> CourseConversationAuthorization
  -> ConversationEnrichmentStep
       loads up to 8 recent turns / 4,000 characters
       reuses bounded pinned targets for at most 3 turns
       loads the owner-scoped pending-action stack
  -> LLM interprets only the new message against role-aware history and registered actions
  -> application action authorization and transaction
  -> ConversationRecordingStep stores the sanitized turn
  -> ChatSessionPendingActionStore persists at most 4 pending confirmations
```

There is no keyword router or canned successful response. The deterministic tests inject structured
intent fixture data, then exercise the real AI Fabric pipeline. The OpenAI profile exercises real
model interpretation and exposes malformed/provider failures.

## Prompt Overlay

Conversation history is evidence for interpreting a follow-up, but it is not permission to repeat
a completed mutation. The application therefore places `v1-course-support` before the curated
`v1-support` overlay and base `v1` bundle. The course overlay adds one rule to both the compound
fast path and multi-step fallback: `yes` or `ok` is a confirmation only while AI Fabric has injected
an owner-scoped pending action. After execution, the same text is an acknowledgement and cannot
reconstruct the completed action from history or a pinned action result.

This remains LLM intent classification. It is not a Java keyword branch, and confirmation storage,
one-time consumption, action authorization, and execution remain AI Fabric and application runtime
responsibilities. `CoursePromptOverlayContractTest` proves overlay precedence and fallback.

## Ownership

`CourseConversationAuthorization` permits a bounded conversation ID only for a customer that exists
in application data. `ChatSessionService` then independently compares the requesting owner with the
stored session owner. Both checks must pass. Ticket actions separately recheck customer and tenant
ownership inside `SupportTicketService` transactions.

## Presentation And Persistence

Closing a chat panel is presentation state. `GET /api/assistant/conversations/{conversationId}`
reloads the authorized sanitized turns without deleting or rewriting the session. A deliberate reset
can use the protected delete endpoint or `/api/demo/reset`, which removes the course customer's
session records before resetting source data.

Trusted application code can set `queryPersistenceMode=NEVER_PERSIST` through
`SupportOrchestrationService.orchestrateTransient`. That request neither reads prior conversation
history nor records the current turn. This mode is intentionally absent from the public request.

## Bounds

| State | Bound |
| --- | --- |
| recent prompt history | 8 turns |
| prompt history characters | 4,000, dropping oldest whole messages |
| reused pinned target age | 3 turns |
| pending confirmation stack | 4 actions |
| current request attachments | 8 |
| conversation ID | 128 safe characters |

Tests in `ConversationMemoryIntegrationTest` prove the request boundary, runtime beans, first-turn
recording, role-aware follow-up history, new-conversation isolation, stored-owner mismatch, bounds,
one-time confirmation, transient behavior, and non-destructive reopen.
