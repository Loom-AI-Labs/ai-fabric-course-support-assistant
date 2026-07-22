# AI Fabric Course Support Assistant

This repository is the standalone learner application for
[Build AI-Enabled Applications with Java and Spring Boot](https://ai-fabric.dev/course).

The course evolves one ordinary Spring Boot support application through semantic search,
evidence-grounded RAG, governed actions, backend-owned conversation memory, tenant security,
privacy, and release verification. Each immutable `course-0.3.3-*` tag is a lesson checkpoint.

## Current Checkpoint

The starter is intentionally not AI-enabled. It owns support articles, policies, accounts, and
tickets, and exposes stable seed, reset, and readiness APIs. AI endpoints return HTTP `501` until a
later checkpoint implements them.

## Requirements

- Java 21
- Maven 3.9+, or the included Maven wrapper

## Run The Starter

```bash
./mvnw clean verify
./mvnw spring-boot:run
```

Then inspect the baseline:

```bash
curl -s -X POST http://localhost:8080/api/demo/reset
curl -s -X POST http://localhost:8080/api/demo/seed
curl -s http://localhost:8080/api/demo/readiness
curl -i "http://localhost:8080/api/knowledge/search?q=How+can+I+recover+access+to+my+account"
```

The final request returns `501 Not Implemented` at the starter checkpoint. That is the expected
baseline, not a hidden fallback.

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
