# AI Fabric Course Support Assistant

This repository is the standalone learner application for
[Build AI-Enabled Applications with Java and Spring Boot](https://ai-fabric.dev/course).

The course evolves one ordinary Spring Boot support application through semantic search,
evidence-grounded RAG, governed actions, backend-owned conversation memory, tenant security,
privacy, release verification, and production provider routing. Each immutable `course-0.3.3-*`
tag is a lesson checkpoint.

## Current Checkpoint

The seventh Production checkpoint preserves the Support Knowledge Assistant contract while adding a
Docker Qdrant profile beside Lucene. Typed readiness reports provider capabilities and transport;
the Docker gate proves 384-dimensional collections, required payload indexing, tenant filters,
golden quality cases, stable Data Sync upsert/delete, durability posture, and visible failure when
Qdrant is unreachable. ONNX remains the embedding provider and no cloud key is required.

## Requirements

- Java 21
- Maven 3.9+, or the included Maven wrapper

## Run The Final Release Gate

```bash
./mvnw clean verify
./scripts/download-onnx-model.sh
COURSE_SMOKE_USE_EXISTING_JAR=true ./scripts/smoke-packaged.sh
COURSE_SMOKE_USE_EXISTING_JAR=true ./scripts/smoke-qdrant.sh
```

The packaged script normally runs `clean package` itself. `COURSE_SMOKE_USE_EXISTING_JAR=true` is
safe here because the immediately preceding `clean verify` already ran all tests and produced the
JAR. The evidence is written to `target/course-release-evidence/`.

The Qdrant script starts and removes a pinned local container. For manual exploration with durable
local storage, use `docker compose -f compose.qdrant.yml up -d` and run the `qdrant` Spring profile.

Inspect the packaged result:

```bash
jq . target/course-release-evidence/packaged-smoke-summary.json
```

For an optional live OpenAI exercise after the keyless gate:

```bash
OPENAI_ENABLED=true \
OPENAI_API_KEY=<set-locally> \
AI_ORCHESTRATION_MODEL=gpt-4o-mini \
AI_GENERATION_MODEL=gpt-4o-mini \
./mvnw spring-boot:run -Dspring-boot.run.profiles=openai
```

Then inspect the application manually:

```bash
export COURSE_TOKEN=course-alex-local-token
curl -s -X POST http://localhost:8080/api/demo/reset
curl -s -X POST http://localhost:8080/api/demo/seed
curl -s -X POST http://localhost:8080/api/assistant/query \
  -H "Authorization: Bearer $COURSE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"message":"What should I do if failed sign-ins locked me out?"}'
curl -s -X POST http://localhost:8080/api/admin/migrations/knowledge-articles \
  -H "Authorization: Bearer $COURSE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"batchSize":3,"rateLimit":0,"reindexExisting":false}'
curl -s -X POST http://localhost:8080/api/knowledge/articles \
  -H "Authorization: Bearer $COURSE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"id":"article-live-sync","title":"Enroll a passkey","body":"Register a passkey in Security Settings before removing the password.","category":"authentication"}'
curl -s -X PUT http://localhost:8080/api/knowledge/articles/article-live-sync \
  -H "Authorization: Bearer $COURSE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Replace a password with a security key","body":"Register the hardware security key, verify it, then revoke the previous login method."}'
curl -s -X DELETE http://localhost:8080/api/knowledge/articles/article-live-sync \
  -H "Authorization: Bearer $COURSE_TOKEN"
curl -s http://localhost:8080/api/quality/rag/golden \
  -H "Authorization: Bearer $COURSE_TOKEN"
curl -s http://localhost:8080/api/quality/prompts \
  -H "Authorization: Bearer $COURSE_TOKEN"
curl -s http://localhost:8080/api/demo/readiness
curl -s http://localhost:8080/api/demo/health
curl -s -X POST http://localhost:8080/api/assistant/query \
  -H "Authorization: Bearer $COURSE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"message":"What should I do if failed sign-ins locked me out?"}'
curl -s http://localhost:8080/api/assistant/actions \
  -H "Authorization: Bearer $COURSE_TOKEN"
curl -s -X POST http://localhost:8080/api/assistant/orchestrate \
  -H "Authorization: Bearer $COURSE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Why is ticket T-1001 unresolved?","conversationId":"course-memory"}'
curl -s -X POST http://localhost:8080/api/assistant/orchestrate \
  -H "Authorization: Bearer $COURSE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Escalate it.","conversationId":"course-memory"}'
curl -s http://localhost:8080/api/assistant/conversations/course-memory \
  -H "Authorization: Bearer $COURSE_TOKEN"
curl -s -X POST http://localhost:8080/api/assistant/orchestrate \
  -H "Authorization: Bearer $COURSE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Yes.","conversationId":"course-memory"}'
curl -s -X POST http://localhost:8080/api/support/messages \
  -H "Authorization: Bearer $COURSE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"content":"Contact alex.private@example.com about SSN 123-45-6789"}'
```

The RAG flow remains available at `/api/assistant/query`. The governed and conversational flow uses
`/api/assistant/orchestrate`; copyable identity, tenant-isolation, pre-confirmation denial, PII, and
release scenarios are in `requests/05-tenant-security-privacy.http` and
`requests/06-test-and-ship.http`.

The default local credentials exist only to make the course reproducible. Alex belongs to
`tenant-blue`; Riley belongs to `tenant-red`. Set `COURSE_ALEX_TOKEN` and `COURSE_RILEY_TOKEN` to
replace them outside a learner workstation. A request body or ad hoc tenant header cannot select
identity. In a real application, replace `CourseBearerAuthenticationFilter` with your verified
OAuth2/JWT or gateway principal adapter while preserving `CoursePrincipalProvider` as the
application boundary.

The first RAG request returns `NO_EVIDENCE` and does not call the LLM because ordinary
database rows have not been indexed. Start the migration, wait for the job to complete, then wait
for `indexingCaughtUp=true`; job completion alone means rows were scanned and indexing work was
queued. The second RAG request returns `ANSWERED` with validated evidence. `internalNotes` is
excluded from the durable queue payload, vector content, generation prompt, and public response.

AI Fabric `0.3.3` exposes source rows processed and failed, but not an exact per-job skipped count.
The course API says so explicitly instead of deriving a misleading number. Idempotency is proved by
stable vector identity and an unchanged queue-entry count after a rerun with
`reindexExisting=false`.

After that initial backfill, application-owned create/update/delete endpoints use
`ai-fabric-data-sync`. The browser cannot supply `tenantId`, verified auth context, or vector space;
the backend projects those values from `CoursePrincipal` and the persisted article. The low-level
`/api/internal/ai-data-sync/**` route is denied by Spring Security. The optional platform bypass
remains false. A failed upsert rolls back the source transaction, while reconciliation reports
per-operation partial failures so an operator can repair derived evidence without changing source
truth.

The `openai` profile uses the Spring AI-backed OpenAI adapter with independently configurable
orchestration and generation models, ONNX for local embeddings, and Lucene for local vector search.
`/api/demo/health` reports both effective purpose routes and models. Provider fallback is disabled. A retrieval failure
returns `RETRIEVAL_FAILED`; a provider or structured-citation failure returns `GENERATION_FAILED`
with HTTP 503 and no canned answer.

The RAG scorecard does not grade model wording. It gates the deterministic boundary first: source
IDs, tenant exclusions, current source fragments, no-source behavior, and prompt resource shape.
An optional OpenAI run can be retained as model observation, but it cannot turn a failed evidence
case into a pass. A scorecard result uses `passed=false` plus failure codes such as
`EXPECTED_EVIDENCE_MISSING` or `STALE_CONTENT_RETURNED`; HTTP success only means the evaluation ran.

`./mvnw clean verify` needs no API key. Tests use explicitly labelled, test-only embedding,
orchestration, and generation providers without pretending to be live AI. They inject valid structured intents, then
exercise the real AI Fabric pipeline, JPA chat storage, role-aware history, session-backed pending
store, registry, annotations, argument binder, authorization hooks, domain transactions, exact
metadata filtering, post-hit verification, and PII processing.

`release-evidence.md` maps indexing, RAG, actions, memory, tenant security, privacy, build identity,
and packaged runtime to success, failure, and forbidden-side-effect proof. Optional OpenAI,
managed-vector, and deployed-frontend rows remain explicitly separate; a conditional or unexecuted
row is never presented as a pass.

`@ActionAllowed` validates an explicit ticket target before AI Fabric creates confirmation state.
The domain transaction validates ownership again before mutation. Retrieval follows the same
defense-in-depth shape: exact `tenantId`, `visibleToUser`, and `status` filters narrow the provider
query, then application code checks every returned hit before any context reaches the LLM. Public
projections omit tenant IDs, raw provider metadata, internal notes, customer IDs, and action
context.

`ai-fabric-pii` runs in `REDACT` plus `INPUT_OUTPUT` mode with raw-original exposure and encrypted
original storage disabled. `SafePIIProcessor` protects app-owned intake paths outside orchestration;
AI Fabric protects orchestration input, output, and conversation recording. Failure to prove either
access or redaction is returned as an explicit denial or service failure, never a permissive
fallback.

The app configures an eight-turn, 4,000-character history window, three-turn target reuse, and a
four-action pending stack. `CourseConversationAuthorization` checks known owners and bounded IDs;
AI Fabric separately verifies stored ownership. `NEVER_PERSIST` is available only through trusted
backend code and skips both enrichment and recording.

The application prompt overlay `v1-course-support` precedes the curated `v1-support` pack. Its
only domain delta prevents a bare acknowledgement after a completed write from reconstructing that
write from history. The overlay covers the compound fast path and multi-step fallback; all omitted
prompt slots continue through `v1-support` and then the complete `v1` base bundle. The classifier
contract is tested, and no text-matching application router replaces LLM intent resolution.

The `local` profile remains useful for retrieval-only exploration. It uses the real ONNX provider
and Lucene with generation disabled. There is no runtime fixture or hidden fallback.

AI Fabric `0.3.3` discovers an optional `com.huggingface.tokenizers` hook but does not package its
implementation. The two small classes under that package adapt the hook to DJL's real Hugging Face
tokenizer. They contain no ranking logic and do not replace AI Fabric's ONNX inference.

## Course Checkpoints

| Tag | Course state |
| --- | --- |
| `course-0.3.3-00-starter` | Ordinary Spring Boot support application |
| `course-0.3.3-01-first-search` | ONNX and Lucene semantic evidence lifecycle |
| `course-0.3.3-02-rag` | Evidence-grounded generation |
| `course-0.3.3-03-actions` | Governed read and write actions |
| `course-0.3.3-04-memory` | Backend-owned conversation memory |
| `course-0.3.3-05-security` | Tenant and privacy boundaries |
| `course-0.3.3-06-tested-solution` | Complete release evidence |
| `course-0.3.3-p01-provider-routing` | Purpose-specific provider and model routing |
| `course-0.3.3-p02-modes-positions` | Application position mapping and server-owned orchestration modes |
| `course-0.3.3-p03-prompt-overlays` | Application prompt overlays, curated fallback, and safe diagnostics |
| `course-0.3.3-p04-migration-backfill` | Admin-scoped migration, durable indexing, readiness, and idempotent backfill |
| `course-0.3.3-p05-live-data-sync` | Trusted create/update/delete synchronization, stable identity, and visible batch failure |
| `course-0.3.3-p06-rag-quality` | Golden evidence IDs, tenant exclusions, freshness, no-source, and prompt regression gates |
| `course-0.3.3-p07-qdrant` | Docker Qdrant provider parity, diagnostics, lifecycle, filtering, and visible outage proof |

Do not move an existing checkpoint tag. Course corrections receive a new course patch version.

## Ownership Boundary

This application always owns domain records, authenticated identity, authorization, mutations, and
public response contracts. AI Fabric supplies AI-facing projection, retrieval, orchestration,
confirmation state, and provider integration as those capabilities are introduced.

## Useful Files

- `requests/` contains copyable HTTP scenarios.
- `docs/conversation-memory-contract.md` traces memory, ownership, bounds, and transient requests.
- `docs/security-privacy-contract.md` traces verified identity, tenant evidence, actions, and PII.
- `release-evidence.md` maps each release claim to executable proof.
- `src/main/resources/prompts/` contains the narrow, tested course-support prompt overlay.
- `requests/production-04-migration-backfill.http` exercises the migration lifecycle.
- `requests/production-05-live-data-sync.http` exercises the trusted incremental sync lifecycle.
- `requests/production-06-rag-quality.http` exercises deterministic RAG and prompt quality gates.
- `requests/production-07-qdrant.http` exercises the same contract against local Qdrant.
- `scripts/reset-course.sh` restores the deterministic fixture state.
- `scripts/smoke-packaged.sh` proves the packaged ONNX/Lucene application over HTTP.
- `scripts/smoke-qdrant.sh` proves provider parity and failure behavior against Docker Qdrant.
- `.github/workflows/verify.yml` runs both gates and retains their reports.

The learner repository never depends on a framework source checkout or unpublished example module.
