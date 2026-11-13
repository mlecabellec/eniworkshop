#!/bin/bash
# Manages local OCI / Podman containers for EniWorkshop, MongoDB, and optional Quasar OPC-UA.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/podman-compose.yml"

detect_compose() {
    if command -v podman-compose >/dev/null 2>&1; then
        echo "podman-compose"
    elif command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
        echo "podman compose"
    elif command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
        echo "docker compose"
    else
        echo "ERROR: Neither podman-compose, 'podman compose', nor 'docker compose' is available." >&2
        exit 1
    fi
}

show_help() {
    cat <<EOF
Usage: $(basename "$0") <command> [options]

Podman / OCI stack helper for EniWorkshop + MongoDB (+ optional Quasar OPC-UA).

Commands:
  build                 Build localhost/eniworkshop:latest using Containerfile
  up [--with-quasar]    Start MongoDB + EniWorkshop in background (plus Quasar if --with-quasar)
  down                  Stop and remove stack containers
  logs [service]        Follow container logs
  status                Show running container status
  cli <args...>         Execute eniworkshop headless CLI inside container (mount pwd as /work)
  clean                 Stop stack and remove MongoDB data volume
EOF
}

if [[ $# -lt 1 ]] || [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]]; then
    show_help
    exit 0
fi

CMD="$1"
shift
COMPOSE_BIN="$(detect_compose)"

case "$CMD" in
    build)
        podman build -t localhost/eniworkshop:latest -f "$ROOT_DIR/Containerfile" "$ROOT_DIR"
        ;;
    up)
        if [[ "${1:-}" == "--with-quasar" ]]; then
            $COMPOSE_BIN -f "$COMPOSE_FILE" --profile quasar up -d --build
        else
            $COMPOSE_BIN -f "$COMPOSE_FILE" up -d --build
        fi
        ;;
    down)
        $COMPOSE_BIN -f "$COMPOSE_FILE" --profile quasar down
        ;;
    logs)
        $COMPOSE_BIN -f "$COMPOSE_FILE" logs -f "${1:-eniworkshop}"
        ;;
    status)
        podman ps --filter "name=eniworkshop" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
        ;;
    cli)
        podman run --rm -v "$(pwd):/work:Z" -w /work localhost/eniworkshop:latest --cli "$@"
        ;;
    clean)
        $COMPOSE_BIN -f "$COMPOSE_FILE" --profile quasar down -v
        ;;
    *)
        echo "Unknown command: $CMD" >&2
        show_help
        exit 1
        ;;
esac
