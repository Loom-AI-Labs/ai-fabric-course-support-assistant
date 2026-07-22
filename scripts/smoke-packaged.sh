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
curl --fail --silent --show-error -X POST "${BASE_URL}/api/demo/index" \
  >"${REPORT_DIR}/index.json" || fail "evidence indexing failed"

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
jq -e '.checkpoint == "course-0.3.3-p03-prompt-overlays"
  and .indexedVectors == 9
  and .indexedMessageVectors == 1
  and .capabilities.tenantSecurity == true
  and .capabilities.piiProtection == true
  and .capabilities.modeRouting == true
  and .capabilities.promptOverlays == true' \
  "${REPORT_DIR}/readiness.json" >/dev/null || fail "readiness contract is incomplete"
jq -e '.status == "UP"
  and .checkpoint == "course-0.3.3-p03-prompt-overlays"
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
  --arg checkpoint course-0.3.3-p03-prompt-overlays \
  --arg profile local \
  --arg unauthenticatedStatus "${unauthenticated_status}" \
  --arg invalidCredentialStatus "${invalid_status}" \
  --slurpfile health "${REPORT_DIR}/deployment-health.json" \
  --slurpfile readiness "${REPORT_DIR}/readiness.json" \
  '{
    status: $status,
    checkpoint: $checkpoint,
    profile: $profile,
    unauthenticatedStatus: $unauthenticatedStatus,
    invalidCredentialStatus: $invalidCredentialStatus,
    deployment: $health[0],
    readiness: $readiness[0]
  }' >"${REPORT_DIR}/packaged-smoke-summary.json"

printf 'Packaged smoke PASS. Evidence: %s\n' "${REPORT_DIR}/packaged-smoke-summary.json"
