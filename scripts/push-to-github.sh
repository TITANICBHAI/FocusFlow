#!/usr/bin/env bash

set -euo pipefail

readonly OWNER="TITANICBHAI"
readonly REPO="FocusFlow"
readonly BRANCH="${GITHUB_SYNC_BRANCH:-main}"
readonly API_ROOT="https://api.github.com"
readonly MAX_RETRIES=6
readonly TREE_CHUNK=100
readonly BASE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${GITHUB_PERSONAL_ACCESS_TOKEN:-}" ]]; then
  echo "ERROR: GITHUB_PERSONAL_ACCESS_TOKEN is not configured." >&2
  exit 1
fi

for command in curl jq git find sort base64 stat; do
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
        -H "Authorization: Bearer $GITHUB_PERSONAL_ACCESS_TOKEN" \
        -H "Accept: application/vnd.github+json" \
        -H "Content-Type: application/json" \
        -H "User-Agent: focusflow-shell-sync" \
        --data-binary "$payload" \
        "$API_ROOT$path")" || status="000"
    else
      status="$(curl -sS -D "$headers_file" -o "$response_file" -w '%{http_code}' \
        -X "$method" \
        -H "Authorization: Bearer $GITHUB_PERSONAL_ACCESS_TOKEN" \
        -H "Accept: application/vnd.github+json" \
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
while IFS= read -r encoded_entry; do
  entry="$(printf '%s' "$encoded_entry" | base64 -d)"
  remote_path="$(jq -er '.path' <<< "$entry")"
  remote_sha_by_path["$remote_path"]="$(jq -er '.sha' <<< "$entry")"
done < <(jq -r '.tree[] | select(.type == "blob") | @base64' <<< "$remote_tree_response")

tree_entries_file="$TEMP_DIR/tree-entries.jsonl"
: > "$tree_entries_file"
local_count=0
changed_count=0

while IFS= read -r -d '' file_path; do
  relative_path="${file_path#"$BASE"/}"
  if should_exclude "$relative_path"; then
    continue
  fi

  ((local_count += 1))
  local_blob_sha="$(git hash-object --stdin < "$file_path")"
  if [[ "${remote_sha_by_path[$relative_path]:-}" == "$local_blob_sha" ]]; then
    continue
  fi

  content_base64="$(base64 -w 0 < "$file_path")"
  blob_payload="$(jq -cn --arg content "$content_base64" '{content: $content, encoding: "base64"}')"
  blob_response="$(api_request "/repos/$OWNER/$REPO/git/blobs" POST "$blob_payload")"
  blob_sha="$(jq -er '.sha' <<< "$blob_response")"
  file_mode="100644"
  [[ -x "$file_path" ]] && file_mode="100755"
  jq -cn --arg path "$relative_path" --arg mode "$file_mode" --arg sha "$blob_sha" \
    '{path: $path, mode: $mode, type: "blob", sha: $sha}' >> "$tree_entries_file"
  ((changed_count += 1))

  if (( changed_count % 25 == 0 )); then
    echo "  Uploaded $changed_count changed files..."
  fi
done < <(
  find "$BASE" \
    -type d \( -name .git -o -name node_modules -o -name attached_assets -o -name .conversation \
      -o -name .cache -o -name .local -o -name .expo -o -name .gradle -o -name dist \
      -o -name tmp -o -name out-tsc -o -name lib -o -name build \) -prune -o \
    -type f -print0 | sort -z
)

echo "Scanned $local_count eligible files; $changed_count need to be added or updated."
echo "Remote-only files will be preserved; this sync does not delete repository files."

if (( changed_count == 0 )); then
  echo "GitHub already matches the eligible workspace files; no commit needed."
  exit 0
fi

current_tree_sha="$base_tree_sha"
offset=0
while (( offset < changed_count )); do
  chunk="$(jq -s --argjson offset "$offset" --argjson length "$TREE_CHUNK" \
    '.[ $offset : ($offset + $length) ]' "$tree_entries_file")"
  tree_payload="$(jq -cn --arg base_tree "$current_tree_sha" --argjson entries "$chunk" \
    '{base_tree: $base_tree, tree: $entries}')"
  tree_response="$(api_request "/repos/$OWNER/$REPO/git/trees" POST "$tree_payload")"
  current_tree_sha="$(jq -er '.sha' <<< "$tree_response")"
  offset=$(( offset + TREE_CHUNK ))
  (( offset > changed_count )) && offset="$changed_count"
  echo "  Layered $offset/$changed_count changed files..."
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