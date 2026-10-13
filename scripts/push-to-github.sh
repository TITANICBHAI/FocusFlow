#!/usr/bin/env bash

set -euo pipefail

readonly OWNER="TITANICBHAI"
readonly REPO="FocusFlow"
readonly BRANCH="${GITHUB_SYNC_BRANCH:-main}"
readonly API_ROOT="https://api.github.com"
readonly MAX_RETRIES=6
readonly TREE_CHUNK=100
readonly BASE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly DELETE_MISSING="${GITHUB_SYNC_DELETE_MISSING:-false}"
DRY_RUN="${GITHUB_SYNC_DRY_RUN:-false}"

usage() {
  cat <<'EOF'
Usage: bash scripts/push-to-github.sh [--dry-run]

By default, sync changed workspace files to the configured GitHub branch.
--dry-run reads the branch and reports planned changes without writing to GitHub.
Remote-only files are kept unless GITHUB_SYNC_DELETE_MISSING=true is set.
EOF
}

if (( $# > 1 )); then
  usage >&2
  exit 2
fi
case "${1:-}" in
  --dry-run) DRY_RUN=true ;;
  --help|-h) usage; exit 0 ;;
  "") ;;
  *)
    echo "ERROR: Unknown argument: $1" >&2
    usage >&2
    exit 2
    ;;
esac
case "$DRY_RUN" in
  true|1) DRY_RUN=true ;;
  false|0) DRY_RUN=false ;;
  *)
    echo "ERROR: GITHUB_SYNC_DRY_RUN must be true, false, 1, or 0." >&2
    exit 2
    ;;
esac
case "$DELETE_MISSING" in
  true|1) ;;
  false|0) ;;
  *)
    echo "ERROR: GITHUB_SYNC_DELETE_MISSING must be true, false, 1, or 0." >&2
    exit 2
    ;;
esac

if [[ -z "${GITHUB_PERSONAL_ACCESS_TOKEN:-}" ]]; then
  echo "ERROR: GITHUB_PERSONAL_ACCESS_TOKEN is not configured." >&2
  exit 1
fi

for command in curl jq git find sort base64 awk sleep mktemp date; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "ERROR: Required command not found: $command" >&2
    exit 1
  fi
done

if [[ ! "$BRANCH" =~ ^[A-Za-z0-9._/-]+$ ]]; then
  echo "ERROR: Invalid branch name." >&2
  exit 1
fi

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

api_request() {
  local path="$1"
  local method="${2:-GET}"
  local payload="${3:-}"
  local attempt=0
  local status body retry_after delay
  local response_file="$TEMP_DIR/response.json"
  local headers_file="$TEMP_DIR/response.headers"

  while (( attempt <= MAX_RETRIES )); do
    : > "$headers_file"
    if [[ -n "$payload" ]]; then
      status="$(curl -sS -D "$headers_file" -o "$response_file" -w '%{http_code}' \
        -X "$method" \
        --connect-timeout 15 --max-time 120 \
        -H "Authorization: Bearer $GITHUB_PERSONAL_ACCESS_TOKEN" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        -H "Content-Type: application/json" \
        -H "User-Agent: focusflow-shell-sync" \
        --data-binary "$payload" \
        "$API_ROOT$path")" || status="000"
    else
      status="$(curl -sS -D "$headers_file" -o "$response_file" -w '%{http_code}' \
        -X "$method" \
        --connect-timeout 15 --max-time 120 \
        -H "Authorization: Bearer $GITHUB_PERSONAL_ACCESS_TOKEN" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        -H "User-Agent: focusflow-shell-sync" \
        "$API_ROOT$path")" || status="000"
    fi

    if [[ "$status" =~ ^2[0-9][0-9]$ ]]; then
      cat "$response_file"
      return 0
    fi

    body="$(cat "$response_file" 2>/dev/null || true)"
    if [[ "$status" == "429" || "$status" == "502" || "$status" == "503" || "$status" == "504" ||
          ( "$status" == "403" && "$body" =~ [Rr]ate.limit|secondary.limit|abuse ) ||
          "$status" == "000" ]]; then
      if (( attempt < MAX_RETRIES )); then
        retry_after="$(awk 'tolower($1) == "retry-after:" { gsub("\r", "", $2); print $2; exit }' "$headers_file")"
        if [[ "$retry_after" =~ ^[0-9]+$ ]]; then
          delay="$retry_after"
        else
          delay=$(( 1 << attempt ))
          (( delay > 60 )) && delay=60
        fi
        echo "  Retrying GitHub request ($status) in ${delay}s (attempt $((attempt + 1))/$MAX_RETRIES)..." >&2
        sleep "$delay"
        ((attempt += 1))
        continue
      fi
    fi

    echo "ERROR: GitHub $method $path returned HTTP $status." >&2
    if [[ "$status" == "401" ]]; then
      echo "Check that the GitHub token is valid and has not expired." >&2
    elif [[ "$status" == "403" ]]; then
      echo "Check that the token can access $OWNER/$REPO and has Contents read/write permission." >&2
    elif [[ "$status" == "404" ]]; then
      echo "Check that $OWNER/$REPO and branch '$BRANCH' exist and are accessible to this token." >&2
    fi
    if [[ -n "$body" ]]; then
      printf '%s' "$body" | head -c 400 >&2
      printf '\n' >&2
    fi
    return 1
  done

  echo "ERROR: GitHub request exhausted retries: $method $path" >&2
  return 1
}

should_exclude() {
  local relative_path="$1"
  case "$relative_path" in
    pnpm-workspace.yaml|tsconfig.base.json|.npmrc|.replitignore|scripts/src/hello.ts)
      return 0
      ;;
    node_modules/*|*/node_modules/*|attached_assets/*|*/attached_assets/*|.conversation/*|*/.conversation/*|.agents/*|*/.agents/*)
      return 0
      ;;
    .env|.env.*|*/.env|*/.env.*|__pycache__/*|*/__pycache__/*|.pytest_cache/*|*/.pytest_cache/*)
      return 0
      ;;
    .git/*|*/.git/*|.cache/*|*/.cache/*|.local/*|*/.local/*|.expo/*|*/.expo/*|.gradle/*|*/.gradle/*)
      return 0
      ;;
    lib/*|dist/*|*/dist/*|tmp/*|*/tmp/*|out-tsc/*|*/out-tsc/*|build/*|*/build/*)
      return 0
      ;;
    *.keystore|*.jks|*credentials.json|*.DS_Store|*/.DS_Store|*/Thumbs.db|*.tsbuildinfo|tbtechs-release.keystore)
      return 0
      ;;
    */android/*|*/dist/*|dist/*|*/tmp/*|tmp/*|*/out-tsc/*|out-tsc/*)
      return 0
      ;;
  esac
  return 1
}

echo "== FocusFlow GitHub sync (Bash + GitHub REST API) =="
echo "Repository: https://github.com/$OWNER/$REPO"
echo "Branch:     $BRANCH"
echo "Local Git history is not used; the new commit will descend from the current GitHub branch."

encoded_branch="$(jq -rn --arg branch "$BRANCH" '$branch | @uri')"
ref_response="$(api_request "/repos/$OWNER/$REPO/git/ref/heads/$encoded_branch")"
parent_sha="$(jq -er '.object.sha' <<< "$ref_response")"
commit_response="$(api_request "/repos/$OWNER/$REPO/git/commits/$parent_sha")"
base_tree_sha="$(jq -er '.tree.sha' <<< "$commit_response")"
remote_tree_response="$(api_request "/repos/$OWNER/$REPO/git/trees/$base_tree_sha?recursive=1")"

if [[ "$(jq -r '.truncated // false' <<< "$remote_tree_response")" == "true" ]]; then
  echo "ERROR: GitHub returned a truncated tree; refusing to sync from incomplete remote state." >&2
  exit 1
fi

declare -A remote_sha_by_path=()
declare -A remote_mode_by_path=()
while IFS= read -r encoded_entry; do
  entry="$(printf '%s' "$encoded_entry" | base64 -d)"
  remote_path="$(jq -er '.path' <<< "$entry")"
  remote_sha_by_path["$remote_path"]="$(jq -er '.sha' <<< "$entry")"
  remote_mode_by_path["$remote_path"]="$(jq -er '.mode' <<< "$entry")"
done < <(jq -r '.tree[] | select(.type == "blob") | @base64' <<< "$remote_tree_response")

tree_entries_file="$TEMP_DIR/tree-entries.jsonl"
deletions_preview_file="$TEMP_DIR/deletions-preview.txt"
changes_preview_file="$TEMP_DIR/changes-preview.txt"
: > "$tree_entries_file"
: > "$deletions_preview_file"
: > "$changes_preview_file"
declare -A local_path_present=()
local_count=0
changed_count=0
deletion_count=0

while IFS= read -r -d '' file_path; do
  relative_path="${file_path#"$BASE"/}"
  if should_exclude "$relative_path"; then
    continue
  fi

  ((local_count += 1))
  local_path_present["$relative_path"]=1
  local_blob_sha="$(git hash-object --stdin < "$file_path")"
  file_mode="100644"
  [[ -x "$file_path" ]] && file_mode="100755"
  remote_blob_sha="${remote_sha_by_path[$relative_path]:-}"
  remote_mode="${remote_mode_by_path[$relative_path]:-}"
  if [[ "$remote_blob_sha" == "$local_blob_sha" && "$remote_mode" == "$file_mode" ]]; then
    continue
  fi

  ((changed_count += 1))
  if [[ "$DRY_RUN" == true ]]; then
    change_kind="ADD"
    [[ -n "$remote_blob_sha" ]] && change_kind="UPDATE"
    printf '%s\t%s\n' "$change_kind" "$relative_path" >> "$changes_preview_file"
    continue
  fi

  if [[ "$remote_blob_sha" == "$local_blob_sha" ]]; then
    blob_sha="$local_blob_sha"
  else
    content_base64="$(base64 -w 0 < "$file_path")"
    blob_payload="$(jq -cn --arg content "$content_base64" '{content: $content, encoding: "base64"}')"
    blob_response="$(api_request "/repos/$OWNER/$REPO/git/blobs" POST "$blob_payload")"
    blob_sha="$(jq -er '.sha' <<< "$blob_response")"
  fi

  jq -cn --arg path "$relative_path" --arg mode "$file_mode" --arg sha "$blob_sha" \
    '{path: $path, mode: $mode, type: "blob", sha: $sha}' >> "$tree_entries_file"

  if (( changed_count % 25 == 0 )); then
    echo "  Uploaded $changed_count changed files..."
  fi
done < <(
  find "$BASE" \
      -type d \( -name .git -o -name node_modules -o -name attached_assets -o -name .conversation \
        -o -name .agents -o -name __pycache__ -o -name .pytest_cache \
      -o -name .cache -o -name .local -o -name .expo -o -name .gradle -o -name dist \
      -o -name tmp -o -name out-tsc -o -name lib -o -name build \) -prune -o \
    -type f -print0 | sort -z
)

for remote_path in "${!remote_sha_by_path[@]}"; do
  if [[ "$DELETE_MISSING" != "true" && "$DELETE_MISSING" != "1" ]]; then
    break
  fi
  if [[ -n "${local_path_present[$remote_path]+present}" ]] || should_exclude "$remote_path"; then
    continue
  fi

  jq -cn --arg path "$remote_path" \
    '{path: $path, mode: "100644", type: "blob", sha: null}' >> "$tree_entries_file"
  printf '%s\n' "$remote_path" >> "$deletions_preview_file"
  ((deletion_count += 1))
done

sync_count=$(( changed_count + deletion_count ))
echo "Scanned $local_count eligible files; $changed_count need adding/updating."
echo "Remote-only files to delete from the new snapshot: $deletion_count."
if (( deletion_count > 0 )); then
  echo "Deletion preview (up to 20 paths):"
  head -n 20 "$deletions_preview_file" | sed 's/^/  - /'
fi
if [[ "$DELETE_MISSING" == "true" || "$DELETE_MISSING" == "1" ]]; then
  echo "Remote-only managed files are eligible for deletion; existing commits are not rewritten."
else
  echo "Remote-only files are being kept. Set GITHUB_SYNC_DELETE_MISSING=true to opt in to removals."
fi

if [[ "$DRY_RUN" == true ]]; then
  if [[ -s "$changes_preview_file" ]]; then
    echo "Planned additions/updates (up to 30 paths):"
    head -n 30 "$changes_preview_file" | while IFS=$'\t' read -r action path; do
      printf '  - %s %s\n' "$action" "$path"
    done
  fi
  echo "DRY RUN: no GitHub blobs, trees, commits, or refs were written."
  exit 0
fi

if (( sync_count == 0 )); then
  echo "GitHub already matches the eligible workspace files; no commit needed."
  exit 0
fi

current_tree_sha="$base_tree_sha"
offset=0
while (( offset < sync_count )); do
  chunk="$(jq -s --argjson offset "$offset" --argjson length "$TREE_CHUNK" \
    '.[ $offset : ($offset + $length) ]' "$tree_entries_file")"
  tree_payload="$(jq -cn --arg base_tree "$current_tree_sha" --argjson entries "$chunk" \
    '{base_tree: $base_tree, tree: $entries}')"
  tree_response="$(api_request "/repos/$OWNER/$REPO/git/trees" POST "$tree_payload")"
  current_tree_sha="$(jq -er '.sha' <<< "$tree_response")"
  offset=$(( offset + TREE_CHUNK ))
  (( offset > sync_count )) && offset="$sync_count"
  echo "  Layered $offset/$sync_count additions, updates, and deletions..."
done

commit_message="${GITHUB_SYNC_COMMIT_MESSAGE:-Sync FocusFlow workspace — $(date -u +%Y-%m-%dT%H:%M:%SZ)}"
commit_payload="$(jq -cn --arg message "$commit_message" --arg tree "$current_tree_sha" --arg parent "$parent_sha" \
  '{message: $message, tree: $tree, parents: [$parent]}')"
new_commit_response="$(api_request "/repos/$OWNER/$REPO/git/commits" POST "$commit_payload")"
new_commit_sha="$(jq -er '.sha' <<< "$new_commit_response")"
actual_parent="$(jq -er '.parents[0].sha' <<< "$new_commit_response")"

if [[ "$actual_parent" != "$parent_sha" ]]; then
  echo "ERROR: New commit does not have the previously fetched GitHub commit as its parent." >&2
  exit 1
fi

ref_payload="$(jq -cn --arg sha "$new_commit_sha" '{sha: $sha, force: false}')"
api_request "/repos/$OWNER/$REPO/git/refs/heads/$encoded_branch" PATCH "$ref_payload" >/dev/null

verified_ref="$(api_request "/repos/$OWNER/$REPO/git/ref/heads/$encoded_branch")"
verified_sha="$(jq -er '.object.sha' <<< "$verified_ref")"
if [[ "$verified_sha" != "$new_commit_sha" ]]; then
  echo "ERROR: GitHub branch verification failed after the update." >&2
  exit 1
fi

echo
echo "Push completed without force."
echo "Commit: $new_commit_sha"
echo "Parent: $parent_sha"
echo "Repo:   https://github.com/$OWNER/$REPO"