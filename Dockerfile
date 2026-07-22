FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw mvnw
COPY pom.xml pom.xml
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src src
COPY scripts/download-onnx-model.sh scripts/download-onnx-model.sh
ARG SOURCE_COMMIT
RUN test -n "${SOURCE_COMMIT}"
RUN SOURCE_COMMIT="${SOURCE_COMMIT}" ./mvnw --batch-mode --no-transfer-progress clean verify
RUN ./scripts/download-onnx-model.sh

FROM eclipse-temurin:21-jre

ARG SOURCE_COMMIT
ARG SOURCE_BRANCH=main
ARG BUILD_TIME

LABEL org.opencontainers.image.title="AI Fabric Course Support Assistant" \
      org.opencontainers.image.source="https://github.com/Loom-AI-Labs/ai-fabric-course-support-assistant" \
      org.opencontainers.image.revision="${SOURCE_COMMIT}" \
      org.opencontainers.image.created="${BUILD_TIME}"

RUN test -n "${SOURCE_COMMIT}" && test -n "${BUILD_TIME}"
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 --create-home course

WORKDIR /app
RUN mkdir -p /app/data /app/models/embeddings && chown -R course:course /app
COPY --from=build --chown=course:course \
    /workspace/target/ai-fabric-course-support-assistant-0.3.3-course.1-SNAPSHOT.jar /app/app.jar
COPY --from=build --chown=course:course /workspace/models/embeddings /app/models/embeddings

ENV SOURCE_COMMIT="${SOURCE_COMMIT}" \
    SOURCE_BRANCH="${SOURCE_BRANCH}" \
    BUILD_TIME="${BUILD_TIME}" \
    PORT=8080 \
    AI_FABRIC_ONNX_MODEL_PATH=/app/models/embeddings/all-MiniLM-L6-v2.onnx \
    AI_FABRIC_ONNX_TOKENIZER_PATH=/app/models/embeddings/tokenizer.json

USER course
EXPOSE 8080
VOLUME ["/app/data"]
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD curl --fail --silent http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
