# Course Support Assistant Release Evidence

Candidate checkpoint: `course-0.3.3-p08-production-ready`
Framework baseline: AI Fabric `0.3.3`  
Required release posture: Java 21, local ONNX embeddings, Lucene and Docker Qdrant gates, durable
container state, no generation fallback

This record maps every Core claim to executable evidence. CI retains the Surefire reports and the
packaged-smoke artifacts for the exact source commit. A missing or skipped row is not a pass.

## Requirement-To-Proof Matrix

| Capability | Success proof | Failure or denial proof | Forbidden side effect proof |
| --- | --- | --- | --- |
| Index lifecycle | `CourseApiTest.sourceDataAndVectorEvidenceHaveSeparateLifecycle` and `updateAndDeleteReplaceTheEvidenceWithoutLeavingStaleVectors` | empty and cleared index return zero evidence | deleted or replaced text is not returned |
| Evidence-grounded RAG | `CourseApiTest.indexedEvidenceProducesGroundedAnswerWithValidatedCitation` | `ragRuntimeIsPresentAndNoEvidenceSkipsGeneration` and `providerFailureRemainsVisibleAndHasNoCannedAnswer` | generation is not called without evidence; failure has no canned answer |
| Governed actions | `GovernedActionsIntegrationTest` and `AIActionRegistryContractTest` | missing target, rejection, duplicate confirmation, and cross-tenant denial cases | domain status changes only after one authorized confirmation |
| Backend memory | `ConversationMemoryIntegrationTest` | new conversation, cross-owner access, bounded IDs, and transient request cases | the client cannot inject history or another owner's pending state |
| Tenant evidence | `SecurityPrivacyIntegrationTest.tenantScopedSearchSeparatesAlexAndRileyAndExcludesRestrictedEvidence` | forged tenant headers and cross-tenant targets are denied | forbidden hits are rejected before prompt generation |
| Privacy | `SecurityPrivacyIntegrationTest` and `SafePIIProcessorTest` | detector exception, unchanged payload, and exposed original all fail closed | raw email and SSN are absent from API, database, vector, prompt, output, and chat history |
| Migration backfill | `KnowledgeMigrationIntegrationTest` | denied admin access, missing jobs, invalid transitions, and cancellation remain visible | private notes stay out of queue payloads and an idempotent rerun adds no duplicate vector work |
| Live Data Sync | `KnowledgeDataSyncIntegrationTest` | unauthorized/raw access, invalid projection, batch limit, and partial failure are explicit | failed upsert rolls back source; update replaces stale content; delete removes source and vector |
| RAG quality | `RagQualityIntegrationTest`, `CoursePromptOverlayContractTest`, and `SupportAssistantServiceTest` | no-source, insufficient-context, retrieval failure, generation failure, and stale-source cases remain visible | expected/forbidden IDs and source fragments are checked before optional model observation; prompt bodies are not exposed |
| Managed Qdrant | `QdrantProfileConfigurationTest` and `scripts/smoke-qdrant.sh` | an unreachable configured Qdrant returns 503 with no Lucene fallback | Docker proof checks dimensions, payload schema, tenant filters, stable update identity, delete count, and typed diagnostics |
| Build identity | `CourseDeploymentInfoServiceTest` and `CourseApiTest.healthReportsBuildAndProviderPostureWithoutCredentials` | unavailable source metadata is reported as `unknown` | health never exposes credentials |
| Packaged runtime | `scripts/smoke-packaged.sh` | missing/invalid credentials return 401 and every failed assertion exits non-zero | cross-tenant evidence and raw PII are absent from responses and logs |
| Component readiness | `CourseOperationsServiceTest`, `OperationsProfileConfigurationTest`, and `scripts/smoke-release.sh` | a failed required component reports `DOWN` with a stable error code | optional disabled generation does not make a keyless deployment unready or expose a secret |
| Restart persistence | `scripts/smoke-release.sh` | process identity must change and every missing state assertion exits non-zero | source rows, Qdrant evidence, chat turns, migration jobs, and indexing rows survive application restart |
| Retention boundary | `CourseOperationsServiceTest.retentionRemovesOnlyExpiredOperationalState` and `scripts/smoke-release.sh` | maintenance and release-probe endpoints require admin scope and explicit enablement | cleanup removes old operational rows and sessions while preserving application source rows and vector evidence |
| Provider credential posture | `scripts/smoke-release.sh` and `scripts/smoke-openai-optional.sh` | explicitly selected OpenAI without `OPENAI_API_KEY` fails startup with the validator message | keyless success is never presented as OpenAI success; keyed evidence is a separate artifact |

## Required Keyless Gate

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
./scripts/download-onnx-model.sh
COURSE_SMOKE_USE_EXISTING_JAR=true ./scripts/smoke-packaged.sh
COURSE_SMOKE_USE_EXISTING_JAR=true ./scripts/smoke-qdrant.sh
./scripts/smoke-release.sh
./scripts/smoke-openai-optional.sh
```

The deterministic suite uses explicit test-only providers and runs the real AI Fabric pipeline,
action registry, binders, policy hooks, pending store, JPA chat storage, PII processing, and
application transactions. It does not claim hosted-provider behavior.

The packaged gate launches the executable JAR with the `local` profile, real ONNX embeddings, and
real Lucene storage. It records:

- `target/course-release-evidence/packaged-smoke-summary.json`;
- source-derived deployment health and provider posture;
- readiness, migration, live create/update/delete, search, and redacted-message responses;
- the packaged application log with raw-PII assertions;
- 401 results for missing and invalid credentials.

The release-container gate records `release-keyless-summary.json`. It proves the OCI revision and
reported commit match the checked-out source, every required readiness component is independently
`UP`, H2/Qdrant/chat/work state survives an application restart, deterministic quality remains
green, and retention removes only eligible operational state. The OpenAI profile must fail startup
when selected without a key. Optional live evidence is retained separately as
`openai-keyed-summary.json`, including `NOT_RUN` when no key was supplied.

## Additional Evidence Classes

| Evidence class | Core requirement | Status rule |
| --- | --- | --- |
| Keyed OpenAI | Optional for learner completion and separate from the required release gate | Retain `openai-keyed-summary.json` as `PASS`, `FAIL`, or `NOT_RUN`; never infer it from local tests |
| Managed vector containers | Required local Qdrant and release-container gates | Retain `qdrant-smoke-summary.json` and `release-keyless-summary.json`; Qdrant Cloud remains optional and separately labelled |
| Deployed frontend | Not applicable to this backend learner repository | Verify public HTML, hashed asset, backend commit, and browser network response separately when a UI is deployed |

To add OpenAI evidence, run the `openai` profile with a runtime-only key and record the provider,
model, commit, scenarios, failures, and skipped rows. Do not commit credentials or raw prompts that
contain sensitive data.

## Release Decision Rule

The candidate checkpoint is ready only when the clean build, packaged Lucene smoke, local Qdrant
smoke, and source-labelled release-container smoke pass for the same source commit. Optional
evidence remains explicitly `NOT_RUN` unless it actually ran. Any failed required row, hidden
provider fallback, cross-tenant leak, raw-PII leak, ownership-crossing cleanup, incorrect build
identity, lost restart state, or test-skipping flag makes the candidate not ready.
