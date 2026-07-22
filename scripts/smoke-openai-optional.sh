#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${COURSE_OPENAI_SMOKE_PORT:-18085}"
BASE_URL="http://127.0.0.1:${PORT}"
REPORT_DIR="${COURSE_SMOKE_REPORT_DIR:-${ROOT_DIR}/target/course-release-evidence}"
REPORT_PATH="${REPORT_DIR}/openai-keyed-summary.json"
APP_LOG="${REPORT_DIR}/openai-keyed-app.log"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ai-fabric-course-openai.XXXXXX")"
APP_PID=""

cleanup() {
  if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
    kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
  fi
  find "${WORK_DIR}" -depth -delete 2>/dev/null || true
}
trap cleanup EXIT INT TERM

mkdir -p "${REPORT_DIR}"

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  jq -n \
    --arg status NOT_RUN \
    --arg reason "OPENAI_API_KEY was not supplied; the required keyless gate is unaffected" \
    --arg checkedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{status: $status, reason: $reason, checkedAt: $checkedAt}' >"${REPORT_PATH}"
  printf 'OpenAI keyed smoke NOT RUN. Evidence: %s\n' "${REPORT_PATH}"
  exit 0
fi

for command in java curl jq; do
  command -v "${command}" >/dev/null 2>&1 || {
    printf 'OpenAI smoke failed: required command %s is unavailable\n' "${command}" >&2
    exit 1
  }
done

cd "${ROOT_DIR}"
if [[ "${COURSE_SMOKE_USE_EXISTING_JAR:-false}" != "true" ]]; then
  ./mvnw --batch-mode --no-transfer-progress clean verify
fi

if [[ ! -s models/embeddings/all-MiniLM-L6-v2.onnx || ! -s models/embeddings/tokenizer.json ]]; then
  ./scripts/download-onnx-model.sh
fi

JAR_PATH="$(find target -maxdepth 1 -type f -name 'ai-fabric-course-support-assistant-*.jar' ! -name '*.original' -print -quit)"
[[ -n "${JAR_PATH}" ]] || {
  printf 'OpenAI smoke failed: packaged JAR was not found\n' >&2
  exit 1
}

AI_FABRIC_LUCENE_INDEX_PATH="${WORK_DIR}/lucene" \
OPENAI_ENABLED=true \
java -jar "${JAR_PATH}" \
  --spring.profiles.active=openai \
  --server.port="${PORT}" \
  --logging.level.root=INFO \
  --logging.level.dev.aifabric.course.support=INFO \
  >"${APP_LOG}" 2>&1 &
APP_PID=$!

for _ in $(seq 1 120); do
  if curl --fail --silent "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
    break
  fi
  if ! kill -0 "${APP_PID}" 2>/dev/null; then
    tail -n 100 "${APP_LOG}" >&2
    exit 1
  fi
  sleep 1
done

curl --fail --silent "${BASE_URL}/actuator/health" >/dev/null
curl --fail --silent -X POST "${BASE_URL}/api/demo/seed" >"${REPORT_DIR}/openai-seed.json"
curl --fail --silent -X POST "${BASE_URL}/api/demo/index" >"${REPORT_DIR}/openai-index.json"
curl --fail --silent -X POST "${BASE_URL}/api/assistant/query" \
  -H 'Authorization: Bearer course-alex-local-token' \
  -H 'Content-Type: application/json' \
  -d '{"message":"What should I do after repeated failed sign-in attempts locked my account?"}' \
  >"${REPORT_DIR}/openai-answer.json"
curl --fail --silent "${BASE_URL}/api/demo/health" >"${REPORT_DIR}/openai-health.json"

jq -e '.status == "ANSWERED" and (.answer | length) > 0
  and (.evidence | map(.id) | index("policy-account-lockout-01")) != null
  and .diagnostics.generationAttempted == true' \
  "${REPORT_DIR}/openai-answer.json" >/dev/null
jq -e '.checkpoint == "course-0.3.3-p08-production-ready"
  and .provider.generationEnabled == true
  and .provider.orchestration == "openai"
  and .provider.generation == "openai"
  and .provider.fallbackEnabled == false' \
  "${REPORT_DIR}/openai-health.json" >/dev/null

jq -n \
  --arg status PASS \
  --arg checkedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --slurpfile health "${REPORT_DIR}/openai-health.json" \
  --slurpfile answer "${REPORT_DIR}/openai-answer.json" \
  '{
    status: $status,
    checkedAt: $checkedAt,
    deployment: $health[0],
    scenario: {
      result: $answer[0].status,
      evidenceIds: [$answer[0].evidence[].id],
      generationAttempted: $answer[0].diagnostics.generationAttempted,
      requestId: $answer[0].diagnostics.requestId
    }
  }' >"${REPORT_PATH}"

printf 'OpenAI keyed smoke PASS. Evidence: %s\n' "${REPORT_PATH}"
