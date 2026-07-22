# AI Fabric Course Support Assistant

This repository is the standalone learner application for
[Build AI-Enabled Applications with Java and Spring Boot](https://ai-fabric.dev/course).

The course evolves one ordinary Spring Boot support application through semantic search,
evidence-grounded RAG, governed actions, backend-owned conversation memory, tenant security,
privacy, and release verification. Each immutable `course-0.3.3-*` tag is a lesson checkpoint.

## Current Checkpoint

This checkpoint adds backend-owned conversation memory while preserving semantic search,
evidence-grounded RAG, and governed actions. AI Fabric now records sanitized turns in JPA-backed
chat sessions, supplies bounded role-aware history to the LLM, persists pending confirmations, and
reuses short-lived targets. The browser sends only the new message, stable conversation ID, and
optional current attachments.

## Requirements

- Java 21
- Maven 3.9+, or the included Maven wrapper

## Run Backend Conversation Memory

```bash
./mvnw clean verify
./scripts/download-onnx-model.sh
OPENAI_API_KEY=<set-locally> ./mvnw spring-boot:run -Dspring-boot.run.profiles=openai
```

Then inspect the baseline:

```bash
curl -s -X POST http://localhost:8080/api/demo/reset
curl -s -X POST http://localhost:8080/api/demo/seed
curl -s -X POST http://localhost:8080/api/assistant/query \
  -H 'Content-Type: application/json' \
  -d '{"message":"What should I do if failed sign-ins locked me out?"}'
curl -s -X POST http://localhost:8080/api/demo/index
curl -s http://localhost:8080/api/demo/readiness
curl -s -X POST http://localhost:8080/api/assistant/query \
  -H 'Content-Type: application/json' \
  -d '{"message":"What should I do if failed sign-ins locked me out?"}'
curl -s http://localhost:8080/api/assistant/actions
curl -s -X POST http://localhost:8080/api/assistant/orchestrate \
  -H 'Content-Type: application/json' \
  -d '{"message":"Why is ticket T-1001 unresolved?","conversationId":"course-memory"}'
curl -s -X POST http://localhost:8080/api/assistant/orchestrate \
  -H 'Content-Type: application/json' \
  -d '{"message":"Escalate it.","conversationId":"course-memory"}'
curl -s http://localhost:8080/api/assistant/conversations/course-memory
curl -s -X POST http://localhost:8080/api/assistant/orchestrate \
  -H 'Content-Type: application/json' \
  -d '{"message":"Yes.","conversationId":"course-memory"}'
```

The RAG flow remains available at `/api/assistant/query`. The governed and conversational flow uses
`/api/assistant/orchestrate`; copyable three-turn follow-up, reopen, duplicate-confirmation, and
new-conversation isolation requests are in `requests/04-backend-memory.http`.

The first RAG request returns `NO_EVIDENCE` and does not call the LLM because ordinary
database rows have not been indexed. The second returns `ANSWERED` with
`policy-account-lockout-01` as validated evidence. `internalNotes` is deliberately not annotated
and never enters vector content, the generation prompt, or the public response.

The `openai` profile uses the Spring AI-backed OpenAI adapter for generation, ONNX for local
embeddings, and Lucene for local vector search. Provider fallback is disabled. A retrieval failure
returns `RETRIEVAL_FAILED`; a provider or structured-citation failure returns `GENERATION_FAILED`
with HTTP 503 and no canned answer.

`./mvnw clean verify` needs no API key. Tests use explicitly labelled, test-only embedding and
generation providers without pretending to be live AI. They inject valid structured intents, then
exercise the real AI Fabric pipeline, JPA chat storage, role-aware history, session-backed pending
store, registry, annotations, argument binder, authorization hooks, and domain transactions.

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

Do not move an existing checkpoint tag. Course corrections receive a new course patch version.

## Ownership Boundary

This application always owns domain records, authenticated identity, authorization, mutations, and
public response contracts. AI Fabric supplies AI-facing projection, retrieval, orchestration,
confirmation state, and provider integration as those capabilities are introduced.

## Useful Files

- `requests/` contains copyable HTTP scenarios.
- `docs/conversation-memory-contract.md` traces memory, ownership, bounds, and transient requests.
- `src/main/resources/prompts/` contains the narrow, tested course-support prompt overlay.
- `scripts/reset-course.sh` restores the deterministic fixture state.
- `.github/workflows/verify.yml` proves the repository builds independently from Maven Central.

The learner repository never depends on a framework source checkout or unpublished example module.
