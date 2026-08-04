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
- a native Zolt binary on `PATH`
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
- enables immutable GitHub Releases for every channel

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

The local `setup-zolt` action pins the exact native Zolt archive and its SHA-256.
Review both values like a dependency update. They must not come from workflow input
or the candidate commit. Its current pin is the final legacy `dist.zolt.sh` bootstrap
archive. After the first GitHub-hosted zap is public, update the pin to that immutable
GitHub Release in a separate reviewed change.

## 5. Configure environments

| Environment | Reviewers | Purpose |
| --- | ---: | --- |
| `channel-zap` | 0 | Automatic zap publication |
| `channel-preview` | 0 | Preview publication after a protected tag |
| `channel-stable` | 1 | Stable publication after approval |

For `channel-stable`, prevent self-review and disable administrator bypass where
available.

Zap now has a reviewed publisher and is enabled in `policy/channels.toml`. Preview and
stable must not receive secrets until their own publishers are implemented and
reviewed.

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

## 7. Configure zap metadata and signing

All archives, checksums, the exact source-commit installer, manifests, records, and
evidence are GitHub Release assets in `zoltsh/releases`. Zap uses the existing
DigitalOcean distribution only for the public installer and its small signed moving
metadata:

```text
Space:    zolt-dist
Region:   nyc3
Endpoint: https://nyc3.digitaloceanspaces.com
Origin:   https://dist.zolt.sh
Key ID:   zolt-release-2026
```

Keep bucket listing private and use a Spaces key restricted to `zolt-dist`. The
publisher needs object read and write access only for `install.sh`,
`channels/zap.json`, `releases/zap.json`, and their signature sidecars. It never stores
release archives in Spaces.

Object versioning is optional recovery convenience, not a publication prerequisite.
Every immutable GitHub Release contains signed snapshots of its channel and release
index, so the moving metadata can be reconstructed without Space object history.

Add these secrets to the `channel-zap` environment:

```text
DO_SPACES_ACCESS_KEY_ID
DO_SPACES_SECRET_ACCESS_KEY
ZOLT_RELEASE_ED25519_PRIVATE_KEY
```

`ZOLT_RELEASE_ED25519_PRIVATE_KEY` must be the existing unencrypted PKCS#8 Ed25519 PEM
whose public key is bundled as `zolt-release-2026`. The trusted signing command proves
the match before uploading. If that private key has been lost, stop and design a
client-compatible key rotation; creating a replacement under the same key ID will not
work.

The workflow uses its repository-scoped `GITHUB_TOKEN` to create the immutable GitHub
Release. It fixes the GitHub repository, Space, region, endpoint, metadata origin, and
key ID in reviewed code. They are not workflow inputs or secrets.

## 8. Start automatic zap publication

After the publisher is merged to `main` and the three environment secrets exist,
rerun the source CI dispatcher or allow the next successful `zoltsh/zolt` `main` CI
run to dispatch a candidate. A successful `zap candidate` run automatically starts
`zap publish`; there is no environment approval.

The first successful publisher run creates an immutable prerelease under
`https://github.com/zoltsh/releases/releases`, then changes the public zap channel.
Before triggering it, confirm the current signed files at:

```text
https://dist.zolt.sh/channels/zap.json
https://dist.zolt.sh/channels/zap.json.sig
https://dist.zolt.sh/releases/zap.json
https://dist.zolt.sh/releases/zap.json.sig
```

Add preview publication next and stable publication last. Do not enable either by
copying the zap workflow and changing its channel name.

Do not turn stable on by copying the zap workflow and changing its channel name.

## 9. Verify the configuration

Before any public release:

- [ ] Immutable releases are enabled.
- [ ] Branch and tag rules are active.
- [ ] Stable has one reviewer and prevents self-review.
- [ ] Zap and preview have no reviewers.
- [ ] `channel-zap` has the existing metadata-only Spaces key and matching Ed25519 private key.
- [ ] No release archive is uploaded to `zolt-dist`.
- [ ] The live zap channel and release index verify with `zolt-release-2026`.
- [ ] `scripts/check` passes.
