# Repository setup

[README](../README.md) · [Architecture](./ARCHITECTURE.md) ·
[Incident runbooks](./RUNBOOKS.md) · [Security](../SECURITY.md)

Use this checklist to configure `zoltsh/releases` and connect it to the Zolt source
repository.

> [!IMPORTANT]
> Complete the steps in order. Candidate builds do not need signing keys or publication
> credentials.

## Before you begin

You need:

- GitHub CLI authenticated as an owner of `zoltsh`
- a trusted Zolt executable on `PATH`, or its path in `ZOLT_BIN`
- access to create organization teams, rulesets, environments, and a GitHub App

If Git uses a custom SSH host, set `ZOLT_RELEASES_REMOTE` to the complete repository
URL before running the bootstrap script.

## 1. Bootstrap the repository

Run:

```sh
./scripts/bootstrap.sh
```

The script:

- creates or connects `zoltsh/releases`
- pushes `main`
- sets the basic repository options
- makes the default Actions token read-only
- creates `channel-zap`, `channel-preview`, and `channel-stable`
- enables immutable GitHub Releases

The repository should be public so its release process and evidence are visible.

## 2. Create the teams

| Team | Repository access | Job |
| --- | --- | --- |
| `release-engineers` | Write | Review controller changes and start releases |
| `release-approvers` | Read | Approve stable publication |
| `maintainers` | Read | Inspect release code and evidence |

Keep organization owners separate and few. A Zolt maintainer does not automatically
need release or owner access.

## 3. Protect `main`

Create a ruleset for `main` with:

- pull requests required
- two approvals
- CODEOWNER review
- approval of the latest push
- resolved conversations
- required `validate / repository` check
- no force pushes
- no deletion
- no normal bypass

`CODEOWNERS` protects itself.

Before publication, protect these release tag patterns:

```text
zolt-zap-*
zolt-preview-*
zolt-v*
```

Only the trusted publisher may create them. Do not allow updates or deletion.

## 4. Restrict GitHub Actions

At the organization and repository levels:

- keep the default `GITHUB_TOKEN` read-only
- prevent Actions from approving pull requests
- allow GitHub-owned actions, `graalvm/setup-graalvm`, and reviewed reusable workflows
- pin every external action to a full commit SHA
- do not use self-hosted runners for jobs with publication credentials

The repository test suite rejects unpinned actions.

The local `setup-zolt` action also pins the Zolt source revision used to build the
controller. Review that pin like a dependency update. It must not come from workflow
input or the candidate commit.

## 5. Configure environments

| Environment | Reviewers | Purpose |
| --- | ---: | --- |
| `channel-zap` | 0 | Automatic zap publication |
| `channel-preview` | 0 | Preview publication after a protected tag |
| `channel-stable` | 1 | Stable publication after approval |

For `channel-stable`, prevent self-review and disable administrator bypass where
available.

Do not add secrets until that channel has a reviewed publisher and its status is
enabled in `policy/channels.toml`.

## 6. Connect `zoltsh/zolt`

Create a GitHub App named `zolt-release-dispatcher`.

Give it one repository permission:

```text
Actions: Read and write
```

Install it only on `zoltsh/releases`.

In `zoltsh/zolt`, add:

```text
Variable: ZOLT_RELEASES_DISPATCH_APP_CLIENT_ID
Secret:   ZOLT_RELEASES_DISPATCH_APP_PRIVATE_KEY
```

Copy:

```text
source-integration/dispatch-zap.yml -> zoltsh/zolt/.github/workflows/dispatch-zap.yml
source-integration/CODEOWNERS        -> zoltsh/zolt/.github/CODEOWNERS
```

If `zoltsh/zolt` already has a `CODEOWNERS` file, merge the entries instead of replacing
the file.

Enable CODEOWNER review on source `main` before adding the App private key. The current
candidate workflow fetches source over public HTTPS, so `zoltsh/zolt` must be public
before candidate builds can run as written.

After source `ci` passes on `main`, the dispatcher sends the repository name, commit
SHA, and CI run ID here. This repository checks all three again before building.

### What the App can do

GitHub grants Actions permission for the whole repository, not one workflow. The App
token can start, cancel, enable, or disable workflows in `zoltsh/releases`.

It cannot:

- change repository contents
- create a GitHub Release
- read environment secrets
- sign channel metadata
- approve stable publication

Rotate the App key if the source workflow or repository is compromised.

## 7. Prepare storage and signing

Before enabling publication, create:

```text
zolt-channel-stable
zolt-channel-preview
zolt-channel-zap
```

Each bucket gets its own write credential and signing key. Put each credential only in
its matching GitHub environment. Enable bucket versioning and keep listings private.

GitHub Releases stores archives and other large files. The buckets store only small,
signed channel metadata.

Create the offline root key before the first public stable release. Keep it out of
GitHub and DigitalOcean.

> [!CAUTION]
> Never store the offline root key in GitHub Actions or ordinary cloud storage.

## 8. Enable channels in order

1. Run and inspect zap candidates.
2. Add exact-file smoke tests and the zap publisher.
3. Change zap from `candidate` to `enabled` in `policy/channels.toml`.
4. Add preview publication.
5. Add stable publication last.

Do not turn stable on by copying the zap workflow and changing its channel name.

## 9. Verify the configuration

Before any public release:

- [ ] Immutable releases are enabled.
- [ ] Branch and tag rules are active.
- [ ] Stable has one reviewer and prevents self-review.
- [ ] Zap and preview have no reviewers.
- [ ] Each environment can reach only its own bucket and key.
- [ ] `scripts/check` passes.
