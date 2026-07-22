#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${COURSE_BASE_URL:-http://localhost:8080}"

curl --fail --silent --show-error -X POST "${BASE_URL}/api/demo/reset"
printf '\n'
curl --fail --silent --show-error -X POST "${BASE_URL}/api/demo/seed"
printf '\n'
curl --fail --silent --show-error "${BASE_URL}/api/demo/readiness"
printf '\n'
