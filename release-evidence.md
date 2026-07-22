# Course Vertical Slice Release Evidence

Candidate checkpoint: `course-0.3.3-06-tested-solution`  
Framework baseline: AI Fabric `0.3.3`  
Required release posture: Java 21, local ONNX embeddings, Lucene vectors, no generation fallback

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
| Build identity | `CourseDeploymentInfoServiceTest` and `CourseApiTest.healthReportsBuildAndProviderPostureWithoutCredentials` | unavailable source metadata is reported as `unknown` | health never exposes credentials |
| Packaged runtime | `scripts/smoke-packaged.sh` | missing/invalid credentials return 401 and every failed assertion exits non-zero | cross-tenant evidence and raw PII are absent from responses and logs |

## Required Keyless Gate

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
./scripts/download-onnx-model.sh
COURSE_SMOKE_USE_EXISTING_JAR=true ./scripts/smoke-packaged.sh
```

The deterministic suite uses explicit test-only providers and runs the real AI Fabric pipeline,
action registry, binders, policy hooks, pending store, JPA chat storage, PII processing, and
application transactions. It does not claim hosted-provider behavior.

The packaged gate launches the executable JAR with the `local` profile, real ONNX embeddings, and
real Lucene storage. It records:

- `target/course-release-evidence/packaged-smoke-summary.json`;
- source-derived deployment health and provider posture;
- readiness, seed, index, search, and redacted-message responses;
- the packaged application log with raw-PII assertions;
- 401 results for missing and invalid credentials.

## Additional Evidence Classes

| Evidence class | Core requirement | Status rule |
| --- | --- | --- |
| Keyed OpenAI | Optional for the keyless Core completion path | Record `PASS`, `FAIL`, or `NOT RUN`; never infer it from local tests |
| Managed vector containers | Not applicable to the local Lucene checkpoint | Required only when a managed adapter becomes part of the release claim |
| Deployed frontend | Not applicable to this backend learner repository | Verify public HTML, hashed asset, backend commit, and browser network response separately when a UI is deployed |

To add OpenAI evidence, run the `openai` profile with a runtime-only key and record the provider,
model, commit, scenarios, failures, and skipped rows. Do not commit credentials or raw prompts that
contain sensitive data.

## Release Decision Rule

The Core checkpoint is ready only when the clean build and packaged smoke both pass for the same
source commit. Optional evidence remains explicitly `NOT RUN` unless it actually ran. Any failed
required row, hidden provider fallback, cross-tenant leak, raw-PII leak, or test-skipping flag makes
the candidate not ready.
