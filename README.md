# AI Fabric Course Support Assistant

This repository is the standalone learner application for
[Build AI-Enabled Applications with Java and Spring Boot](https://ai-fabric.dev/course).

The course evolves one ordinary Spring Boot support application through semantic search,
evidence-grounded RAG, governed actions, backend-owned conversation memory, tenant security,
privacy, and release verification. Each immutable `course-0.3.3-*` tag is a lesson checkpoint.

## Current Checkpoint

This checkpoint adds governed read and write actions while preserving the evidence-grounded RAG
endpoint from the previous checkpoint. AI Fabric discovers an explicit action catalog, validates
typed model parameters, denies anonymous writes, and owns pending confirmation state. The support
application supplies trusted identity and tenant context, authorizes the current customer again in
its transaction, performs the mutation, and returns a concise structured result.

## Requirements

- Java 21
- Maven 3.9+, or the included Maven wrapper

## Run Governed Actions

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
  -d '{"message":"What is the status of T-1001?","conversationId":"course-actions"}'
curl -s -X POST http://localhost:8080/api/assistant/orchestrate \
  -H 'Content-Type: application/json' \
  -d '{"message":"Create a high-priority ticket titled Account locked and explain that recovery emails never arrive.","conversationId":"course-actions"}'
curl -s -X POST http://localhost:8080/api/assistant/orchestrate \
  -H 'Content-Type: application/json' \
  -d '{"message":"yes","conversationId":"course-actions"}'
```

The RAG flow remains available at `/api/assistant/query`. The governed action flow uses
`/api/assistant/orchestrate`; copyable read, clarification, rejection, confirmation, and duplicate
confirmation requests are in `requests/03-governed-actions.http`.

The first RAG request returns `NO_EVIDENCE` and does not call the LLM because ordinary
database rows have not been indexed. The second returns `ANSWERED` with
`policy-account-lockout-01` as validated evidence. `internalNotes` is deliberately not annotated
and never enters vector content, the generation prompt, or the public response.

The `openai` profile uses the Spring AI-backed OpenAI adapter for generation, ONNX for local
embeddings, and Lucene for local vector search. Provider fallback is disabled. A retrieval failure
returns `RETRIEVAL_FAILED`; a provider or structured-citation failure returns `GENERATION_FAILED`
with HTTP 503 and no canned answer.

`./mvnw clean verify` needs no API key. Tests use explicitly labelled, test-only embedding and
generation providers and prove the same public contracts without pretending to be live AI. Action
tests inject valid structured intents, then execute the real AI Fabric pipeline, registry, pending
store, annotations, argument binder, authorization hooks, and JPA transaction.

This checkpoint uses the core in-memory pending action store deliberately. It survives turns in one
running process but not a restart or horizontal scale-out. The next checkpoint adds
`ai-fabric-chat-session` and moves conversation and confirmation state into backend-owned storage.

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
- `scripts/reset-course.sh` restores the deterministic fixture state.
- `.github/workflows/verify.yml` proves the repository builds independently from Maven Central.

The learner repository never depends on a framework source checkout or unpublished example module.
