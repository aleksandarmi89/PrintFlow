#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/printflow/app"
SERVICE="printflow"
JAR_PATTERN="target/printflow-saas-*.jar"

JAR_FILE=$(ls -1 ${JAR_PATTERN} 2>/dev/null | head -n 1 || true)
if [[ -z "${JAR_FILE}" ]]; then
  echo "Jar not found (${JAR_PATTERN}). Build first: ./mvnw -DskipTests clean package"
  exit 1
fi

sudo mkdir -p "${APP_DIR}"
sudo cp -f "${JAR_FILE}" "${APP_DIR}/printflow.new.jar"

if [[ -f "${APP_DIR}/printflow.jar" ]]; then
  sudo cp -f "${APP_DIR}/printflow.jar" "${APP_DIR}/printflow.prev.jar"
fi

sudo mv -f "${APP_DIR}/printflow.new.jar" "${APP_DIR}/printflow.jar"
sudo chown printflow:printflow "${APP_DIR}/printflow.jar"

sudo systemctl daemon-reload
sudo systemctl restart "${SERVICE}"
sudo systemctl --no-pager --full status "${SERVICE}"

echo "Deploy done: ${JAR_FILE}"
