#!/usr/bin/env bash
# Wrapper Bash para infra/seed/reset_seed.py
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if command -v python3 >/dev/null 2>&1; then
    PY=python3
elif command -v python >/dev/null 2>&1; then
    PY=python
else
    echo "ERROR: no se encontro python3 ni python en PATH" >&2
    exit 1
fi

exec "$PY" "$DIR/reset_seed.py" "$@"
