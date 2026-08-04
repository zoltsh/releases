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
command -v jq >/dev/null 2>&1 || fail "jq is required: https://jqlang.github.io/jq/"

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

gh api --method PUT "repos/${FULL_REPO}/actions/permissions" \
    -F enabled=true \
    -f allowed_actions=selected \
    -F sha_pinning_required=true >/dev/null

gh api --method PUT "repos/${FULL_REPO}/actions/permissions/selected-actions" \
    -F github_owned_allowed=true \
    -F verified_allowed=false \
    -f 'patterns_allowed[]=graalvm/setup-graalvm@*' \
    -f 'patterns_allowed[]=zoltsh/setup-zolt@*' >/dev/null

ruleset_payload="$(mktemp "${TMPDIR:-/tmp}/zolt-releases-main-ruleset.XXXXXX")"
trap 'rm -f "$ruleset_payload"' EXIT
jq -n '
    {
        name: "main",
        target: "branch",
        enforcement: "active",
        bypass_actors: [],
        conditions: {
            ref_name: {
                include: ["refs/heads/main"],
                exclude: []
            }
        },
        rules: [
            {type: "deletion"},
            {type: "non_fast_forward"},
            {
                type: "pull_request",
                parameters: {
                    allowed_merge_methods: ["squash"],
                    dismiss_stale_reviews_on_push: true,
                    require_code_owner_review: true,
                    require_last_push_approval: true,
                    required_approving_review_count: 2,
                    required_review_thread_resolution: true
                }
            },
            {
                type: "required_status_checks",
                parameters: {
                    do_not_enforce_on_create: true,
                    required_status_checks: [
                        {context: "repository", integration_id: 15368}
                    ],
                    strict_required_status_checks_policy: true
                }
            }
        ]
    }
' >"$ruleset_payload"

rulesets="$(gh api "repos/${FULL_REPO}/rulesets?includes_parents=false")"
ruleset_count="$(jq '[.[] | select(.name == "main")] | length' <<<"$rulesets")"
case "$ruleset_count" in
    0)
        gh api --method POST "repos/${FULL_REPO}/rulesets" \
            --input "$ruleset_payload" >/dev/null
        ;;
    1)
        ruleset_id="$(jq -r '.[] | select(.name == "main") | .id' <<<"$rulesets")"
        gh api --method PUT "repos/${FULL_REPO}/rulesets/${ruleset_id}" \
            --input "$ruleset_payload" >/dev/null
        ;;
    *)
        fail "multiple repository rulesets are named main"
        ;;
esac

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
  3. Make release-engineers owners of sensitive paths through CODEOWNERS.
  4. Add one trusted reviewer to channel-stable and prevent self-review.
  5. Keep channel-zap and channel-preview at zero reviewers initially.
  6. Confirm immutable releases show as enabled; bootstrap enables them through the GitHub API.
  7. Configure the dispatcher GitHub App and install source-integration/ in zoltsh/zolt.

No publication secret is required for candidate builds.
EOF
