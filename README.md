<p align="center">
  <img src="https://raw.githubusercontent.com/zoltsh/zolt/main/logo.svg" alt="zolt" width="720">
</p>

<h3 align="center">Download Zolt</h3>

<p align="center">
  Stable, preview, and zap builds for macOS and Linux.
</p>

<p align="center">
  <a href="https://github.com/zoltsh/releases/actions/workflows/validate.yml">
    <img src="https://github.com/zoltsh/releases/actions/workflows/validate.yml/badge.svg" alt="Validation">
  </a>
</p>

<p align="center">
  <a href="https://github.com/zoltsh/zolt">Zolt</a>
  <span> · </span>
  <a href="#install">Install</a>
  <span> · </span>
  <a href="https://github.com/zoltsh/releases/releases">Releases</a>
  <span> · </span>
  <a href="#choose-a-channel">Channels</a>
  <span> · </span>
  <a href="./SECURITY.md">Security</a>
</p>

<br />

## Install

```sh
curl -fsSL https://dist.zolt.sh/install.sh | sh
```

Until the first stable release exists, the installer follows the automatic zap
channel. Production automation should use an exact version and checksum.

## Choose a channel

| Channel | Best for | Origin |
| :--- | :--- | :--- |
| `stable` | Recommended releases; not enabled yet | Signed pointer at `dist.zolt.sh` |
| `preview` | Alpha, beta, and release candidates; not enabled yet | Signed pointer at `dist.zolt.sh` |
| `zap` | The latest healthy build from `main` | [Signed pointer at `dist.zolt.sh`](https://dist.zolt.sh/channels/zap.json) |

> [!TIP]
> Production builds should pin an exact version and checksum instead of following a
> channel.

## What comes with a release

| File | Purpose |
| :--- | :--- |
| Native archives | Zolt for each supported platform |
| SHA-256 checksums | File-integrity verification |
| Release manifest | Version, builder metadata, and archive identities |
| Release record | Source, workflow, controller, and candidate file identities |
| Source CI evidence | The exact successful source run used for the build |

Release files for every channel live in immutable
[GitHub Releases](https://github.com/zoltsh/releases/releases). DigitalOcean stores only
the small signed channel and release-index files that tell existing clients which GitHub
Release is current. Preview and stable publication remain disabled until their build and
approval workflows are complete; they use the same GitHub asset contract as zap.

## About this repository

Zolt source and product development live in
[`zoltsh/zolt`](https://github.com/zoltsh/zolt). This repository contains the release
workflows, policy, schemas, and operating documentation.

| Read | When you need it |
| :--- | :--- |
| [Release architecture](./docs/ARCHITECTURE.md) | Understand the trust model and release flow |
| [Repository setup](./docs/SETUP.md) | Configure GitHub, credentials, and channels |
| [Incident runbooks](./docs/RUNBOOKS.md) | Respond to failed or compromised releases |
| [Security policy](./SECURITY.md) | Report a vulnerability or exposed credential |
| [Contributing](./CONTRIBUTING.md) | Change release code or policy |

## Development

```sh
scripts/check
```

This builds the controller, runs its tests, checks repository policy, packages the
application, and validates the packaged command.

## License

Apache-2.0. See [LICENSE](./LICENSE).
