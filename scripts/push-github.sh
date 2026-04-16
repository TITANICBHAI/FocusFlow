#!/usr/bin/env bash
# Commit all pending changes and push to GitHub (focusflow remote).
# Triggered by the "Push to GitHub" Replit workflow, or run directly:
#   bash scripts/push-github.sh

set -e

REPO="/home/runner/workspace"
REMOTE="focusflow"
BRANCH="main"

echo "──────────────────────────────────────────"
echo "  FocusFlow → GitHub push"
echo "──────────────────────────────────────────"

cd "$REPO"

# ── Step 1: Commit any uncommitted changes ────────────────────────────────────
# Replit's checkpoint system saves files to disk but does NOT create local git
# commits. We commit everything here so there is always something to push.
git add -A

if git diff --cached --quiet; then
  echo "ℹ  No staged changes — working tree is clean."
else
  TIMESTAMP=$(date -u '+%Y-%m-%d %H:%M UTC')
  git commit -m "chore: Replit agent changes – $TIMESTAMP"
  echo "✅ Committed working-tree changes."
fi

# ── Step 2: Sync with remote ──────────────────────────────────────────────────
echo "▶ Fetching remote state..."
git fetch "$REMOTE" "$BRANCH"

LOCAL=$(git rev-parse HEAD)
REMOTE_SHA=$(git rev-parse "$REMOTE/$BRANCH")

if [ "$LOCAL" = "$REMOTE_SHA" ]; then
  echo "✅ Already up-to-date. Nothing to push."
  exit 0
fi

BEHIND=$(git rev-list HEAD.."$REMOTE/$BRANCH" --count)
if [ "$BEHIND" -gt 0 ]; then
  echo "⚠  Remote is $BEHIND commit(s) ahead — rebasing local on top..."
  git rebase "$REMOTE/$BRANCH"
fi

# ── Step 3: Push ──────────────────────────────────────────────────────────────
AHEAD=$(git rev-list "$REMOTE/$BRANCH"..HEAD --count)
echo "▶ Pushing $AHEAD commit(s) to $REMOTE/$BRANCH..."
git push "$REMOTE" "HEAD:$BRANCH"

echo ""
echo "✅ Push complete! GitHub Actions build starting automatically."
echo "   https://github.com/TITANICBHAI/FocusFlow/actions"

# Keep alive briefly so the workflow log is readable
sleep 5
