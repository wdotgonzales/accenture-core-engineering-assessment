#!/usr/bin/env bash
# Runs TextScan inside Docker. Accepts the same arguments as the Groovy script.
# Usage: ./textscan-docker.sh -d <directory> -p <pattern> [options]

set -e

IMAGE="textscan"

if ! command -v docker >/dev/null 2>&1; then
    echo "Error: Docker is not installed or not on PATH. See README 'Option A' for a native install." >&2
    exit 1
fi

# Build the image on first use
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "Building Docker image '$IMAGE' (first run only)..."
    docker build -t "$IMAGE" "$(dirname "$0")"
fi

# Stop Git Bash from rewriting the /work path on Windows
export MSYS_NO_PATHCONV=1

# Mount the current folder as /work so -d, --report and --archive resolve against it
docker run --rm -v "$(pwd):/work" "$IMAGE" "$@"
