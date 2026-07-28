# Release architecture

[README](../README.md) · [Repository setup](./SETUP.md) ·
[Incident runbooks](./RUNBOOKS.md) · [Security](../SECURITY.md)

This document describes how Zolt turns trusted source into immutable releases without
giving candidate code access to publication credentials.

> [!NOTE]
> Candidate builds are implemented. Publication remains disabled until its signing,
> storage, and recovery requirements are complete.

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
        +--> GitHub Releases: binaries and evidence
        |
        +--> signed channel file: which release is current
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

The three public origins are:

```text
https://dist.zolt.sh
https://preview.dist.zolt.sh
https://zap.dist.zolt.sh
```

`https://dist.zolt.sh/install.sh` is the normal installer. Choosing preview or zap
changes where the installer reads channel metadata.

## Storage

| Location | Stores |
| :--- | :--- |
| GitHub Releases | Native archives, checksums, manifests, SBOMs, provenance, and release records |
| DigitalOcean Spaces | Small signed channel metadata |

GitHub release immutability applies to every channel, including zap. An old zap release
may be deleted only as a whole after nothing points to it. Its files are never replaced,
and its tag is never reused.

DigitalOcean Spaces stores only small channel metadata. It has three buckets:

```text
zolt-channel-stable
zolt-channel-preview
zolt-channel-zap
```

Each bucket has its own write credential, signing key, GitHub environment, and DNS
origin. A zap credential cannot write preview or stable metadata.

Enable object versioning on all three buckets. Keep bucket listings private and expose
only the required metadata files. The buckets are not the source of truth; signed
release records and immutable GitHub files must be enough to rebuild channel state.

Large release files never go in Spaces. GitHub serves the bandwidth-heavy downloads;
the buckets remain small and inexpensive.

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

Candidate code never runs with preview or stable credentials.

> [!IMPORTANT]
> Build jobs create candidates. Only a fresh publication job can turn a verified
> candidate into a release.

### Build job

- checks out the exact source commit or protected tag
- has read-only repository access
- has no signing or publication credentials
- builds every supported target
- runs the required tests and smokes
- creates checksums, an SBOM, and provenance
- uploads candidate files

### Verification job

- downloads the exact candidate files
- checks hashes and archive layout
- runs install, update, build, test, and package smokes
- checks the embedded version and source commit
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

Each channel has one operational key:

```text
zolt-stable-2026
zolt-preview-2026
zolt-zap-2026
```

Before the first public stable release, create an offline root key. The root authorizes
channel keys and is used only to add, rotate, revoke, or recover them.

```text
offline root
├── stable key
├── preview key
└── zap key
```

The root private key never enters GitHub Actions or DigitalOcean. Keep it hardware
protected with a separate offline backup.

> [!CAUTION]
> Losing the offline root key breaks the base of client trust. Its recovery path must be
> practiced before stable publication.

At first, channel keys may live in their matching protected GitHub environments. The
stable key can later move to a hardware-backed or non-exportable signing service
without changing the file format.

Use one small, versioned, root-signed key document. It records key IDs, allowed
channels, validity dates, revocations, and overlap during rotation. Do not build a
larger custom PKI.

Every signed channel file includes:

```text
schema version
channel name
sequence number
operation: promote or rollback
issued-at time
expiry time
digest of the previous channel file
release version
source commit
release-manifest URL
release-manifest digest
channel signature
```

Clients must:

- receive the expected channel from the caller
- reject a signed channel name that does not match
- accept only keys authorized for that channel
- reject an older sequence number
- treat rollback as a new, higher sequence pointing to an older immutable release
- reject unknown keys, channels, algorithms, and schema versions
- allow pinned installs without reading a channel file

Sign deterministic bytes, not whichever JSON formatting a library happens to emit.

## Installation trust

This command starts by trusting HTTPS:

```sh
curl -fsSL https://dist.zolt.sh/install.sh | sh
```

> [!WARNING]
> This path begins by trusting HTTPS. Signed metadata protects later downloads; it
> cannot independently authenticate an installer that has already been replaced.

The script can verify signed metadata and file checksums after it starts, but an
attacker who replaced the script could also replace its embedded key. Do not describe
`curl | sh` as independently authenticated.

Offer two install paths:

1. **Convenient:** use the HTTPS installer, then verify signed channel metadata and
   downloaded file checksums.
2. **Pinned:** download a versioned installer or archive from an immutable GitHub
   Release and verify its published checksum and provenance.

Production CI uses the pinned path.

## Release flows

### Zap candidate

```text
main CI passes
  -> dispatch exact commit and CI run
  -> check that CI evidence again
  -> build four targets without release credentials
  -> verify archives and checksums
  -> write a release record
  -> stop
```

### Zap publication

```text
candidate passes
  -> smoke the exact candidate files
  -> publish immutable zap files
  -> sign the next zap channel file
  -> reject stale or out-of-order publication
  -> change the zap channel last
```

Zap needs no human approval. Its publisher is hard-coded to zap and never accepts an
arbitrary channel input.

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

The three metadata buckets share one DigitalOcean Spaces subscription. Channel files
are only a few kilobytes. GitHub Releases serves the large files, and production CI
usually downloads a pinned release instead of polling a channel.

If usage outgrows Spaces, move metadata behind the same domains to another
S3-compatible service or CDN. The channel format and release URLs do not need to
change.

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
