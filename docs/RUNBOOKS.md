# Release incident runbooks

[README](../README.md) · [Architecture](./ARCHITECTURE.md) ·
[Repository setup](./SETUP.md) · [Security](../SECURITY.md)

Use these procedures when a release fails or release authority may be compromised.

> [!CAUTION]
> Preserve workflow logs, audit events, release records, file identities, and external
> deployment IDs before changing anything.

> [!IMPORTANT]
> Release maintainers receive private incident reports through
> [GitHub private vulnerability reporting](https://github.com/zoltsh/releases/security/advisories/new).
> Preserve the report and release evidence while following the procedures below.

## First response

| Signal | First action | Continue with |
| :--- | :--- | :--- |
| Zap build or verification fails | Leave the current zap release in place | [Zap failure](#zap-failure) |
| Preview publication fails | Stop before changing the channel | [Preview failure](#preview-failure) |
| Stable publication fails | Stop publication and preserve approval evidence | [Stable failure](#stable-failure) |
| Channel credential may be exposed | Disable that channel | [Channel credential exposure](#channel-credential-exposure) |
| Embedded release trust may be compromised | Stop affected publication paths | [Signing trust compromise](#signing-trust-compromise) |
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

1. Stop before changing the preview channel when possible. A candidate or immutable
   canary failure leaves public metadata untouched.
2. Do not replace files in an immutable prerelease or move its source tag.
3. If immutable publication succeeded but promotion failed, rerun the same trusted
   publication. It must verify and reuse the exact release and signed bytes before
   retrying the compare-and-swap promotion.
4. If the candidate itself is wrong, fix the problem and publish a higher prerelease
   version; never rebuild the same version with different bytes.
5. Preserve source-tag JSON, source CI evidence, the release record, signed metadata,
   canary logs, and public object state.

## Stable failure

1. Stop stable publication.
2. Keep the source tag, GitHub draft or release ID, file IDs, checksums, approval, and
   external deployment IDs.
3. Do not rebuild the same version after approval.
4. If publication stopped halfway, retry the same `stable publish` workflow with the
   same controller tag and `candidate_run_id`. It verifies and reuses the exact
   immutable release and signed files. If public metadata differs from both the
   recorded prior state and the intended publication, stop and investigate that
   difference; do not bypass the prior-state checks.
5. Users with an installer-managed installation can run `zolt self versions`, then
   `zolt self rollback` to select the recorded previous installed version, or
   `zolt self use VERSION` to select another installed version. Confirm with
   `zolt --version`. This changes only the local installation, not the public channel.
   Avoid `zolt self update` while the channel still points to the bad version.
6. Fix the defect and publish a higher patch version through the complete source CI,
   four-target candidate, approval, and canary flow. The normal stable publisher
   rejects channel downgrades. There is no stable channel rollback workflow or
   manifest sequence field. For the first stable release, a previous stable version
   may not exist, so use a tested fix-forward patch.
7. Mark the bad version withdrawn or unsupported. Do not erase its evidence or replace
   its immutable files.

## Channel credential exposure

1. Disable that channel's workflow and environment.
2. Revoke exposed storage credentials and remove exposed signing secrets from
   publication environments. Existing clients still trust their bundled public keys;
   removing a secret does not revoke that client trust. Because the Spaces credential
   can replace `install.sh`, treat curl installations during the exposure window as
   potentially compromised even when signed metadata stayed valid.
3. Preserve object-access logs and compare the public installer with the reviewed
   `scripts/install-bootstrap` bytes before restoring it with a new credential.
4. Check whether the other channels were reachable.
5. If a signing key was exposed, prepare reviewed controller and Zolt source changes
   that replace the affected bundled public key. Plan how existing clients receive
   that trust change through a verified recovery release or another trusted path.
   The current format has no online key-authority document or sequence field.
6. Restore the installer bootstrap only from reviewed bytes with an exact immutable
   installer URL and SHA-256. If signing trust changed, verify that its client and the
   controller trust the replacement key before signing new metadata or resuming
   publication.
7. Verify installation and update recovery with the changed trust. Do not assume
   clients will accept a replacement key merely because an environment secret changed.
8. Publish an incident report appropriate to the impact.

## Signing trust compromise

Stop affected publication paths. Current clients and the controller trust public keys
bundled in reviewed code; there is no implemented offline-root authorization or online
revocation mechanism. Recovery requires reviewed trust changes and a verified delivery
path to affected clients, following [channel credential exposure](#channel-credential-exposure).
Do not resume publication solely because a replacement private key has been uploaded.

## Repository compromise

1. Freeze `zoltsh/releases` and revoke active publication credentials.
2. Keep GitHub audit events and workflow logs.
3. Compare controller commits with reviewed commits and signed release records.
4. Rotate every operational secret that an affected workflow could read.
5. Restore trusted controller code through reviewed commits.
6. Require an independent review before stable publication resumes.
