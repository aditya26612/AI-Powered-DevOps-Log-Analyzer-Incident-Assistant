#!/usr/bin/env bash
# ==============================================================================
# DevInsight - One-Click Launcher for Bash / Linux / macOS / Git Bash
# ==============================================================================

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." >/dev/null 2>&1 && pwd)"

export DB_PASSWORD="${DB_PASSWORD:-postgres}"
export JWT_SECRET="${JWT_SECRET:-devinsight_super_secret_jwt_key_2026_at_least_32_characters_long!}"
export INTERNAL_API_KEY="${INTERNAL_API_KEY:-devinsight_internal_ml_key_secret_12345}"
export USER_SERVICE_URL="${USER_SERVICE_URL:-http://localhost:8081}"
export LOG_SERVICE_URL="${LOG_SERVICE_URL:-http://localhost:8082}"
export ML_SERVICE_URL="${ML_SERVICE_URL:-http://localhost:8000}"
export LLM_SERVICE_URL="${LLM_SERVICE_URL:-http://localhost:8084}"

echo "====================================================================="
echo "            DEVINSIGHT — BASH ORCHESTRATOR LAUNCHER                  "
echo "====================================================================="

echo "Starting ML Pipeline on port 8000..."
(cd "$DIR/ml-pipeline" && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000) &

echo "Starting User Service on port 8081..."
(cd "$DIR" && mvn -f user-service/pom.xml spring-boot:run) &

echo "Starting Log Layer on port 8082..."
(cd "$DIR" && mvn -f log-layer/pom.xml spring-boot:run) &

echo "Starting LLM Service on port 8084..."
(cd "$DIR" && mvn -f llm-service/pom.xml spring-boot:run) &

echo "Starting API Gateway on port 8080..."
(cd "$DIR" && mvn -f api-gateway/pom.xml spring-boot:run) &

echo "Starting Frontend on port 5173..."
(cd "$DIR/frontend" && npm run dev) &

echo "All services started in background."
echo "Frontend: http://localhost:5173"
echo "Live Inspector: http://localhost:5173/analyzer"
wait

