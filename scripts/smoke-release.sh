#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${COURSE_RELEASE_SMOKE_PORT:-18084}"
QDRANT_PORT="${COURSE_RELEASE_QDRANT_PORT:-16335}"
BASE_URL="http://127.0.0.1:${PORT}"
REPORT_DIR="${COURSE_SMOKE_REPORT_DIR:-${ROOT_DIR}/target/course-release-evidence}"
RUN_ID="$$"
IMAGE="ai-fabric-course-support-assistant:prod08-${RUN_ID}"
NETWORK="course-prod08-${RUN_ID}"
APP_NAME="course-prod08-app-${RUN_ID}"
QDRANT_NAME="course-prod08-qdrant-${RUN_ID}"
MISSING_KEY_NAME="course-prod08-missing-key-${RUN_ID}"
DB_VOLUME="course-prod08-db-${RUN_ID}"
QDRANT_VOLUME="course-prod08-qdrant-${RUN_ID}"
APP_LOG="${REPORT_DIR}/release-app.log"
MISSING_KEY_LOG="${REPORT_DIR}/release-missing-key.log"
SOURCE_COMMIT=""
SOURCE_BRANCH=""
BUILD_TIME=""

cleanup() {
  docker rm --force "${APP_NAME}" "${QDRANT_NAME}" "${MISSING_KEY_NAME}" >/dev/null 2>&1 || true
  docker network rm "${NETWORK}" >/dev/null 2>&1 || true
  if [[ "${COURSE_RELEASE_KEEP_DOCKER_STATE:-false}" != "true" ]]; then
    docker volume rm "${DB_VOLUME}" "${QDRANT_VOLUME}" >/dev/null 2>&1 || true
    docker image rm "${IMAGE}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

fail() {
  printf 'Release smoke failed: %s\n' "$1" >&2
  if [[ -f "${APP_LOG}" ]]; then
    tail -n 120 "${APP_LOG}" >&2
  fi
  exit 1
}

for command in docker curl jq git date grep; do
  command -v "${command}" >/dev/null 2>&1 || fail "required command '${command}' is unavailable"
done

cd "${ROOT_DIR}"
mkdir -p "${REPORT_DIR}"
SOURCE_COMMIT="$(git rev-parse HEAD)"
SOURCE_BRANCH="$(git branch --show-current)"
BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

docker build \
  --build-arg "SOURCE_COMMIT=${SOURCE_COMMIT}" \
  --build-arg "SOURCE_BRANCH=${SOURCE_BRANCH:-detached}" \
  --build-arg "BUILD_TIME=${BUILD_TIME}" \
  --tag "${IMAGE}" \
  . >"${REPORT_DIR}/release-image-build.log"

docker image inspect "${IMAGE}" >"${REPORT_DIR}/release-image-inspect.json"
jq -e --arg commit "${SOURCE_COMMIT}" \
  '.[0].Config.Labels["org.opencontainers.image.revision"] == $commit' \
  "${REPORT_DIR}/release-image-inspect.json" >/dev/null || fail "image revision label does not match source commit"

docker network create "${NETWORK}" >/dev/null
docker volume create "${DB_VOLUME}" >/dev/null
docker volume create "${QDRANT_VOLUME}" >/dev/null
docker run --detach \
  --name "${QDRANT_NAME}" \
  --network "${NETWORK}" \
  --publish "${QDRANT_PORT}:6333" \
  --volume "${QDRANT_VOLUME}:/qdrant/storage" \
  qdrant/qdrant:v1.16.1 >/dev/null

for _ in $(seq 1 90); do
  curl --fail --silent "http://127.0.0.1:${QDRANT_PORT}/readyz" >/dev/null 2>&1 && break
  sleep 1
done
curl --fail --silent "http://127.0.0.1:${QDRANT_PORT}/readyz" >/dev/null \
  || fail "Qdrant did not become ready"

start_app() {
  docker rm --force "${APP_NAME}" >/dev/null 2>&1 || true
  docker run --detach \
    --name "${APP_NAME}" \
    --network "${NETWORK}" \
    --publish "${PORT}:8080" \
    --volume "${DB_VOLUME}:/app/data" \
    --env SPRING_PROFILES_ACTIVE=operations,qdrant \
    --env COURSE_RELEASE_RUNTIME_MODE=production-keyless \
    --env COURSE_DB_PATH=/app/data/database/course-support \
    --env COURSE_OPERATIONS_MAINTENANCE_ENABLED=true \
    --env COURSE_RELEASE_PROBES_ENABLED=true \
    --env COURSE_COMPLETED_RECORD_RETENTION=PT0S \
    --env COURSE_CONVERSATION_RETENTION=PT0S \
    --env "AI_PROVIDERS_QDRANT_HOST=${QDRANT_NAME}" \
    --env AI_PROVIDERS_QDRANT_PORT=6333 \
    --env AI_PROVIDERS_QDRANT_GRPC_PORT=6334 \
    --env AI_PROVIDERS_QDRANT_COLLECTION_PREFIX=course_prod08__ \
    --env AI_PROVIDERS_QDRANT_PREFER_GRPC=false \
    "${IMAGE}" >/dev/null

  for _ in $(seq 1 120); do
    if curl --fail --silent "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
      docker logs "${APP_NAME}" >"${APP_LOG}" 2>&1 || true
      return
    fi
    running="$(docker inspect --format '{{.State.Running}}' "${APP_NAME}" 2>/dev/null || true)"
    [[ "${running}" == "true" ]] || {
      docker logs "${APP_NAME}" >"${APP_LOG}" 2>&1 || true
      fail "application exited before readiness"
    }
    sleep 1
  done
  docker logs "${APP_NAME}" >"${APP_LOG}" 2>&1 || true
  fail "application did not become ready"
}

start_app
curl --fail --silent "${BASE_URL}/api/demo/health" >"${REPORT_DIR}/release-health-before.json"
curl --fail --silent "${BASE_URL}/api/demo/operations/readiness" \
  >"${REPORT_DIR}/release-readiness-empty.json"

jq -e --arg commit "${SOURCE_COMMIT}" \
  '.status == "UP" and .checkpoint == "course-0.3.3-p08-production-ready"
    and .commit == $commit and .provider.mode == "production-keyless"
    and .provider.generationEnabled == false and .provider.fallbackEnabled == false' \
  "${REPORT_DIR}/release-health-before.json" >/dev/null || fail "deployment metadata is not source-derived"
jq -e '.status == "READY"
  and .components.build.status == "UP"
  and .components.database.status == "UP"
  and .components.vector.status == "UP"
  and .components.sessions.status == "UP"
  and .components.indexing.status == "UP"
  and .components.migration.status == "UP"
  and .components.generationProvider.status == "DISABLED"
  and .components.generationProvider.required == false
  and .components.generationProvider.details.credentialConfigured == false' \
  "${REPORT_DIR}/release-readiness-empty.json" >/dev/null || fail "component readiness is incomplete"

curl --fail --silent -X POST "${BASE_URL}/api/demo/seed" >"${REPORT_DIR}/release-seed.json"
curl --fail --silent -X POST "${BASE_URL}/api/admin/migrations/knowledge-articles" \
  -H 'Authorization: Bearer course-alex-local-token' \
  -H 'Content-Type: application/json' \
  -d '{"batchSize":3,"rateLimit":0,"reindexExisting":false}' \
  >"${REPORT_DIR}/release-migration-start.json"
migration_job_id="$(jq -r '.jobId // empty' "${REPORT_DIR}/release-migration-start.json")"
[[ -n "${migration_job_id}" ]] || fail "migration did not return a job ID"

for _ in $(seq 1 120); do
  curl --fail --silent \
    "${BASE_URL}/api/admin/migrations/knowledge-articles/${migration_job_id}" \
    -H 'Authorization: Bearer course-alex-local-token' \
    >"${REPORT_DIR}/release-migration.json"
  if jq -e '.status == "COMPLETED" and .indexingCaughtUp == true and .currentIndexedVectors == 9' \
      "${REPORT_DIR}/release-migration.json" >/dev/null; then
    break
  fi
  jq -e '.status != "FAILED" and .status != "CANCELLED"' \
    "${REPORT_DIR}/release-migration.json" >/dev/null || fail "migration failed"
  sleep 0.2
done
jq -e '.status == "COMPLETED" and .indexingCaughtUp == true and .currentIndexedVectors == 9' \
  "${REPORT_DIR}/release-migration.json" >/dev/null || fail "migration/indexing did not catch up"

curl --fail --silent -X POST "${BASE_URL}/api/admin/operations/release-probes" \
  -H 'Authorization: Bearer course-alex-local-token' \
  >"${REPORT_DIR}/release-probe.json"
probe_conversation_id="$(jq -r '.conversationId // empty' "${REPORT_DIR}/release-probe.json")"
[[ -n "${probe_conversation_id}" ]] || fail "release probe did not persist a conversation"
jq -e '.modelInvoked == false and .storedTurns == 1' \
  "${REPORT_DIR}/release-probe.json" >/dev/null || fail "release probe misrepresented model execution"

curl --fail --silent "${BASE_URL}/api/quality/rag/golden" \
  -H 'Authorization: Bearer course-alex-local-token' \
  >"${REPORT_DIR}/release-quality-before.json"
curl --fail --silent "${BASE_URL}/api/demo/operations/readiness" \
  >"${REPORT_DIR}/release-readiness-before-restart.json"
jq -e '.passed == true and .failedCases == 0' \
  "${REPORT_DIR}/release-quality-before.json" >/dev/null || fail "quality gate failed before restart"
jq -e '.status == "READY"
  and .components.database.details.sourceRecords.articles == 9
  and .components.vector.details.knowledgeVectors == 9
  and .components.sessions.details.courseSessions == 1
  and .components.indexing.details.completed >= 9
  and .components.migration.details.total >= 1
  and .components.migration.details.active == 0' \
  "${REPORT_DIR}/release-readiness-before-restart.json" >/dev/null \
  || fail "durable state was not ready before restart"

first_started_at="$(jq -r '.startedAt' "${REPORT_DIR}/release-health-before.json")"
docker stop --time 20 "${APP_NAME}" >/dev/null
docker rm "${APP_NAME}" >/dev/null
start_app

curl --fail --silent "${BASE_URL}/api/demo/health" >"${REPORT_DIR}/release-health-after.json"
curl --fail --silent "${BASE_URL}/api/demo/operations/readiness" \
  >"${REPORT_DIR}/release-readiness-after-restart.json"
curl --fail --silent "${BASE_URL}/api/assistant/conversations/${probe_conversation_id}" \
  -H 'Authorization: Bearer course-alex-local-token' \
  >"${REPORT_DIR}/release-conversation-after-restart.json"
curl --fail --silent "${BASE_URL}/api/quality/rag/golden" \
  -H 'Authorization: Bearer course-alex-local-token' \
  >"${REPORT_DIR}/release-quality-after.json"

jq -e --arg first "${first_started_at}" '.startedAt != $first' \
  "${REPORT_DIR}/release-health-after.json" >/dev/null || fail "restart did not produce a new process identity"
jq -e '.status == "READY"
  and .components.database.details.sourceRecords.articles == 9
  and .components.vector.details.knowledgeVectors == 9
  and .components.sessions.details.courseSessions == 1
  and .components.indexing.details.completed >= 9
  and .components.migration.details.total >= 1' \
  "${REPORT_DIR}/release-readiness-after-restart.json" >/dev/null \
  || fail "database, vector, session, or work state did not survive restart"
jq -e '(.turns | length) == 1
  and .turns[0].assistantMessage == "Release persistence probe recorded. No language model was invoked."' \
  "${REPORT_DIR}/release-conversation-after-restart.json" >/dev/null \
  || fail "backend conversation state did not survive restart"
jq -e '.passed == true and .failedCases == 0' \
  "${REPORT_DIR}/release-quality-after.json" >/dev/null || fail "quality gate failed after restart"

curl --fail --silent -X POST "${BASE_URL}/api/admin/operations/retention/cleanup" \
  -H 'Authorization: Bearer course-alex-local-token' \
  >"${REPORT_DIR}/release-cleanup.json"
curl --fail --silent "${BASE_URL}/api/demo/operations/readiness" \
  >"${REPORT_DIR}/release-readiness-after-cleanup.json"

jq -e '.migrationJobs.removed >= 1
  and .indexingEntries.removed >= 9
  and .courseSessions.removed >= 1
  and .sourceRecordsPreserved == true
  and .vectorsPreserved == true
  and .sourceAfter.articles == 9
  and .sourceAfter.policies == 2
  and .sourceAfter.accounts == 2
  and .sourceAfter.tickets == 2
  and .vectorsAfter == 9' \
  "${REPORT_DIR}/release-cleanup.json" >/dev/null || fail "retention crossed an ownership boundary"
jq -e '.status == "READY"
  and .components.database.details.sourceRecords.articles == 9
  and .components.vector.details.knowledgeVectors == 9
  and .components.sessions.details.courseSessions == 0
  and .components.indexing.details.total == 0
  and .components.migration.details.total == 0' \
  "${REPORT_DIR}/release-readiness-after-cleanup.json" >/dev/null \
  || fail "post-cleanup readiness is inconsistent"

set +e
docker run --name "${MISSING_KEY_NAME}" --network "${NETWORK}" \
  --env SPRING_PROFILES_ACTIVE=openai \
  "${IMAGE}" --server.port=8080 >"${MISSING_KEY_LOG}" 2>&1
missing_key_exit=$?
set -e
[[ "${missing_key_exit}" -ne 0 ]] || fail "OpenAI profile started without its required key"
grep -Fq 'OpenAI API key is required when OpenAI is selected' "${MISSING_KEY_LOG}" \
  || fail "missing-key startup failure was not explicit"

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  jq -n \
    --arg status NOT_RUN \
    --arg reason "OPENAI_API_KEY was not supplied to the required keyless release gate" \
    --arg checkedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{status: $status, reason: $reason, checkedAt: $checkedAt}' \
    >"${REPORT_DIR}/openai-keyed-summary.json"
fi

docker logs "${APP_NAME}" >"${APP_LOG}" 2>&1 || true
jq -n \
  --arg status PASS \
  --arg checkpoint course-0.3.3-p08-production-ready \
  --arg image "${IMAGE}" \
  --arg sourceCommit "${SOURCE_COMMIT}" \
  --arg missingKeyExit "${missing_key_exit}" \
  --slurpfile imageInspect "${REPORT_DIR}/release-image-inspect.json" \
  --slurpfile healthBefore "${REPORT_DIR}/release-health-before.json" \
  --slurpfile readinessBefore "${REPORT_DIR}/release-readiness-before-restart.json" \
  --slurpfile migration "${REPORT_DIR}/release-migration.json" \
  --slurpfile probe "${REPORT_DIR}/release-probe.json" \
  --slurpfile healthAfter "${REPORT_DIR}/release-health-after.json" \
  --slurpfile readinessAfter "${REPORT_DIR}/release-readiness-after-restart.json" \
  --slurpfile conversationAfter "${REPORT_DIR}/release-conversation-after-restart.json" \
  --slurpfile cleanup "${REPORT_DIR}/release-cleanup.json" \
  --slurpfile readinessAfterCleanup "${REPORT_DIR}/release-readiness-after-cleanup.json" \
  --slurpfile keyedEvidence "${REPORT_DIR}/openai-keyed-summary.json" \
  '{
    status: $status,
    checkpoint: $checkpoint,
    image: $image,
    sourceCommit: $sourceCommit,
    imageRevision: $imageInspect[0][0].Config.Labels["org.opencontainers.image.revision"],
    beforeRestart: {health: $healthBefore[0], readiness: $readinessBefore[0]},
    migration: $migration[0],
    persistenceProbe: $probe[0],
    afterRestart: {
      health: $healthAfter[0],
      readiness: $readinessAfter[0],
      conversation: $conversationAfter[0]
    },
    retention: $cleanup[0],
    afterCleanup: $readinessAfterCleanup[0],
    missingRequiredCredential: {
      expectedStartupFailure: true,
      exitCode: ($missingKeyExit | tonumber),
      validationMessageObserved: true
    },
    optionalOpenAiEvidence: $keyedEvidence[0]
  }' >"${REPORT_DIR}/release-keyless-summary.json"

printf 'Release smoke PASS. Evidence: %s\n' "${REPORT_DIR}/release-keyless-summary.json"
