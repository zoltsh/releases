#!/usr/bin/env bash
set -euo pipefail

ORG="${ZOLT_GITHUB_ORG:-zoltsh}"
REPO="${ZOLT_RELEASES_REPO:-releases}"
FULL_REPO="${ORG}/${REPO}"
REMOTE="${ZOLT_RELEASES_REMOTE:-https://github.com/${FULL_REPO}.git}"
RECOVERY_REVIEWER="${ZOLT_RECOVERY_REVIEWER:-}"

fail() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

configure_environment_ref() {
    local environment="$1"
    local ref_type="$2"
    local ref_pattern="$3"
    local endpoint="repos/${FULL_REPO}/environments/${environment}"
    local environment_state policies

    environment_state="$(gh api "$endpoint" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2026-03-10" 2>/dev/null || true)"
    if ! jq -e '
        .deployment_branch_policy.protected_branches == false and
        .deployment_branch_policy.custom_branch_policies == true
    ' <<<"$environment_state" >/dev/null 2>&1; then
        gh api --method PUT "$endpoint" \
            -H "Accept: application/vnd.github+json" \
            -H "X-GitHub-Api-Version: 2026-03-10" \
            -F 'deployment_branch_policy[protected_branches]=false' \
            -F 'deployment_branch_policy[custom_branch_policies]=true' >/dev/null
    fi

    policies="$(gh api "${endpoint}/deployment-branch-policies?per_page=100" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2026-03-10")"

    while IFS= read -r policy_id; do
        gh api --method DELETE "${endpoint}/deployment-branch-policies/${policy_id}" \
            -H "Accept: application/vnd.github+json" \
            -H "X-GitHub-Api-Version: 2026-03-10" >/dev/null
    done < <(jq -r \
        --arg name "$ref_pattern" \
        --arg type "$ref_type" \
        '.branch_policies[] | select(.name != $name or .type != $type) | .id' \
        <<<"$policies")

    if ! jq -e \
        --arg name "$ref_pattern" \
        --arg type "$ref_type" \
        'any(.branch_policies[]; .name == $name and .type == $type)' \
        <<<"$policies" >/dev/null; then
        gh api --method POST "${endpoint}/deployment-branch-policies" \
            -H "Accept: application/vnd.github+json" \
            -H "X-GitHub-Api-Version: 2026-03-10" \
            -f name="$ref_pattern" \
            -f type="$ref_type" >/dev/null
    fi

    policies="$(gh api "${endpoint}/deployment-branch-policies?per_page=100" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2026-03-10")"
    jq -e \
        --arg name "$ref_pattern" \
        --arg type "$ref_type" \
        '.total_count == 1 and
         .branch_policies[0].name == $name and
         .branch_policies[0].type == $type' \
        <<<"$policies" >/dev/null \
        || fail "${environment} must allow only ${ref_type} ${ref_pattern}"
}

configure_recovery_protection() {
    local environment="channel-zap-recovery"
    local endpoint="repos/${FULL_REPO}/environments/${environment}"
    local reviewer_id protection_payload environment_state

    [ -n "$RECOVERY_REVIEWER" ] \
        || fail "set ZOLT_RECOVERY_REVIEWER to the trusted recovery approver login"
    reviewer_id="$(gh api "users/${RECOVERY_REVIEWER}" --jq .id 2>/dev/null || true)"
    [ -n "$reviewer_id" ] \
        || fail "recovery reviewer ${RECOVERY_REVIEWER} is not a GitHub user"

    protection_payload="$(jq -n \
        --argjson reviewer_id "$reviewer_id" '
        {
            wait_timer: 0,
            prevent_self_review: true,
            can_admins_bypass: false,
            reviewers: [{type: "User", id: $reviewer_id}],
            deployment_branch_policy: {
                protected_branches: false,
                custom_branch_policies: true
            }
        }
    ')"
    gh api --method PUT "$endpoint" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2026-03-10" \
        --input - <<<"$protection_payload" >/dev/null

    environment_state="$(gh api "$endpoint" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2026-03-10")"
    jq -e \
        --arg login "$RECOVERY_REVIEWER" '
        .can_admins_bypass == false and
        any(.protection_rules[];
            .type == "required_reviewers" and
            .prevent_self_review == true and
            (.reviewers | length) == 1 and
            .reviewers[0].type == "User" and
            .reviewers[0].reviewer.login == $login)
    ' <<<"$environment_state" >/dev/null \
        || fail "channel-zap-recovery must require only ${RECOVERY_REVIEWER}, prevent self-review, and disallow administrator bypass"
}

configure_ruleset() {
    local name="$1"
    local payload="$2"
    local rulesets ruleset_count ruleset_id

    rulesets="$(gh api "repos/${FULL_REPO}/rulesets?includes_parents=false")"
    ruleset_count="$(jq --arg name "$name" '[.[] | select(.name == $name)] | length' \
        <<<"$rulesets")"
    case "$ruleset_count" in
        0)
            gh api --method POST "repos/${FULL_REPO}/rulesets" \
                --input "$payload" >/dev/null
            ;;
        1)
            ruleset_id="$(jq -r --arg name "$name" \
                '.[] | select(.name == $name) | .id' <<<"$rulesets")"
            gh api --method PUT "repos/${FULL_REPO}/rulesets/${ruleset_id}" \
                --input "$payload" >/dev/null
            ;;
        *)
            fail "multiple repository rulesets are named ${name}"
            ;;
    esac
}

command -v git >/dev/null 2>&1 || fail "git is required"
command -v gh >/dev/null 2>&1 || fail "GitHub CLI is required: https://cli.github.com/"
command -v jq >/dev/null 2>&1 || fail "jq is required: https://jqlang.github.io/jq/"

gh auth status >/dev/null 2>&1 || fail "authenticate GitHub CLI with: gh auth login"

if [ -z "$RECOVERY_REVIEWER" ]; then
    RECOVERY_REVIEWER="$(gh api user --jq .login)"
fi

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
immutable_release_tags_payload="$(mktemp \
    "${TMPDIR:-/tmp}/zolt-releases-immutable-tags-ruleset.XXXXXX")"
trap 'rm -f "$ruleset_payload" "$immutable_release_tags_payload"' EXIT
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
                    require_code_owner_review: false,
                    require_last_push_approval: false,
                    required_approving_review_count: 0,
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

configure_ruleset main "$ruleset_payload"

jq -n '
    {
        name: "immutable release tags",
        target: "tag",
        enforcement: "active",
        bypass_actors: [],
        conditions: {
            ref_name: {
                include: [
                    "refs/tags/zolt-zap-*",
                    "refs/tags/zolt-preview-*",
                    "refs/tags/zolt-v*"
                ],
                exclude: []
            }
        },
        rules: [
            {type: "update"},
            {type: "deletion"}
        ]
    }
' >"$immutable_release_tags_payload"

configure_ruleset "immutable release tags" "$immutable_release_tags_payload"

configure_environment_ref channel-zap branch main
configure_environment_ref channel-zap-recovery branch main
configure_recovery_protection
configure_environment_ref channel-preview branch main
configure_environment_ref channel-preview-signing branch main
configure_environment_ref channel-stable tag 'zolt-v*'

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
  1. Keep the solo owner in release-engineers; add release-approvers and maintainers only as trusted people join.
  2. Give normal maintainers read-only access to this repository when they join.
  3. Keep release-engineers listed for sensitive paths through CODEOWNERS; solo mode does not gate merging on that review.
  4. Add one trusted reviewer to channel-stable and prevent self-review before stable publication.
  5. Keep channel-zap and channel-preview at zero reviewers initially.
  6. Keep channel-zap-recovery approval-gated; the bootstrap configures ${RECOVERY_REVIEWER} and prevents self-review.
  7. Confirm only reviewed publication workflows grant contents:write and release tags cannot move or be deleted.
  8. Confirm each environment allows only its bootstrap-managed branch or tag pattern.
  9. Confirm immutable releases show as enabled; bootstrap enables them through the GitHub API.
 10. Configure the dispatcher GitHub App and install source-integration/ in zoltsh/zolt.

No publication secret is required for candidate builds.
EOF
