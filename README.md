# AI Fabric Course Support Assistant

This repository is the standalone learner application for
[Build AI-Enabled Applications with Java and Spring Boot](https://ai-fabric.dev/course).

The course evolves one ordinary Spring Boot support application through semantic search,
evidence-grounded RAG, governed actions, backend-owned conversation memory, tenant security,
privacy, and release verification. Each immutable `course-0.3.3-*` tag is a lesson checkpoint.

## Current Checkpoint

This checkpoint adds the first real AI Fabric capability: support articles are projected through
`@AICapable`, `@AISearchable`, and `@AIContext`, embedded locally with ONNX, and stored in Lucene.
The source database and vector evidence remain separate lifecycles so learners can observe the
difference between owning records and making approved fields retrievable.

## Requirements

- Java 21
- Maven 3.9+, or the included Maven wrapper

## Run Semantic Search

```bash
./mvnw clean verify
./scripts/download-onnx-model.sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Then inspect the baseline:

```bash
curl -s -X POST http://localhost:8080/api/demo/reset
curl -s -X POST http://localhost:8080/api/demo/seed
curl -s "http://localhost:8080/api/knowledge/search?q=I+cannot+sign+in+after+too+many+attempts"
curl -s -X POST http://localhost:8080/api/demo/index
curl -s http://localhost:8080/api/demo/readiness
curl -s "http://localhost:8080/api/knowledge/search?q=I+cannot+sign+in+after+too+many+attempts"
```

The first search returns no evidence because ordinary database rows have not been indexed. The
second returns `article-account-lockout` with public metadata. `internalNotes` is deliberately not
annotated and never enters the vector content or public evidence response.

Tests use an explicitly test-only semantic fixture and the in-memory vector adapter. The `local`
profile uses the real ONNX provider and Lucene; there is no runtime fixture or hidden fallback.

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
