#!/bin/bash
# Standalone build, test, CLI, and web runner for EniWorkshop (with optional Podman MongoDB container).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$ROOT_DIR/target/eniworkshop-0.1.0-SNAPSHOT.jar"

PORT=8080
FORCE_BUILD=false
WITH_MONGO_CONTAINER=false
MONGO_CONTAINER_NAME="eniworkshop-dev-mongo"
CLI_ARGS=()

show_help() {
    cat <<EOF
Usage: $(basename "$0") <command> [options]

Helper script to manage, build, test, and run EniWorkshop in standalone or container-assisted mode.

Commands:
  run              Build (if missing or -b) and start the Spring Boot + Svelte web workbench.
  run-cli          Build (if missing or -b) and run headless CLI mode (forward trailing args).
  build            Compile Svelte frontend and package runnable Spring Boot JAR.
  test             Run offline unit & web integration tests + Checkstyle + PMD/CPD.
  test-containers  Run Podman/Docker Testcontainers persistence tests (*IT.java).
  clean            Remove target/ and frontend build artifacts.

Options:
  -p, --port PORT            Web listening port (default: 8080)
  -b, --build                Force clean Maven package before running
  --with-mongo-container     Start/reuse a Podman mongo:7.0.14 container on port 27017 for local run
  -h, --help                 Show this help message
EOF
}

if [[ $# -lt 1 ]]; then
    show_help
    exit 1
fi

CMD="$1"
shift

while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--port)
            PORT="$2"
            shift 2
            ;;
        -b|--build)
            FORCE_BUILD=true
            shift
            ;;
        --with-mongo-container)
            WITH_MONGO_CONTAINER=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            CLI_ARGS+=("$1")
            shift
            ;;
    esac
done

ensure_mongo_container() {
    local engine="podman"
    if ! command -v podman >/dev/null 2>&1; then
        engine="docker"
    fi
    if ! $engine ps --format "{{.Names}}" | grep -qx "$MONGO_CONTAINER_NAME"; then
        if $engine ps -a --format "{{.Names}}" | grep -qx "$MONGO_CONTAINER_NAME"; then
            $engine start "$MONGO_CONTAINER_NAME" >/dev/null
        else
            $engine run -d --name "$MONGO_CONTAINER_NAME" -p 27017:27017 docker.io/library/mongo:7.0.14 >/dev/null
        fi
    fi
    export ENIWORKSHOP_MONGO_URI="mongodb://localhost:27017/eniworkshop"
    echo "[INFO] Using MongoDB container '$MONGO_CONTAINER_NAME' ($ENIWORKSHOP_MONGO_URI)"
}

cd "$ROOT_DIR"

case "$CMD" in
    build)
        mvn clean verify -DskipTests
        ;;
    test)
        mvn clean verify
        ;;
    test-containers)
        "$SCRIPT_DIR/run-container-tests.sh"
        ;;
    clean)
        mvn clean
        ;;
    run)
        if [[ "$FORCE_BUILD" == "true" ]] || [[ ! -f "$JAR_PATH" ]]; then
            mvn clean package -DskipTests
        fi
        if [[ "$WITH_MONGO_CONTAINER" == "true" ]]; then
            ensure_mongo_container
        fi
        exec java -Dserver.port="$PORT" -jar "$JAR_PATH"
        ;;
    run-cli)
        if [[ "$FORCE_BUILD" == "true" ]] || [[ ! -f "$JAR_PATH" ]]; then
            mvn clean package -DskipTests
        fi
        exec java -jar "$JAR_PATH" --cli "${CLI_ARGS[@]}"
        ;;
    *)
        echo "Unknown command: $CMD" >&2
        show_help
        exit 1
        ;;
esac
