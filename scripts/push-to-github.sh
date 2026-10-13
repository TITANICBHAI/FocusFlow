#!/usr/bin/env bash

set -euo pipefail

if [[ -z "${GITHUB_PERSONAL_ACCESS_TOKEN:-}" ]]; then
  echo "ERROR: GITHUB_PERSONAL_ACCESS_TOKEN is not configured." >&2
  exit 1
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# The sync script uses the target GitHub branch as the new commit's parent.
# It does not depend on this workspace's .git history and never force-pushes.
exec node "$SCRIPT_DIR/github-push.mjs" "$@"