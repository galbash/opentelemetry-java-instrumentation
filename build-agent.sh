#!/usr/bin/env bash

set -e

AGENT_OUT="$HOME/IdeaProjects/opentelemetry-java-instrumentation/javaagent/build/libs/opentelemetry-javaagent.jar"
AGENT_DEST="$HOME/workspaces/observability/opentelemetry-nifi/opentelemetry-nifi-env/nifi_data/conf/opentelemetry-javaagent.jar"
CONTAINER_PROJECT="$HOME/workspaces/observability/opentelemetry-nifi/opentelemetry-nifi-env"

echo "[INFO] Building javaagent..."
./gradlew assemble

echo "[INFO] Moving agent to NIFI container"
mv "$AGENT_OUT" "$AGENT_DEST"

echo "[INFO] Restarting NIFI container"
docker compose --project-directory "$CONTAINER_PROJECT" restart nifi

echo "[INFO] Waiting for the container to come up..."
until curl --output /dev/null --silent --head --fail http://localhost:8080/nifi; do
    printf '.'
    sleep 5
done

echo ""
osascript -e 'display notification "Done." with title "NIFI is ready"'

