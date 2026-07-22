# Tenant Security And Privacy Contract

This checkpoint treats identity, tenant scope, authorization, and privacy as application-owned
inputs to AI Fabric. The model never chooses a user, tenant, scope, role, visibility rule, or PII
policy.

## Request Boundary

```text
Authorization: Bearer <server-configured credential>
  -> CourseBearerAuthenticationFilter
  -> CourseTokenRegistry constant-time credential comparison
  -> authenticated CourseAuthenticatedPrincipal
  -> CoursePrincipalProvider
  -> application account, tenant, role, and scope verification
```

Public request JSON contains the current message, conversation ID, and optional attachments. It
cannot provide owner IDs, tenant IDs, roles, scopes, pending actions, or history. The two local
course credentials make isolation visible; production applications should adapt a verified
OAuth2/JWT or gateway principal at the same boundary.

## Tenant Evidence Flow

```text
verified principal
  -> require support:read and matching application account
  -> AI Fabric search / RAG request
       tenantId = verified tenant
       visibleToUser = true
       status = PUBLISHED
  -> provider metadata filtering
  -> application post-hit exact-match verification
  -> safe public metadata projection
  -> approved evidence context for generation
```

Provider filtering is necessary but not sufficient. `KnowledgeEvidenceService` and
`SupportAssistantService` reject the complete retrieval operation if any returned record violates
the required metadata terms. Restricted and cross-tenant records therefore cannot become prompt
context, citations, or public results even if an adapter regresses. Provider bookkeeping metadata
and tenant fields are used internally and omitted from public evidence projections.

## Governed Action Flow

```text
LLM proposes typed action parameters
  -> @ActionAllowed checks verified scope and role
  -> explicit ticket target is checked against user + tenant ownership
  -> only then may AI Fabric create pending confirmation
  -> confirmation is consumed once for the same owner and conversation
  -> SupportTicketService rechecks ownership inside the transaction
  -> safe action-result projection
```

A missing target remains a clarification case. An explicit inaccessible target is denied before
confirmation, preventing an unauthorized identifier from becoming apparently valid pending work.

## Privacy Flow

```text
app-owned message intake -> SafePIIProcessor -> redacted row -> redacted vector
orchestration input      -> AI Fabric PIIDetectionStep -> provider-safe prompt
generated output         -> AI Fabric/app output processing -> safe response
conversation recording  -> AI Fabric chat-session sanitizer -> safe stored turn
```

The configured policy is `REDACT` in `INPUT_OUTPUT` direction. Raw-original result exposure and
encrypted-original storage are both disabled. If the detector throws, returns the wrong mode,
returns no processed text, exposes the original, or claims detection without changing the payload,
`SafePIIProcessor` fails closed. The API does not substitute a canned success.

## Regression Proof

`SecurityPrivacyIntegrationTest` proves:

- missing and invalid bearer credentials return 401;
- body/header values cannot override verified identity;
- Alex and Riley retrieve only their tenant-visible evidence;
- restricted evidence never enters responses;
- cross-tenant action targets are denied before confirmation;
- message rows, vectors, API projections, provider prompts, outputs, and chat turns contain no raw
  test email or SSN;
- access-policy exceptions deny access and report hook failure.

`SafePIIProcessorTest` separately proves detector exception, unchanged payload, and raw-original
exposure failures. `./mvnw clean verify` executes the matrix without API keys.
