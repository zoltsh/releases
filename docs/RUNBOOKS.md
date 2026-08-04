# Release incident runbooks

[README](../README.md) · [Architecture](./ARCHITECTURE.md) ·
[Repository setup](./SETUP.md) · [Security](../SECURITY.md)

Use these procedures when a release fails or release authority may be compromised.

> [!CAUTION]
> Preserve workflow logs, audit events, release records, file identities, and external
> deployment IDs before changing anything.

> [!IMPORTANT]
> Zap publication uses immutable GitHub Releases plus the stable bootstrap and metadata
> in `zolt-dist`. Add the real owner and incident-contact details before enabling preview
> or stable.

## First response

| Signal | First action | Continue with |
| :--- | :--- | :--- |
| Zap build or verification fails | Leave the current zap release in place | [Zap failure](#zap-failure) |
| Preview publication fails | Stop before changing the channel | [Preview failure](#preview-failure) |
| Stable publication fails | Stop publication and preserve approval evidence | [Stable failure](#stable-failure) |
| Channel credential may be exposed | Disable that channel | [Channel credential exposure](#channel-credential-exposure) |
| Offline root key may be exposed | Stop every publication path | [Offline root key exposure](#offline-root-key-exposure) |
| Release repository may be compromised | Freeze the repository and revoke credentials | [Repository compromise](#repository-compromise) |

## Zap failure

1. Do not change the zap channel if the build, checks, revalidation, signing, or upload
   fails.
2. Leave the previous zap release current.
3. If stable installer bootstrap publication or read-back verification fails, leave the
   previous channel current and fix the Spaces write path before retrying.
4. If the immutable GitHub Release exists but the channel did not change, rerun the same
   publisher. It verifies and reuses the exact release before retrying metadata.
   If the original publisher cannot reach Spaces, dispatch `recover zap metadata` with
   that immutable release tag. A different trusted operator must approve its
   `channel-zap-recovery` deployment before the workflow receives storage credentials.
   The recovery workflow verifies the release and its signed metadata snapshots before
   restoring the reviewed bootstrap and four channel metadata objects.
5. If the release index changed but the channel did not, rerun the same publisher. The
   channel JSON remains the last write.
6. If the channel changed to a bad release, disable `zap publish`, preserve the signed
   bad metadata, and use a reviewed recovery change to publish newly signed channel and
   release-index files that point to the last good immutable version.
7. Keep the logs, GitHub Release and asset IDs, release record, source evidence, and
   staged signatures. Keep Space object version IDs only when versioning is enabled.

## Preview failure

1. Stop before changing the preview channel when possible.
2. Do not replace files in an immutable prerelease.
3. Fix the problem and publish a new prerelease version.
4. If needed, publish a new signed channel file with a higher sequence number that
   points to the previous good release.

## Stable failure

1. Stop stable publication.
2. Keep the source tag, GitHub draft or release ID, file IDs, checksums, approval, and
   external deployment IDs.
3. Do not rebuild the same version after approval.
4. If publication stopped halfway, continue from the recorded and already-verified
   files.
5. If a bad stable release is current, publish a new signed channel file with a higher
   sequence number that points to a verified older release.
6. Mark the bad version withdrawn or unsupported. Do not erase its evidence.

## Channel credential exposure

1. Disable that channel's workflow and environment.
2. Revoke its storage credential and signing key. Because the Spaces credential can
   replace `install.sh`, treat curl installations during the exposure window as
   potentially compromised even when signed metadata stayed valid.
3. Preserve object-access logs and compare the public installer with the reviewed
   `scripts/install-bootstrap` bytes before restoring it with a new credential.
4. Check whether the other channels were reachable.
5. Use the offline root process to authorize a replacement channel key.
6. Publish a new key-authority document and channel file, both with higher sequence or
   generation numbers.
7. Check that existing clients can move to the new key.
8. Publish an incident report appropriate to the impact.

## Offline root key exposure

Stop all publication. This breaks the base of client trust and may require a new Zolt
release plus recovery instructions delivered through another trusted path.

The root private key must never be stored in GitHub, DigitalOcean, an ordinary cloud
drive, or an unprotected maintainer laptop. Keep it hardware protected with a separate
offline backup.

## Repository compromise

1. Freeze `zoltsh/releases` and revoke active publication credentials.
2. Keep GitHub audit events and workflow logs.
3. Compare controller commits with reviewed commits and signed release records.
4. Rotate every operational secret that an affected workflow could read.
5. Restore trusted controller code through reviewed commits.
6. Require an independent review before stable publication resumes.
