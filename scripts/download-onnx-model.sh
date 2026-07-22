#!/usr/bin/env bash
set -euo pipefail

MODEL_DIR="${AI_FABRIC_MODEL_DIR:-models/embeddings}"
MODEL_URL="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx"
TOKENIZER_URL="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json"

mkdir -p "${MODEL_DIR}"
curl --fail --location --retry 3 "${MODEL_URL}" --output "${MODEL_DIR}/all-MiniLM-L6-v2.onnx"
curl --fail --location --retry 3 "${TOKENIZER_URL}" --output "${MODEL_DIR}/tokenizer.json"

echo "Downloaded ONNX assets to ${MODEL_DIR}"
