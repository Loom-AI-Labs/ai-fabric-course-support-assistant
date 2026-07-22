#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${COURSE_SMOKE_PORT:-18081}"
BASE_URL="http://127.0.0.1:${PORT}"
REPORT_DIR="${COURSE_SMOKE_REPORT_DIR:-${ROOT_DIR}/target/course-release-evidence}"
APP_LOG="${REPORT_DIR}/packaged-app.log"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ai-fabric-course-smoke.XXXXXX")"
APP_PID=""

cleanup() {
  if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
    kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
  fi
  find "${WORK_DIR}" -depth -delete 2>/dev/null || true
}
trap cleanup EXIT INT TERM

fail() {
  printf 'Packaged smoke failed: %s\n' "$1" >&2
  if [[ -f "${APP_LOG}" ]]; then
    tail -n 120 "${APP_LOG}" >&2
  fi
  exit 1
}

for command in java curl jq; do
  command -v "${command}" >/dev/null 2>&1 || fail "required command '${command}' is unavailable"
done

cd "${ROOT_DIR}"
mkdir -p "${REPORT_DIR}"

if [[ "${COURSE_SMOKE_USE_EXISTING_JAR:-false}" != "true" ]]; then
  ./mvnw --batch-mode --no-transfer-progress clean package
fi

if [[ ! -s models/embeddings/all-MiniLM-L6-v2.onnx || ! -s models/embeddings/tokenizer.json ]]; then
  fail "ONNX assets are missing; run ./scripts/download-onnx-model.sh"
fi

JAR_PATH="$(find target -maxdepth 1 -type f -name 'ai-fabric-course-support-assistant-*.jar' ! -name '*.original' -print -quit)"
[[ -n "${JAR_PATH}" ]] || fail "packaged application jar was not found"

DEBUG=false \
AI_FABRIC_LUCENE_INDEX_PATH="${WORK_DIR}/lucene" \
java -jar "${JAR_PATH}" \
  --spring.profiles.active=local \
  --server.port="${PORT}" \
  --logging.level.root=INFO \
  --logging.level.dev.aifabric.course.support=INFO \
  >"${APP_LOG}" 2>&1 &
APP_PID=$!

for _ in $(seq 1 90); do
  if curl --fail --silent --show-error "${BASE_URL}/actuator/health" \
      >"${REPORT_DIR}/actuator-health.json" 2>/dev/null; then
    break
  fi
  kill -0 "${APP_PID}" 2>/dev/null || fail "application exited before becoming healthy"
  sleep 1
done

curl --fail --silent --show-error "${BASE_URL}/actuator/health" \
  >"${REPORT_DIR}/actuator-health.json" || fail "application did not become healthy"
curl --fail --silent --show-error "${BASE_URL}/api/demo/health" \
  >"${REPORT_DIR}/deployment-health.json" || fail "deployment health failed"

unauthenticated_status="$(curl --silent --show-error --output "${REPORT_DIR}/unauthenticated.json" \
  --write-out '%{http_code}' "${BASE_URL}/api/knowledge/articles")"
invalid_status="$(curl --silent --show-error --output "${REPORT_DIR}/invalid-credential.json" \
  --write-out '%{http_code}' "${BASE_URL}/api/knowledge/articles" \
  -H 'Authorization: Bearer invalid-course-token')"
[[ "${unauthenticated_status}" == "401" ]] || fail "missing bearer credential did not return 401"
[[ "${invalid_status}" == "401" ]] || fail "invalid bearer credential did not return 401"

curl --fail --silent --show-error -X POST "${BASE_URL}/api/demo/reset" \
  >"${REPORT_DIR}/reset.json" || fail "fixture reset failed"
curl --fail --silent --show-error -X POST "${BASE_URL}/api/demo/seed" \
  >"${REPORT_DIR}/seed.json" || fail "fixture seed failed"
curl --fail --silent --show-error -X POST "${BASE_URL}/api/admin/migrations/knowledge-articles" \
  -H 'Authorization: Bearer course-alex-local-token' \
  -H 'Content-Type: application/json' \
  -d '{"batchSize":3,"rateLimit":0,"reindexExisting":false}' \
  >"${REPORT_DIR}/migration-start.json" || fail "migration start failed"
migration_job_id="$(jq -r '.jobId // empty' "${REPORT_DIR}/migration-start.json")"
[[ -n "${migration_job_id}" ]] || fail "migration start did not return a job ID"

for _ in $(seq 1 100); do
  curl --fail --silent --show-error \
    "${BASE_URL}/api/admin/migrations/knowledge-articles/${migration_job_id}" \
    -H 'Authorization: Bearer course-alex-local-token' \
    >"${REPORT_DIR}/migration-progress.json" || fail "migration progress failed"
  migration_status="$(jq -r '.status' "${REPORT_DIR}/migration-progress.json")"
  [[ "${migration_status}" != "FAILED" && "${migration_status}" != "CANCELLED" ]] \
    || fail "migration reached ${migration_status}"
  if [[ "${migration_status}" == "COMPLETED" ]]; then
    break
  fi
  sleep 0.1
done
[[ "$(jq -r '.status' "${REPORT_DIR}/migration-progress.json")" == "COMPLETED" ]] \
  || fail "migration did not complete"

for _ in $(seq 1 100); do
  curl --fail --silent --show-error \
    "${BASE_URL}/api/admin/migrations/knowledge-articles/${migration_job_id}" \
    -H 'Authorization: Bearer course-alex-local-token' \
    >"${REPORT_DIR}/migration-progress.json" || fail "migration readiness failed"
  if jq -e '.currentIndexedVectors == 9 and .pendingQueueEntries == 0
      and .processingQueueEntries == 0 and .indexingCaughtUp == true
      and .fullSourceVectorCoverage == true' \
      "${REPORT_DIR}/migration-progress.json" >/dev/null; then
    break
  fi
  sleep 0.1
done
jq -e '.status == "COMPLETED" and .totalSourceRows == 9 and .processedSourceRows == 9
  and .failedRows == 0 and .currentIndexedVectors == 9 and .indexingCaughtUp == true
  and .fullSourceVectorCoverage == true' \
  "${REPORT_DIR}/migration-progress.json" >/dev/null || fail "migration indexing did not catch up"

completed_before_rerun="$(jq -r '.completedQueueEntries' "${REPORT_DIR}/migration-progress.json")"
curl --fail --silent --show-error -X POST "${BASE_URL}/api/admin/migrations/knowledge-articles" \
  -H 'Authorization: Bearer course-alex-local-token' \
  -H 'Content-Type: application/json' \
  -d '{"batchSize":4,"rateLimit":0,"reindexExisting":false}' \
  >"${REPORT_DIR}/migration-rerun-start.json" || fail "migration rerun start failed"
rerun_job_id="$(jq -r '.jobId // empty' "${REPORT_DIR}/migration-rerun-start.json")"
[[ -n "${rerun_job_id}" ]] || fail "migration rerun did not return a job ID"
for _ in $(seq 1 100); do
  curl --fail --silent --show-error \
    "${BASE_URL}/api/admin/migrations/knowledge-articles/${rerun_job_id}" \
    -H 'Authorization: Bearer course-alex-local-token' \
    >"${REPORT_DIR}/migration-rerun.json" || fail "migration rerun progress failed"
  [[ "$(jq -r '.status' "${REPORT_DIR}/migration-rerun.json")" == "COMPLETED" ]] && break
  sleep 0.1
done
jq -e --argjson completed "${completed_before_rerun}" \
  '.status == "COMPLETED" and .processedSourceRows == 9 and .currentIndexedVectors == 9
    and .completedQueueEntries == $completed' \
  "${REPORT_DIR}/migration-rerun.json" >/dev/null || fail "migration rerun was not idempotent"

unauthorized_sync_status="$(curl --silent --show-error --output "${REPORT_DIR}/sync-unauthorized.json" \
  --write-out '%{http_code}' -X POST "${BASE_URL}/api/knowledge/articles" \
  -H 'Authorization: Bearer course-riley-local-token' \
  -H 'Content-Type: application/json' \
  -d '{"id":"article-riley-forbidden","title":"Forbidden sync","body":"This row must not be created.","category":"security"}')"
raw_sync_status="$(curl --silent --show-error --output "${REPORT_DIR}/sync-raw-endpoint.json" \
  --write-out '%{http_code}' -X POST "${BASE_URL}/api/internal/ai-data-sync/upsert" \
  -H 'Authorization: Bearer course-alex-local-token' \
  -H 'Content-Type: application/json' \
  -d '{"vectorSpace":"unknown-space","id":"forged","content":"forged","trace":{"authContext":{"subjectId":"customer-alex"}}}')"
[[ "${unauthorized_sync_status}" == "403" ]] || fail "unauthorized application sync did not return 403"
case "${raw_sync_status}" in
  401|403|404) ;;
  *) fail "raw framework data-sync endpoint was externally reachable (status=${raw_sync_status})" ;;
esac

curl --fail --silent --show-error -X POST "${BASE_URL}/api/knowledge/articles" \
  -H 'Authorization: Bearer course-alex-local-token' \
  -H 'Content-Type: application/json' \
  -d '{"id":"article-live-sync","title":"Enroll a passkey","body":"Register a passkey in Security Settings before removing the password.","category":"authentication"}' \
  >"${REPORT_DIR}/sync-create.json" || fail "trusted article create sync failed"
jq -e '.article.id == "article-live-sync" and .sync.success == true
  and .sync.operation == "UPSERT" and .sync.vectorSpace == "knowledge-article"
  and .sync.id == "article-live-sync"' \
  "${REPORT_DIR}/sync-create.json" >/dev/null || fail "create sync response was incomplete"

curl --fail --silent --show-error --get "${BASE_URL}/api/knowledge/search" \
  -H 'Authorization: Bearer course-alex-local-token' \
  --data-urlencode 'q=How do I register a passkey?' \
  >"${REPORT_DIR}/sync-create-search.json" || fail "created evidence was not searchable"
jq -e '[.evidence[] | select(.evidenceId == "article-live-sync")] | length == 1' \
  "${REPORT_DIR}/sync-create-search.json" >/dev/null || fail "created vector was not retrieved"

curl --fail --silent --show-error -X PUT "${BASE_URL}/api/knowledge/articles/article-live-sync" \
  -H 'Authorization: Bearer course-alex-local-token' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Replace a password with a security key","body":"Register the hardware security key, verify it, then revoke the previous login method."}' \
  >"${REPORT_DIR}/sync-update.json" || fail "trusted article update sync failed"
jq -e '.article.id == "article-live-sync" and .sync.success == true
  and .sync.operation == "UPSERT" and .sync.id == "article-live-sync"
  and .sync.sourceVersion == "1"' \
  "${REPORT_DIR}/sync-update.json" >/dev/null || fail "update did not preserve stable evidence identity"

curl --fail --silent --show-error --get "${BASE_URL}/api/knowledge/search" \
  -H 'Authorization: Bearer course-alex-local-token' \
  --data-urlencode 'q=How do I revoke the previous security key login?' \
  >"${REPORT_DIR}/sync-update-search.json" || fail "updated evidence search failed"
jq -e '[.evidence[] | select(.evidenceId == "article-live-sync"
  and (.content | contains("hardware security key"))
  and ((.content | contains("removing the password")) | not))] | length == 1' \
  "${REPORT_DIR}/sync-update-search.json" >/dev/null || fail "search returned stale evidence after update"

batch_limit_status="$(curl --silent --show-error --output "${REPORT_DIR}/sync-batch-limit.json" \
  --write-out '%{http_code}' -X POST "${BASE_URL}/api/knowledge/sync/reconcile" \
  -H 'Authorization: Bearer course-alex-local-token' \
  -H 'Content-Type: application/json' \
  -d '{"articleIds":["article-account-lockout","article-two-factor","article-api-key"]}')"
[[ "${batch_limit_status}" == "400" ]] || fail "oversized sync batch did not return 400"
jq -e '.errorCode == "BATCH_TOO_LARGE" and .succeededOperations == 0
  and .failedOperations == 3' \
  "${REPORT_DIR}/sync-batch-limit.json" >/dev/null || fail "batch limit failure was not explicit"

curl --fail --silent --show-error -X DELETE "${BASE_URL}/api/knowledge/articles/article-live-sync" \
  -H 'Authorization: Bearer course-alex-local-token' \
  >"${REPORT_DIR}/sync-delete.json" || fail "trusted article delete sync failed"
jq -e '.success == true and .operation == "DELETE" and .id == "article-live-sync"' \
  "${REPORT_DIR}/sync-delete.json" >/dev/null || fail "delete sync response was incomplete"

curl --fail --silent --show-error --get "${BASE_URL}/api/knowledge/search" \
  -H 'Authorization: Bearer course-alex-local-token' \
  --data-urlencode 'q=How do I revoke the previous security key login?' \
  >"${REPORT_DIR}/sync-delete-search.json" || fail "post-delete evidence search failed"
jq -e 'all(.evidence[]; .evidenceId != "article-live-sync")' \
  "${REPORT_DIR}/sync-delete-search.json" >/dev/null || fail "deleted evidence remained retrievable"

curl --fail --silent --show-error --get "${BASE_URL}/api/knowledge/search" \
  -H 'Authorization: Bearer course-alex-local-token' \
  -H 'X-Tenant-Id: tenant-red' \
  --data-urlencode 'q=restore VPN access and refresh certificate' \
  >"${REPORT_DIR}/alex-search.json" || fail "Tenant Blue search failed"
curl --fail --silent --show-error --get "${BASE_URL}/api/knowledge/search" \
  -H 'Authorization: Bearer course-riley-local-token' \
  --data-urlencode 'q=restore VPN access and enroll replacement device' \
  >"${REPORT_DIR}/riley-search.json" || fail "Tenant Red search failed"

jq -e '[.evidence[] | select(.evidenceId == "article-vpn-blue")] | length == 1' \
  "${REPORT_DIR}/alex-search.json" >/dev/null || fail "Tenant Blue evidence was not retrieved"
jq -e 'all(.evidence[]; .evidenceId != "article-vpn-red" and .evidenceId != "article-payroll-red")' \
  "${REPORT_DIR}/alex-search.json" >/dev/null || fail "Tenant Blue response leaked Tenant Red evidence"
jq -e '[.evidence[] | select(.evidenceId == "article-vpn-red")] | length == 1' \
  "${REPORT_DIR}/riley-search.json" >/dev/null || fail "Tenant Red evidence was not retrieved"
jq -e 'all(.evidence[]; .evidenceId != "article-vpn-blue" and .evidenceId != "article-payroll-red")' \
  "${REPORT_DIR}/riley-search.json" >/dev/null || fail "Tenant Red response leaked forbidden evidence"

curl --fail --silent --show-error -X POST "${BASE_URL}/api/support/messages" \
  -H 'Authorization: Bearer course-alex-local-token' \
  -H 'Content-Type: application/json' \
  -d '{"content":"Contact alex.private@example.com about SSN 123-45-6789"}' \
  >"${REPORT_DIR}/redacted-message.json" || fail "PII-safe message intake failed"
curl --fail --silent --show-error "${BASE_URL}/api/support/messages" \
  -H 'Authorization: Bearer course-alex-local-token' \
  >"${REPORT_DIR}/stored-messages.json" || fail "safe message projection failed"

jq -e '.piiDetected == true and (.safeContent | contains("alex.private@example.com") | not) and (.safeContent | contains("123-45-6789") | not)' \
  "${REPORT_DIR}/redacted-message.json" >/dev/null || fail "message response exposed raw PII"
jq -e 'all(.[]; (.safeContent | contains("alex.private@example.com") | not) and (.safeContent | contains("123-45-6789") | not))' \
  "${REPORT_DIR}/stored-messages.json" >/dev/null || fail "stored message projection exposed raw PII"

curl --fail --silent --show-error "${BASE_URL}/api/demo/readiness" \
  >"${REPORT_DIR}/readiness.json" || fail "readiness failed"
curl --fail --silent --show-error "${BASE_URL}/api/demo/prompts" \
  >"${REPORT_DIR}/prompt-posture.json" || fail "prompt posture failed"
jq -e '.candidateVersions == ["v1-course-support", "v1-support", "v1"]
  and .resolvedVersions["intent-classifier"] == "v1-course-support"
  and .resolvedVersions["support-answer"] == "v1-course-support"
  and .resolvedVersions["action-selector"] == "v1"' \
  "${REPORT_DIR}/prompt-posture.json" >/dev/null || fail "prompt overlay resolution is incomplete"
jq -e '.checkpoint == "course-0.3.3-p05-live-data-sync"
  and .indexedVectors == 9
  and .indexedMessageVectors == 1
  and .capabilities.tenantSecurity == true
  and .capabilities.piiProtection == true
  and .capabilities.modeRouting == true
  and .capabilities.promptOverlays == true
  and .capabilities.migrationBackfill == true
  and .capabilities.liveDataSync == true' \
  "${REPORT_DIR}/readiness.json" >/dev/null || fail "readiness contract is incomplete"
jq -e '.status == "UP"
  and .checkpoint == "course-0.3.3-p05-live-data-sync"
  and .version != "unknown"
  and .aiFabricVersion == "0.3.3"
  and .commit != "unknown"
  and .provider.mode == "local-retrieval"
  and .provider.generationEnabled == false
  and .provider.orchestration == "disabled"
  and .provider.orchestrationModel == "disabled"
  and .provider.generation == "disabled"
  and .provider.generationModel == "disabled"
  and .provider.embedding == "onnx"
  and .provider.vector == "lucene"
  and .provider.fallbackEnabled == false' \
  "${REPORT_DIR}/deployment-health.json" >/dev/null || fail "deployment identity or provider posture is incomplete"

if grep -Fq 'alex.private@example.com' "${APP_LOG}" || grep -Fq '123-45-6789' "${APP_LOG}"; then
  fail "application log contains raw PII"
fi

jq -n \
  --arg status PASS \
  --arg checkpoint course-0.3.3-p05-live-data-sync \
  --arg profile local \
  --arg unauthenticatedStatus "${unauthenticated_status}" \
  --arg invalidCredentialStatus "${invalid_status}" \
  --arg unauthorizedSyncStatus "${unauthorized_sync_status}" \
  --arg rawSyncStatus "${raw_sync_status}" \
  --arg batchLimitStatus "${batch_limit_status}" \
  --slurpfile health "${REPORT_DIR}/deployment-health.json" \
  --slurpfile readiness "${REPORT_DIR}/readiness.json" \
  --slurpfile migration "${REPORT_DIR}/migration-progress.json" \
  --slurpfile migrationRerun "${REPORT_DIR}/migration-rerun.json" \
  --slurpfile syncCreate "${REPORT_DIR}/sync-create.json" \
  --slurpfile syncUpdate "${REPORT_DIR}/sync-update.json" \
  --slurpfile syncDelete "${REPORT_DIR}/sync-delete.json" \
  --slurpfile syncBatchLimit "${REPORT_DIR}/sync-batch-limit.json" \
  '{
    status: $status,
    checkpoint: $checkpoint,
    profile: $profile,
    unauthenticatedStatus: $unauthenticatedStatus,
    invalidCredentialStatus: $invalidCredentialStatus,
    unauthorizedSyncStatus: $unauthorizedSyncStatus,
    rawSyncStatus: $rawSyncStatus,
    batchLimitStatus: $batchLimitStatus,
    deployment: $health[0],
    readiness: $readiness[0],
    migration: $migration[0],
    migrationRerun: $migrationRerun[0],
    liveDataSync: {
      create: $syncCreate[0],
      update: $syncUpdate[0],
      delete: $syncDelete[0],
      batchLimit: $syncBatchLimit[0]
    }
  }' >"${REPORT_DIR}/packaged-smoke-summary.json"

printf 'Packaged smoke PASS. Evidence: %s\n' "${REPORT_DIR}/packaged-smoke-summary.json"
