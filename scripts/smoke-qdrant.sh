#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_PORT="${COURSE_QDRANT_APP_PORT:-18082}"
FAILURE_APP_PORT="${COURSE_QDRANT_FAILURE_APP_PORT:-18083}"
QDRANT_HTTP_PORT="${COURSE_QDRANT_HTTP_PORT:-16333}"
QDRANT_GRPC_PORT="${COURSE_QDRANT_GRPC_PORT:-16334}"
QDRANT_IMAGE="${COURSE_QDRANT_IMAGE:-qdrant/qdrant:v1.16.1}"
COLLECTION_PREFIX="${COURSE_QDRANT_COLLECTION_PREFIX:-course_prod07__}"
COLLECTION_NAME="${COLLECTION_PREFIX}knowledge-article"
CONTAINER_NAME="${COURSE_QDRANT_CONTAINER_NAME:-ai-fabric-course-qdrant-$$}"
REPORT_DIR="${COURSE_QDRANT_REPORT_DIR:-${ROOT_DIR}/target/course-release-evidence}"
APP_LOG="${REPORT_DIR}/qdrant-app.log"
FAILURE_APP_LOG="${REPORT_DIR}/qdrant-unavailable-app.log"
APP_PID=""
MANAGE_CONTAINER="${COURSE_QDRANT_MANAGE_CONTAINER:-true}"

cleanup() {
  if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
    kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
  fi
  if [[ "${MANAGE_CONTAINER}" == "true" ]]; then
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

fail() {
  printf 'Qdrant smoke failed: %s\n' "$1" >&2
  [[ -f "${APP_LOG}" ]] && tail -n 100 "${APP_LOG}" >&2
  [[ -f "${FAILURE_APP_LOG}" ]] && tail -n 80 "${FAILURE_APP_LOG}" >&2
  if [[ "${MANAGE_CONTAINER}" == "true" ]]; then
    docker logs --tail 80 "${CONTAINER_NAME}" >&2 || true
  fi
  exit 1
}

for command in java curl jq docker; do
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

if [[ "${MANAGE_CONTAINER}" == "true" ]]; then
  docker run --detach --rm \
    --name "${CONTAINER_NAME}" \
    --publish "${QDRANT_HTTP_PORT}:6333" \
    --publish "${QDRANT_GRPC_PORT}:6334" \
    "${QDRANT_IMAGE}" >/dev/null || fail "Qdrant container did not start"
fi

for _ in $(seq 1 90); do
  if curl --fail --silent --show-error "http://127.0.0.1:${QDRANT_HTTP_PORT}/readyz" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl --fail --silent --show-error "http://127.0.0.1:${QDRANT_HTTP_PORT}/readyz" \
  >"${REPORT_DIR}/qdrant-ready.txt" || fail "Qdrant did not become ready"

start_app() {
  local app_port="$1"
  local qdrant_port="$2"
  local log_file="$3"
  DEBUG=false \
  AI_PROVIDERS_QDRANT_HOST=127.0.0.1 \
  AI_PROVIDERS_QDRANT_PORT="${qdrant_port}" \
  AI_PROVIDERS_QDRANT_GRPC_PORT="${QDRANT_GRPC_PORT}" \
  AI_PROVIDERS_QDRANT_PREFER_GRPC=false \
  AI_PROVIDERS_QDRANT_COLLECTION_PREFIX="${COLLECTION_PREFIX}" \
  java -jar "${JAR_PATH}" \
    --spring.profiles.active=qdrant \
    --server.port="${app_port}" \
    --logging.level.root=INFO \
    --logging.level.dev.aifabric.course.support=INFO \
    >"${log_file}" 2>&1 &
  APP_PID=$!

  for _ in $(seq 1 90); do
    if curl --fail --silent --show-error "http://127.0.0.1:${app_port}/actuator/health" >/dev/null 2>&1; then
      return
    fi
    kill -0 "${APP_PID}" 2>/dev/null || fail "application exited before becoming healthy"
    sleep 1
  done
  fail "application did not become healthy"
}

stop_app() {
  if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
    kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
  fi
  APP_PID=""
}

BASE_URL="http://127.0.0.1:${APP_PORT}"
start_app "${APP_PORT}" "${QDRANT_HTTP_PORT}" "${APP_LOG}"

curl --fail --silent --show-error "${BASE_URL}/api/demo/health" \
  >"${REPORT_DIR}/qdrant-deployment-health.json" || fail "deployment health failed"
curl --fail --silent --show-error -X POST "${BASE_URL}/api/demo/seed" \
  >"${REPORT_DIR}/qdrant-seed.json" || fail "fixture seed failed"
curl --fail --silent --show-error -X POST "${BASE_URL}/api/demo/index" \
  >"${REPORT_DIR}/qdrant-index.json" || fail "Qdrant index failed"
jq -e '.indexedArticles == 9 and .indexedVectors == 9' \
  "${REPORT_DIR}/qdrant-index.json" >/dev/null || fail "Qdrant did not index all source articles"

curl --fail --silent --show-error "${BASE_URL}/api/demo/readiness" \
  >"${REPORT_DIR}/qdrant-readiness.json" || fail "Qdrant readiness failed"
jq -e '.checkpoint == "course-0.3.3-p08-production-ready"
  and .indexedVectors == 9
  and .capabilities.managedVectorProfile == true
  and .vectorProvider.provider == "qdrant"
  and .vectorProvider.nativeClient == "qdrant-rest-api"
  and .vectorProvider.transport == "rest"
  and .vectorProvider.scopePrefix == "'"${COLLECTION_PREFIX}"'"
  and .vectorProvider.searchMetadataFiltering == true
  and .vectorProvider.scanMetadataFiltering == true
  and .vectorProvider.durableStorage == true
  and .vectorProvider.productionProfileSafe == true' \
  "${REPORT_DIR}/qdrant-readiness.json" >/dev/null || fail "Qdrant provider readiness is incomplete"

curl --fail --silent --show-error \
  "http://127.0.0.1:${QDRANT_HTTP_PORT}/collections/${COLLECTION_NAME}" \
  >"${REPORT_DIR}/qdrant-collection.json" || fail "Qdrant collection was not created"
jq -e '.result.config.params.vectors.size == 384
  and (.result.payload_schema | has("knowledgeSourceHandleRef"))' \
  "${REPORT_DIR}/qdrant-collection.json" >/dev/null || fail "Qdrant dimensions or payload index are incorrect"

curl --fail --silent --show-error "${BASE_URL}/api/quality/rag/golden" \
  -H 'Authorization: Bearer course-alex-local-token' \
  >"${REPORT_DIR}/qdrant-golden-alex.json" || fail "Tenant Blue Qdrant quality suite failed"
curl --fail --silent --show-error "${BASE_URL}/api/quality/rag/golden" \
  -H 'Authorization: Bearer course-riley-local-token' \
  >"${REPORT_DIR}/qdrant-golden-riley.json" || fail "Tenant Red Qdrant quality suite failed"
jq -e '.passed == true and .failedCases == 0' \
  "${REPORT_DIR}/qdrant-golden-alex.json" >/dev/null || fail "Tenant Blue Qdrant evidence contract changed"
jq -e '.passed == true and .cases[0].observedEvidenceIds == ["article-vpn-red"]' \
  "${REPORT_DIR}/qdrant-golden-riley.json" >/dev/null || fail "Tenant Red Qdrant evidence contract changed"

curl --fail --silent --show-error -X POST "${BASE_URL}/api/knowledge/articles" \
  -H 'Authorization: Bearer course-alex-local-token' \
  -H 'Content-Type: application/json' \
  -d '{"id":"article-qdrant-sync","title":"Register a hardware key","body":"Register and verify the hardware key before revoking the previous method.","category":"authentication"}' \
  >"${REPORT_DIR}/qdrant-sync-create.json" || fail "Qdrant Data Sync create failed"
curl --fail --silent --show-error -X PUT "${BASE_URL}/api/knowledge/articles/article-qdrant-sync" \
  -H 'Authorization: Bearer course-alex-local-token' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Replace a hardware key","body":"Verify the replacement key, then revoke the previous hardware key."}' \
  >"${REPORT_DIR}/qdrant-sync-update.json" || fail "Qdrant Data Sync update failed"
create_vector_id="$(jq -r '.sync.vectorId' "${REPORT_DIR}/qdrant-sync-create.json")"
update_vector_id="$(jq -r '.sync.vectorId' "${REPORT_DIR}/qdrant-sync-update.json")"
[[ -n "${create_vector_id}" && "${create_vector_id}" == "${update_vector_id}" ]] \
  || fail "Qdrant upsert did not preserve stable vector identity"
curl --fail --silent --show-error -X DELETE "${BASE_URL}/api/knowledge/articles/article-qdrant-sync" \
  -H 'Authorization: Bearer course-alex-local-token' \
  >"${REPORT_DIR}/qdrant-sync-delete.json" || fail "Qdrant Data Sync delete failed"
jq -e '.success == true and .operation == "DELETE"' \
  "${REPORT_DIR}/qdrant-sync-delete.json" >/dev/null || fail "Qdrant delete result is incomplete"
curl --fail --silent --show-error "${BASE_URL}/api/demo/readiness" \
  >"${REPORT_DIR}/qdrant-readiness-after-sync.json" || fail "post-sync Qdrant readiness failed"
jq -e '.indexedVectors == 9' \
  "${REPORT_DIR}/qdrant-readiness-after-sync.json" >/dev/null || fail "Qdrant delete left a stale point"

stop_app

BROKEN_QDRANT_PORT="${COURSE_QDRANT_BROKEN_PORT:-16999}"
BROKEN_URL="http://127.0.0.1:${FAILURE_APP_PORT}"
start_app "${FAILURE_APP_PORT}" "${BROKEN_QDRANT_PORT}" "${FAILURE_APP_LOG}"
curl --fail --silent --show-error -X POST "${BROKEN_URL}/api/demo/seed" \
  >"${REPORT_DIR}/qdrant-unavailable-seed.json" || fail "failure fixture seed failed"
unavailable_status="$(curl --silent --show-error --output "${REPORT_DIR}/qdrant-unavailable-index.json" \
  --write-out '%{http_code}' -X POST "${BROKEN_URL}/api/demo/index")"
[[ "${unavailable_status}" == "503" ]] || fail "unreachable Qdrant did not return HTTP 503"
jq -e '.title == "AI evidence operation failed"
  and .detail == "Knowledge evidence provider is unavailable"' \
  "${REPORT_DIR}/qdrant-unavailable-index.json" >/dev/null || fail "Qdrant failure was not safely projected"
stop_app

jq -n \
  --arg status PASS \
  --arg checkpoint course-0.3.3-p08-production-ready \
  --arg image "${QDRANT_IMAGE}" \
  --arg collectionName "${COLLECTION_NAME}" \
  --arg unavailableStatus "${unavailable_status}" \
  --slurpfile health "${REPORT_DIR}/qdrant-deployment-health.json" \
  --slurpfile readiness "${REPORT_DIR}/qdrant-readiness.json" \
  --slurpfile collection "${REPORT_DIR}/qdrant-collection.json" \
  --slurpfile goldenAlex "${REPORT_DIR}/qdrant-golden-alex.json" \
  --slurpfile goldenRiley "${REPORT_DIR}/qdrant-golden-riley.json" \
  --slurpfile syncCreate "${REPORT_DIR}/qdrant-sync-create.json" \
  --slurpfile syncUpdate "${REPORT_DIR}/qdrant-sync-update.json" \
  --slurpfile syncDelete "${REPORT_DIR}/qdrant-sync-delete.json" \
  --slurpfile unavailable "${REPORT_DIR}/qdrant-unavailable-index.json" \
  '{
    status: $status,
    checkpoint: $checkpoint,
    profile: "qdrant",
    qdrantImage: $image,
    deployment: $health[0],
    readiness: $readiness[0],
    collection: {
      name: $collectionName,
      dimensions: $collection[0].result.config.params.vectors.size,
      distance: $collection[0].result.config.params.vectors.distance,
      payloadSchema: $collection[0].result.payload_schema
    },
    quality: {alex: $goldenAlex[0], riley: $goldenRiley[0]},
    dataSync: {create: $syncCreate[0], update: $syncUpdate[0], delete: $syncDelete[0]},
    unavailableProvider: {httpStatus: $unavailableStatus, response: $unavailable[0]}
  }' >"${REPORT_DIR}/qdrant-smoke-summary.json"

printf 'Qdrant smoke PASS. Evidence: %s\n' "${REPORT_DIR}/qdrant-smoke-summary.json"
