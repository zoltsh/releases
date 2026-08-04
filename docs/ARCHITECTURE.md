# Release architecture

[README](../README.md) · [Repository setup](./SETUP.md) ·
[Incident runbooks](./RUNBOOKS.md) · [Security](../SECURITY.md)

This document describes how Zolt turns trusted source into immutable releases without
giving candidate code access to publication credentials.

> [!NOTE]
> Automatic zap publication stores immutable files in GitHub Releases and moves signed
> metadata at `dist.zolt.sh` last. Preview and stable publication remain disabled.

## Contents

- [Trust model](#trust-model)
- [Channels](#channels)
- [Storage](#storage)
- [Roles and access](#roles-and-access)
- [Repository protection](#repository-protection)
- [Job boundaries](#job-boundaries)
- [Signing model](#signing-model)
- [Release flows](#release-flows)
- [Production use](#production-use)
- [Stable release checklist](#stable-release-checklist)
- [Invariants](#invariants)

## Trust model

`zoltsh/zolt` owns the source code. `zoltsh/releases` owns release credentials,
release files, and the decision to publish.

```text
zoltsh/zolt
  CI passes on an exact commit
        |
        v
zoltsh/releases
  check the CI run again
  build without release credentials
  verify the files
  publish through a separate trusted job
        |
        +--> GitHub Releases: immutable files and evidence
        |
        +--> dist.zolt.sh: signed pointer to the current GitHub Release
```

This separation means a source maintainer can trigger a zap build without gaining
preview or stable release access.

## Glossary

- **Candidate:** built and checked files that have not been published.
- **Release record:** JSON that connects candidate files to the source commit, source
  CI run, controller commit, and checksums.
- **Channel file:** a small signed file that says which immutable release is current.
- **Provenance:** information about where and how a release was built.
- **SBOM:** a list of software included in a release.

## Channels

| Channel | Purpose | Starts from | Human action |
| --- | --- | --- | --- |
| `zap` | Latest healthy development build | Exact commit on `main` | None |
| `preview` | Alpha, beta, and release-candidate builds | Protected prerelease tag | Create the tag |
| `stable` | Recommended release | Protected final tag | Create the tag and approve publication |

Zap stays automatic. Preview requires a deliberate tag. Stable adds one protected
approval.

All three channels use GitHub Releases for downloadable files. Their signed metadata
uses one public origin:

```text
https://dist.zolt.sh
```

The stable and preview workflows are not enabled. Before either is enabled, its build,
signing, and approval path must be completed and reviewed.

The public installer command names an exact immutable GitHub Release asset. Until
stable exists, that installer follows `channels/zap.json`. Every release preserves the
installer extracted from its exact verified source commit; DigitalOcean does not serve
executable bootstrap code.

## Storage

| Location | Stores |
| :--- | :--- |
| GitHub Releases | Native archives, checksums, the source-matched installer, manifests, release records, source evidence, and signed metadata snapshots |
| `zolt-dist` Space | Current signed channel/release-index metadata only |

Every channel publishes one complete immutable GitHub Release. Zap and preview releases
are prereleases; stable releases are normal releases and may be marked latest. Release
tags and assets are never reused or replaced. A retry must verify the existing tag,
asset set, sizes, and SHA-256 digests before accepting it.

The mutable zap files are `channels/zap.json` and `releases/zap.json`; each has an
Ed25519 `.sig` sidecar. The publisher first makes the GitHub Release immutable, ensures
the retired `install.sh` object is absent, then writes the release-index pair, the
channel signature, and `channels/zap.json` last. The archive URLs in the signed channel
point directly at exact immutable GitHub Release assets.

DigitalOcean object versioning is optional. Each GitHub Release contains immutable
copies of the signed channel and index produced for that publication, so operators can
reconstruct channel state after an accidental metadata overwrite. Preview and stable
will use the same storage split when their policy status changes from `disabled`.

## Roles and access

| Role | `zoltsh/zolt` | `zoltsh/releases` | Release access |
| --- | --- | --- | --- |
| Contributor | Open pull requests | Read | None |
| Maintainer | Merge protected pull requests | Read | Trigger zap through normal CI |
| Release engineer | Write | Write | Start preview or stable work |
| Release approver | Read | Read | Approve stable publication |
| Organization owner | Emergency administration | Emergency administration | Ultimate GitHub access |

Keep organization owners few. Aim for two owners for account recovery, both protected
by passkeys or hardware security keys. Being a maintainer is not a reason to become an
organization owner or release engineer.

Human work per operation:

| Operation | Human work |
| --- | --- |
| Normal source pull request | One review |
| Release-sensitive source change | Release-engineer CODEOWNER review |
| Zap after merge | None |
| Preview | Create a protected prerelease tag |
| Stable | One protected-environment approval |
| Release-controller change | Two reviews, including the security owner |
| Root or stable-key rotation | Follow the security procedure |

When a second trusted release person is available, stable should prevent self-review.

## Repository protection

### Organization defaults

Use these defaults:

- lowest practical base repository permission
- secure two-factor authentication required
- read-only default `GITHUB_TOKEN`
- GitHub Actions cannot approve pull requests
- only approved actions and reusable workflows
- every external action pinned to a full commit SHA
- classic personal access tokens blocked
- fine-grained personal access tokens approved and short-lived
- GitHub App installation restricted to owners

Use `GITHUB_TOKEN` inside one repository. Use a narrowly scoped GitHub App for the
cross-repository zap trigger. Do not use a maintainer's personal token.

### `zoltsh/zolt`

Protect `main` with:

- pull requests
- one approval
- required CI
- approval of the latest push
- resolved conversations
- no force pushes
- no branch deletion
- no normal maintainer bypass

Require `@zoltsh/release-engineers` review for:

```text
.github/workflows/**
.github/actions/**
.github/CODEOWNERS
scripts/install-zolt
scripts/*release*
scripts/*distribution*
modules/zolt-release/**
modules/zolt-update/**
release/**
```

Protect `CODEOWNERS` itself.

### `zoltsh/releases`

Protect `main` with:

- two approvals
- CODEOWNER review
- approval of the latest push
- all required checks
- resolved conversations
- no force pushes
- no branch deletion
- no normal bypass

Protect these tag namespaces:

```text
zolt-zap-*
zolt-preview-*
zolt-v*
```

Only the trusted publisher may create them. Do not allow updates or deletion.

Protect source `v*` tags as well. Require signed tags and allow only release authority
to create, update, delete, or bypass their rules. Zap uses its exact source commit
instead of a source `v*` tag.

## Trusted build tool

The controller is a Java project built and tested by Zolt. Its workflows install one
exact native Zolt archive pinned by URL and SHA-256 in
`.github/actions/setup-zolt/action.yml`.

The controller code is split by responsibility:

| Package | Owns |
| --- | --- |
| `cli` | Command parsing and dispatch |
| `policy` and `intent` | Channel policy and release-request validation |
| `github` | GitHub Actions access and source-run verification |
| `record` | Candidate artifact checks and release records |
| `repository` | Independent repository policy checks |
| `version` | Zap version calculation |
| `io` | JSON, TOML, and filesystem helpers |
| `core` | Shared release constants |

That pin is part of the controller, not an input from the source workflow. A candidate
commit cannot choose the Zolt version that validates it. Update the archive and
checksum through the same two-review process as any other controller change. After the
first stable release, prefer the previous trusted Zolt release when preparing the next
one.

Every controller command calls the installed `zolt` binary directly.

## Source handoff

The source repository uses a GitHub App to start the fixed zap workflow here. The App
has Actions read/write access to `zoltsh/releases` and no Contents, Secrets,
Environments, or release-channel access.

GitHub grants Actions access to the whole repository, not one workflow. A stolen App
token could start, cancel, enable, or disable workflows. It still could not change
controller code, read channel secrets, sign metadata, or approve stable.

This repository does not trust the trigger by itself. It checks:

- the source is exactly `zoltsh/zolt`
- the workflow is `ci` at `.github/workflows/ci.yml`
- the event was a push to `main`
- the run completed for the requested commit
- every required test, coverage, smoke, and managed-toolchain job passed

## Job boundaries

Candidate code never runs with signing or storage credentials for any channel.

> [!IMPORTANT]
> Build jobs create candidates. Only a fresh publication job can turn a verified
> candidate into a release.

### Build job

- checks out the exact source commit or protected tag
- has read-only repository access
- has no signing or publication credentials
- builds every supported target
- inherits the required tests and smokes from the independently checked source CI run
- runs the native release verifier for each target
- creates checksums and one target release manifest
- uploads candidate files

### Verification job

- downloads the exact candidate files
- checks archive checksum sidecars
- requires all four target archives and manifests
- records every candidate file digest and size
- writes the release record

### Publication job

- starts on a fresh GitHub-hosted runner
- checks out only trusted controller code at an exact commit
- does not check out candidate source
- does not execute the candidate binary
- downloads candidates by exact workflow and artifact identity
- checks every digest again
- signs trusted metadata
- publishes the immutable release
- changes the channel file last

Source code can create a bad candidate. It cannot turn that candidate into a trusted
preview or stable release by gaining access to publication credentials.

## Signing model

The deployed zap contract uses one Ed25519 key:

```text
key id: zolt-release-2026
sidecar version: zolt-ed25519-v1
```

The matching public key is bundled in Zolt and in this trusted controller. The private
key exists only as `ZOLT_RELEASE_ED25519_PRIVATE_KEY` in the `channel-zap` GitHub
environment. The publisher accepts an unencrypted PKCS#8 PEM, signs the exact file
bytes, and verifies the new signature against the bundled public key before any upload.
A missing or wrong private key therefore fails before publication.

Each mutable JSON file has a text sidecar:

```text
version: zolt-ed25519-v1
keyId: zolt-release-2026
signature: <base64 Ed25519 signature>
```

Both `channels/zap.json` and `releases/zap.json` are signed. The controller verifies the
currently public pair before using it as publication input, validates its structure,
and produces deterministic successor files. Native Zolt update clients verify the
channel sidecar before trusting archive URLs or digests.

The current format has no root-signed online key directory or sequence field. Rotating
`zolt-release-2026` therefore requires a reviewed Zolt source release that adds the new
public key, an overlap plan, and a separate recovery runbook. Do not generate a new key
silently if the existing private key is lost.

## Installation trust

The public command pins the installer code to one immutable release snapshot:

```sh
version='0.1.0-zap.20260804.c72838dc828e'
release="https://github.com/zoltsh/releases/releases/download/zolt-zap-$version"
curl --proto '=https' --proto-redir '=https' --tlsv1.2 -fsSL \
  "$release/install.sh" |
  ZOLT_INSTALL_CHANNEL=zap \
  ZOLT_INSTALL_VERSION="$version" \
  ZOLT_INSTALL_CHANNEL_URL="$release/channel-zap.json" \
  ZOLT_INSTALL_UPDATE_CHANNEL_URL=https://dist.zolt.sh/channels/zap.json \
  sh
```

The shell installer reads the channel snapshot from that same immutable release and
requires the named version, channel, target, filenames, archive URL, and checksum URL
before verifying SHA-256. It records the canonical moving channel only for later
self-updates. A compromise of that metadata origin without the signing key can deny an
update but cannot authorize executable bytes: native Zolt clients verify the channel's
Ed25519 sidecar with a bundled public key.

For reproducible automation, pin `install.sh`, `channel-zap.json`, the requested
version, archives, and checksums from one immutable GitHub Release. The primary README
command is updated to that complete snapshot after each installer protocol change; it
does not execute a mutable object-storage file.

## Release flows

### Zap candidate

```text
main CI passes
  -> dispatch exact commit and CI run
  -> check that CI evidence again
  -> build four targets without release credentials
  -> verify archives and checksums
  -> write a release record
  -> upload short-lived candidate artifacts
```

### Zap publication

```text
candidate passes
  -> start a separate workflow_run on a fresh hosted runner
  -> check out the exact trusted controller commit
  -> download artifacts from the exact candidate run
  -> verify the record, evidence, manifests, and every file identity again
  -> verify the currently public signed channel and release index
  -> reject stale or divergent source history
  -> sign the new channel and release index with the existing zap key
  -> create or resume the exact draft GitHub Release
  -> verify every GitHub asset size and SHA-256 digest
  -> publish the GitHub Release immutably
  -> publish the release index pair
  -> change the zap channel last
```

Zap needs no human approval. Its workflow is hard-coded to zap. Shared publication code
accepts a channel, but each workflow supplies a fixed value and has only that channel's
signing environment. The signing and metadata credentials are scoped only to their
steps. Candidate source is never checked out or executed on that runner.

### Preview publication

```text
protected prerelease tag
  -> check that the tag still points to the requested commit
  -> build and verify without credentials
  -> publish an immutable preview release
  -> sign the preview channel file
  -> change the preview channel last
```

Creating the protected tag is the human action. Preview initially needs no extra
environment approval. Repeating the same request must not create a second release.

### Stable publication

```text
protected final tag
  -> check that the tag still points to the requested commit
  -> build once without credentials
  -> verify the exact candidates
  -> create the SBOM, provenance, and release record
  -> create a draft GitHub Release
  -> receive one protected approval
  -> publish the immutable release
  -> sign and change the stable channel last
  -> update Homebrew
  -> publish to Maven Central when a JVM artifact is part of the release
```

Never rebuild between verification and publication. Maven Central does not need to
block the first native stable release unless a JVM artifact is part of the promise.
Repeating the same request must safely return the existing result.

Shared publication code may work for any channel, but each workflow calls it through a
fixed channel wrapper with only that channel's environment and credentials.

## Production use

Human installs may follow `stable`. Production builds should pin a version and digest.

```yaml
- uses: zoltsh/setup-zolt@<full-commit-sha>
  with:
    version: '0.3.2'
    sha256: '<expected-digest>'
```

Zolt may also support a repository-level pin:

```toml
[zolt]
version = "0.3.2"
sha256 = "..."
```

Production support must include:

- direct immutable release URLs
- manifest and provenance verification
- internal mirror override
- proxy and custom CA support
- an offline verification bundle
- no quiet fallback to another channel

If CI follows a channel by explicit choice, it must print the resolved version and
digest.

## Hosting

GitHub Releases serves every large or immutable file. The existing `zolt-dist` Space and
`https://dist.zolt.sh` serve only the installer and small signed moving metadata.
Production CI should use a pinned GitHub Release URL and checksum instead of polling a
channel.

The metadata host can move later without relocating release assets. A future metadata
backend must preserve the signed JSON contract and channel URLs used by existing
clients.

## Stable release checklist

Complete and publish:

- [ ] A working private security-reporting path
- [ ] The supported OS, architecture, ABI, JDK, and GraalVM matrix
- [ ] Signed immutable release files
- [ ] Checksums and a signed release manifest
- [ ] Build provenance and an attestation
- [ ] An SBOM for each distribution
- [ ] Clean-machine install, update, rollback, build, test, and package smokes
- [ ] Release and rollback instructions
- [ ] Key IDs and rotation instructions
- [ ] A practiced key-rotation exercise
- [ ] A practiced bad-release rollback
- [ ] Mirror, proxy, custom CA, and offline verification instructions
- [ ] A macOS signing and notarization policy
- [ ] Vulnerability response and release-withdrawal rules

> [!NOTE]
> Do not claim reproducible builds until they have been measured.

## Invariants

- Zap stays automatic.
- Zap cannot change preview or stable.
- Candidate code never sees preview or stable secrets.
- Build once; verify and publish the same files.
- Change mutable channel metadata last.
- Release files are immutable.
- Production CI pins versions and checksums.
- Source maintainers are not automatically release authorities.
- Personal tokens are not release infrastructure.
- Organization owners stay few and well protected.
- The offline root key never enters normal CI.
