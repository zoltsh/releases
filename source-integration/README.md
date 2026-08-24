# Connect the Zolt source repository

[Release repository](../README.md) · [Setup guide](../docs/SETUP.md) ·
[Architecture](../docs/ARCHITECTURE.md)

These files connect `zoltsh/zolt` to automatic zap candidates and protected preview
candidates in `zoltsh/releases`.

## 1. Copy the integration files

```text
dispatch-zap.yml -> zoltsh/zolt/.github/workflows/dispatch-zap.yml
dispatch-preview.yml -> zoltsh/zolt/.github/workflows/dispatch-preview.yml
CODEOWNERS        -> zoltsh/zolt/.github/CODEOWNERS
```

Merge the CODEOWNERS entries if the source repository already has that file.

## 2. Configure the GitHub App

Create the `zolt-release-dispatcher` App described in the
[setup guide](../docs/SETUP.md), then add these settings to `zoltsh/zolt`:

| Setting | Name |
| :--- | :--- |
| Repository variable | `ZOLT_RELEASES_DISPATCH_APP_CLIENT_ID` |
| Repository secret | `ZOLT_RELEASES_DISPATCH_APP_PRIVATE_KEY` |

Enable CODEOWNER review on `main` before adding the private key.

## 3. Protect preview source tags

Run this as the GitHub user who will create signed preview tags:

```sh
source-integration/configure-preview-tag-rules
```

Set `ZOLT_PREVIEW_TAG_CREATOR` to name a different trusted release engineer. The
resulting source rulesets restrict creation, updates, and deletion for
`v*.*.*-*`. The preview dispatcher separately requires a signed annotated tag with a
valid GitHub verification result.

## 4. Understand the handoff

After the source workflow named `ci` finishes for a push to `main`, the dispatcher:

1. checks that all required test, coverage, smoke, and managed-toolchain jobs passed
2. creates a short-lived GitHub App token
3. starts `zap-candidate.yml` in `zoltsh/releases`
4. sends the exact source repository, commit SHA, and CI run ID

`zoltsh/releases` checks the CI evidence again before building.

When a protected prerelease tag is pushed, the preview dispatcher verifies and peels
the signed annotated tag, then sends its exact source commit and tag. The controller
repeats the signature, tag-target, source-CI, and source-main ancestry checks before any
candidate build.

```text
zoltsh/zolt CI
      |
      | exact repository, commit, and run ID
      v
zoltsh/releases
      |
      +-- verify the source run, then build a candidate
```

## Permission boundary

The App can manage Actions in `zoltsh/releases`, but it cannot change repository
contents, read channel secrets, sign metadata, or publish preview or stable by itself.
Candidate builds stop at GitHub Actions artifacts and a release record; only trusted
controller `main` can enter the isolated publication jobs.
