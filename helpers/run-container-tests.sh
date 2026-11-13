#!/bin/bash
# Runs @Tag("container") integration tests using Testcontainers backed by rootless Podman or Docker.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

SERVICE_PID=""
cleanup() {
    if [[ -n "$SERVICE_PID" ]]; then
        kill "$SERVICE_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

wait_for_socket() {
    local sock="$1"
    for _ in $(seq 1 25); do
        if [[ -S "$sock" ]]; then
            return 0
        fi
        sleep 0.2
    done
    return 1
}

ensure_container_socket() {
    if [[ -n "${DOCKER_HOST:-}" ]]; then
        if [[ "${DOCKER_HOST}" != unix://* ]]; then
            return 0
        fi
        local configured_sock="${DOCKER_HOST#unix://}"
        if [[ -S "$configured_sock" ]]; then
            export TESTCONTAINERS_RYUK_DISABLED="${TESTCONTAINERS_RYUK_DISABLED:-true}"
            return 0
        fi
        echo "[INFO] Configured DOCKER_HOST socket missing ($configured_sock); activating service..."
    fi

    local podman_sock="/run/user/$(id -u)/podman/podman.sock"
    if [[ "${DOCKER_HOST:-}" == unix://* ]]; then
        podman_sock="${DOCKER_HOST#unix://}"
    fi

    if command -v podman >/dev/null 2>&1; then
        if [[ ! -S "$podman_sock" ]] && command -v systemctl >/dev/null 2>&1; then
            systemctl --user start podman.socket >/dev/null 2>&1 || true
        fi
        if [[ ! -S "$podman_sock" ]]; then
            mkdir -p "$(dirname "$podman_sock")"
            podman system service --time=300 "unix://$podman_sock" >/dev/null 2>&1 &
            SERVICE_PID="$!"
            wait_for_socket "$podman_sock" || true
        fi
        if [[ -S "$podman_sock" ]]; then
            export DOCKER_HOST="unix://$podman_sock"
            # Ryuk often requires privileged mount flags under rootless Podman; JVM shutdown hook cleans up containers
            export TESTCONTAINERS_RYUK_DISABLED="${TESTCONTAINERS_RYUK_DISABLED:-true}"
            echo "[INFO] Using Podman socket at $DOCKER_HOST (TESTCONTAINERS_RYUK_DISABLED=$TESTCONTAINERS_RYUK_DISABLED)"
            return 0
        fi
    fi

    if [[ -S "/var/run/docker.sock" ]]; then
        export DOCKER_HOST="unix:///var/run/docker.sock"
        echo "[INFO] Using Docker socket at $DOCKER_HOST"
        return 0
    fi

    echo "[ERROR] No active Podman or Docker API socket found for Testcontainers." >&2
    exit 1
}

ensure_container_socket
mvn verify -Pcontainer-tests -Dskip.frontend=true "$@"
