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

The installer selects the stable channel by default.

## Choose a channel

| Channel | Best for | Origin |
| :--- | :--- | :--- |
| `stable` | Recommended releases | [`dist.zolt.sh`](https://dist.zolt.sh) |
| `preview` | Alpha, beta, and release candidates | [`preview.dist.zolt.sh`](https://preview.dist.zolt.sh) |
| `zap` | The latest healthy build from `main` | [`zap.dist.zolt.sh`](https://zap.dist.zolt.sh) |

> [!TIP]
> Production builds should pin an exact version and checksum instead of following a
> channel.

## What comes with a release

| File | Purpose |
| :--- | :--- |
| Native archives | Zolt for each supported platform |
| SHA-256 checksums | File-integrity verification |
| Release manifest | The complete contents of the release |
| Software bill of materials | Included components and dependencies |
| Build provenance | Where and how the release was built |
| Signed release record | Source, workflow, and artifact identity |

Large, versioned files live in immutable
[GitHub Releases](https://github.com/zoltsh/releases/releases). Small signed channel
files tell the installer which release is current.

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
