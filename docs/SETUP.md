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
- restricts Actions to reviewed, full-SHA dependencies
- protects `main` with the reviewed solo-maintainer repository rules
- protects all controller release-tag namespaces so only GitHub Actions can create
  immutable tags
- creates `channel-zap`, `channel-zap-recovery`, `channel-preview-signing`,
  `channel-preview`, and `channel-stable` with exact deployment-ref policies
- requires one named reviewer for Zap recovery, prevents self-review, and disables
  administrator bypass
- enables immutable GitHub Releases for every channel

The repository should be public so its release process and evidence are visible.

## 2. Define the current team

| Team | Repository access | Job |
| --- | --- | --- |
| `release-engineers` | Write | Review controller changes and start releases |
| `release-approvers` | Read | Approve stable publication |
| `maintainers` | Read | Inspect release code and evidence |

The initial repository has one owner and release engineer. Do not manufacture reviews
with a second account. Keep organization owners separate and few; a Zolt maintainer does
not automatically need release or owner access. Add these teams as real trusted people
join.

## 3. Protect `main`

The bootstrap creates a `main` ruleset with:

- pull requests required
- zero required approvals while only one maintainer exists
- no required CODEOWNER or latest-push approval that the author cannot provide
- resolved conversations
- required `repository` check from GitHub Actions
- no force pushes
- no deletion
- no normal bypass

`CODEOWNERS` still records and requests ownership, including for itself; it is not a
merge gate in solo-maintainer mode. When another trusted maintainer has write access,
raise the policy to one approval and require CODEOWNER and latest-push approval. Require
two approvals only when two independent reviewers are actually available.

The bootstrap protects these release tag patterns:

```text
zolt-zap-*
zolt-preview-*
zolt-v*
```

Only GitHub Actions may create them. Updates and deletion are restricted for everyone;
publication code must verify and reuse an existing immutable release on retry.

## 4. Restrict GitHub Actions

The bootstrap restricts this repository to:

- keep the default `GITHUB_TOKEN` read-only
- prevent Actions from approving pull requests
- allow GitHub-owned actions, `graalvm/setup-graalvm`, and `zoltsh/setup-zolt`
- pin every external action to a full commit SHA
- do not use self-hosted runners for jobs with publication credentials

Keep the organization policy compatible or stricter. Add any future external action or
reusable workflow to the repository allowlist only after review.

The repository test suite rejects unpinned actions.

The local `setup-zolt` action pins the installer action by full commit SHA, then pins
an exact Zolt version and SHA-256. Review all three values like a dependency update.
They must not come from workflow input or the candidate commit.

## 5. Configure environments

| Environment | Allowed workflow ref | Reviewers | Purpose |
| --- | --- | ---: | --- |
| `channel-zap` | branch `main` | 0 | Automatic zap publication |
| `channel-zap-recovery` | branch `main` | 1 | Operator-approved zap rollback |
| `channel-preview-signing` | branch `main` | 0 | Sign metadata and create the immutable preview |
| `channel-preview` | branch `main` | 0 | Promote already-signed preview metadata after canary |
| `channel-stable` | tag `zolt-v*` | 1 | Stable publication after approval |

Set `ZOLT_RECOVERY_REVIEWER` before running the bootstrap when the authenticated GitHub
owner should not be the recovery reviewer. The bootstrap requires that one user,
prevents self-review, and disables administrator bypass for `channel-zap-recovery`.
For `channel-stable`, prevent self-review and disable administrator bypass where
available. The bootstrap replaces each environment's deployment rules with the single
ref shown above; a workflow from any other ref cannot receive that environment's
secrets.

Zap and preview have reviewed publishers and are enabled in `policy/channels.toml`.
Stable remains disabled. Preview deliberately splits authority: the signing environment
has no Spaces credential, and the promotion environment has no signing key.

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
source-integration/dispatch-preview.yml -> zoltsh/zolt/.github/workflows/dispatch-preview.yml
source-integration/CODEOWNERS        -> zoltsh/zolt/.github/CODEOWNERS
```

If `zoltsh/zolt` already has a `CODEOWNERS` file, merge the entries instead of replacing
the file.

Enable CODEOWNER review on source `main` before adding the App private key. The current
candidate workflow fetches source over public HTTPS, so `zoltsh/zolt` must be public
before candidate builds can run as written.

After source `ci` passes on `main`, the dispatcher sends the repository name, commit
SHA, and CI run ID here. This repository checks all three again before building.

Protect preview source tags by running:

```sh
source-integration/configure-preview-tag-rules
```

By default the authenticated GitHub user is the only bypass actor. Set
`ZOLT_PREVIEW_TAG_CREATOR` to a different trusted release engineer. The ruleset covers
`v*.*.*-*`; the source dispatcher and controller additionally require a valid signed
annotated tag in the exact `vMAJOR.MINOR.PATCH-(alpha|beta|rc).N` shape.

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
DigitalOcean distribution for the stable installer bootstrap and small signed moving
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
`channels/zap.json`, `releases/zap.json`, and the JSON signature sidecars. The bootstrap
pins and verifies an immutable GitHub installer; Spaces never stores native archives,
checksums, manifests, or release records.

Object versioning is optional recovery convenience, not a publication prerequisite.
Every immutable GitHub Release contains signed snapshots of its channel and release
index, so the moving metadata can be reconstructed without Space object history.

Add these secrets to the `channel-zap` environment:

```text
DO_SPACES_ACCESS_KEY_ID
DO_SPACES_SECRET_ACCESS_KEY
ZOLT_RELEASE_ED25519_PRIVATE_KEY
```

Add only the two narrow Spaces credentials to `channel-zap-recovery`:

```text
DO_SPACES_ACCESS_KEY_ID
DO_SPACES_SECRET_ACCESS_KEY
```

GitHub does not reveal an existing environment secret's value, so provision the same
narrow credential again from its trusted source. Do not put the signing key in the
recovery environment: recovery republishes metadata signatures from an immutable
release and must not be able to create new signed metadata.

`ZOLT_RELEASE_ED25519_PRIVATE_KEY` must be the existing unencrypted PKCS#8 Ed25519 PEM
whose public key is bundled as `zolt-release-2026`. The trusted signing command proves
the match before uploading. If that private key has been lost, stop and design a
client-compatible key rotation; creating a replacement under the same key ID will not
work.

The workflow uses its repository-scoped `GITHUB_TOKEN` to create the immutable GitHub
Release. It fixes the GitHub repository, Space, region, endpoint, metadata origin, and
key ID in reviewed code. They are not workflow inputs or secrets.

## 8. Configure protected preview publication

Preview uses a distinct Ed25519 key whose public half is bundled in Zolt and this
controller as `zolt-preview-2026`. Add only its unencrypted PKCS#8 private PEM to
`channel-preview-signing`:

```text
ZOLT_RELEASE_ED25519_PRIVATE_KEY
```

Add only the narrow `zolt-dist` credential to `channel-preview`:

```text
DO_SPACES_ACCESS_KEY_ID
DO_SPACES_SECRET_ACCESS_KEY
```

GitHub cannot reveal existing environment secret values, so provision the same narrow
Spaces credential again from its trusted source. Do not copy it into the signing
environment, and do not copy the preview private key into the promotion environment.

The source commit must already be on `zoltsh/zolt` `main` with successful source CI.
The release engineer then creates and pushes a signed annotated tag, for example:

```sh
git tag -s v0.1.0-alpha.1 -m 'v0.1.0-alpha.1'
git push origin v0.1.0-alpha.1
```

That single human action dispatches a four-target candidate. Trusted controller `main`
reverifies the tag and CI, signs and publishes an immutable prerelease, runs a
secretless immutable canary, promotes the signed metadata with compare-and-swap, and
then runs a public preview smoke.

## 9. Start automatic zap publication

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

Do not turn stable on by copying the zap workflow and changing its channel name.

## 10. Verify the configuration

Before any public release:

- [ ] Immutable releases are enabled.
- [ ] Branch and tag rules are active.
- [ ] Controller release tags can be created only by GitHub Actions and cannot move or
      be deleted.
- [ ] Source prerelease tags can be created only by the named release engineer and
      cannot move or be deleted.
- [ ] `channel-zap` permits only the `main` branch.
- [ ] `channel-zap-recovery` permits only `main`, requires one reviewer, prevents
      self-review, and disallows administrator bypass.
- [ ] Both preview environments permit only trusted controller `main`.
- [ ] Stable has one reviewer and prevents self-review.
- [ ] Zap and preview have no reviewers.
- [ ] `channel-zap` has the narrow `zolt-dist` Spaces key and matching Ed25519 private key.
- [ ] `channel-zap-recovery` has only the narrow `zolt-dist` Spaces key.
- [ ] `channel-preview-signing` has only the matching preview Ed25519 private key.
- [ ] `channel-preview` has only the narrow `zolt-dist` Spaces key.
- [ ] No release archive is uploaded to `zolt-dist`.
- [ ] `https://dist.zolt.sh/install.sh` matches `scripts/install-bootstrap` exactly.
- [ ] The live zap channel and release index verify with `zolt-release-2026`.
- [ ] Preview intent validation succeeds without creating a tag or publishing files.
- [ ] `scripts/check` passes.
