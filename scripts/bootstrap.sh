#!/usr/bin/env bash
set -euo pipefail

ORG="${ZOLT_GITHUB_ORG:-zoltsh}"
REPO="${ZOLT_RELEASES_REPO:-releases}"
FULL_REPO="${ORG}/${REPO}"
REMOTE="${ZOLT_RELEASES_REMOTE:-https://github.com/${FULL_REPO}.git}"

fail() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

command -v git >/dev/null 2>&1 || fail "git is required"
command -v gh >/dev/null 2>&1 || fail "GitHub CLI is required: https://cli.github.com/"

gh auth status >/dev/null 2>&1 || fail "authenticate GitHub CLI with: gh auth login"

org_role="$(gh api "user/memberships/orgs/${ORG}" --jq .role 2>/dev/null || true)"
if [ "$org_role" != "admin" ]; then
    fail "the authenticated GitHub account must be an owner of ${ORG}"
fi

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    fail "run this from the initialized release repository"
fi
if [ -n "$(git status --porcelain)" ]; then
    fail "commit or discard local changes before bootstrapping"
fi
if [ "$(git branch --show-current)" != "main" ]; then
    fail "bootstrap must run from the main branch"
fi

scripts/check

if gh repo view "$FULL_REPO" >/dev/null 2>&1; then
    printf 'Repository %s already exists.\n' "$FULL_REPO"
else
    gh repo create "$FULL_REPO" \
        --public \
        --description "Zolt downloads and release channels"
fi

if git remote get-url origin >/dev/null 2>&1; then
    git remote set-url origin "$REMOTE"
else
    git remote add origin "$REMOTE"
fi
git push -u origin main

gh api --method PATCH "repos/${FULL_REPO}" \
    -f default_branch=main \
    -F has_issues=true \
    -F has_projects=false \
    -F has_wiki=false \
    -F allow_squash_merge=true \
    -F allow_merge_commit=false \
    -F allow_rebase_merge=false \
    -F delete_branch_on_merge=true >/dev/null

gh api --method PUT "repos/${FULL_REPO}/actions/permissions/workflow" \
    -f default_workflow_permissions=read \
    -F can_approve_pull_request_reviews=false >/dev/null

for environment in channel-zap channel-preview channel-stable; do
    gh api --method PUT "repos/${FULL_REPO}/environments/${environment}" >/dev/null
done

immutable_enabled="$(gh api "repos/${FULL_REPO}/immutable-releases" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2026-03-10" \
    --jq .enabled 2>/dev/null || true)"
if [ "$immutable_enabled" != "true" ]; then
    gh api --method PUT "repos/${FULL_REPO}/immutable-releases" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2026-03-10" >/dev/null
fi

# Best effort; the owner may need to enable this in the UI depending on plan/settings.
gh api --method PUT "repos/${FULL_REPO}/private-vulnerability-reporting" \
    -H "Accept: application/vnd.github+json" >/dev/null 2>&1 || true

cat <<EOF

Created and pushed ${FULL_REPO}.

Still required in GitHub:
  1. Create teams: release-engineers and release-approvers.
  2. Give normal maintainers read-only access to this repository.
  3. Add the main ruleset from docs/SETUP.md (2 approvals, CODEOWNER, no normal bypass).
  4. Make release-engineers owners of sensitive paths through CODEOWNERS.
  5. Add one trusted reviewer to channel-stable and prevent self-review.
  6. Keep channel-zap and channel-preview at zero reviewers initially.
  7. Confirm immutable releases show as enabled; bootstrap enables them through the GitHub API.
  8. Configure the dispatcher GitHub App and install source-integration/ in zoltsh/zolt.

No publication secret is required for candidate builds.
EOF
