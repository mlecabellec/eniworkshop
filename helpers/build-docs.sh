#!/bin/bash
# Documentation helper wrapping mkdocs-kit (HTML site, PDF manual, Man page).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOC_DIR="$ROOT_DIR/doc"

find_mkdocs_kit() {
    if [[ -x "${MKDOCS_KIT_BIN:-}" ]]; then
        echo "$MKDOCS_KIT_BIN"
    elif command -v mkdocs-kit >/dev/null 2>&1; then
        command -v mkdocs-kit
    elif [[ -x "$ROOT_DIR/../mkdocs-kit/mkdocs-kit" ]]; then
        echo "$ROOT_DIR/../mkdocs-kit/mkdocs-kit"
    else
        echo "ERROR: mkdocs-kit executable not found. Set MKDOCS_KIT_BIN or install mkdocs-kit in PATH." >&2
        exit 1
    fi
}

CMD="${1:-build}"
MKDOCS_BIN="$(find_mkdocs_kit)"
cd "$DOC_DIR"

case "$CMD" in
    build)
        "$MKDOCS_BIN" build
        ;;
    serve)
        "$MKDOCS_BIN" serve
        ;;
    clean)
        rm -rf "$DOC_DIR/site" "$DOC_DIR/documentation.pdf" "$DOC_DIR/man"
        ;;
    *)
        echo "Usage: $(basename "$0") [build|serve|clean]" >&2
        exit 1
        ;;
esac
