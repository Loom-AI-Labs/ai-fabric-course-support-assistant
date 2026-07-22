# AI Fabric Course Support Assistant

This repository is the standalone learner application for
[Build AI-Enabled Applications with Java and Spring Boot](https://ai-fabric.dev/course).

The course evolves one ordinary Spring Boot support application through semantic search,
evidence-grounded RAG, governed actions, backend-owned conversation memory, tenant security,
privacy, release verification, and production provider routing. Each immutable `course-0.3.3-*`
tag is a lesson checkpoint.

## Current Checkpoint

The fourth Production checkpoint keeps the complete Core slice and adds a real initial backfill from
application-owned JPA rows into AI Fabric's durable indexing queue. The app binds the migration
repository explicitly, exposes an admin-scoped job API, supports filters and bounded batches, and
reports migration progress separately from queue and vector readiness. Deterministic tests cover
pause, resume, cancel, invalid transitions, tenant-safe retrieval, private-field exclusion, and an
idempotent rerun. The packaged gate proves the same flow with real ONNX embeddings and Lucene.

## Requirements

- Java 21
- Maven 3.9+, or the included Maven wrapper

## Run The Final Release Gate

```bash
./mvnw clean verify
./scripts/download-onnx-model.sh
COURSE_SMOKE_USE_EXISTING_JAR=true ./scripts/smoke-packaged.sh
```

The packaged script normally runs `clean package` itself. `COURSE_SMOKE_USE_EXISTING_JAR=true` is
safe here because the immediately preceding `clean verify` already ran all tests and produced the
JAR. The evidence is written to `target/course-release-evidence/`.

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

The `openai` profile uses the Spring AI-backed OpenAI adapter with independently configurable
orchestration and generation models, ONNX for local embeddings, and Lucene for local vector search.
`/api/demo/health` reports both effective purpose routes and models. Provider fallback is disabled. A retrieval failure
returns `RETRIEVAL_FAILED`; a provider or structured-citation failure returns `GENERATION_FAILED`
with HTTP 503 and no canned answer.

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
- `scripts/reset-course.sh` restores the deterministic fixture state.
- `scripts/smoke-packaged.sh` proves the packaged ONNX/Lucene application over HTTP.
- `.github/workflows/verify.yml` runs both gates and retains their reports.

The learner repository never depends on a framework source checkout or unpublished example module.
