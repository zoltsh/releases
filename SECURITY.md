# Security

[README](./README.md) · [Architecture](./docs/ARCHITECTURE.md) ·
[Incident runbooks](./docs/RUNBOOKS.md)

This repository controls what Zolt publishes and what users download. A problem in its
workflows, installer bootstrap, policy, signing, or release metadata may be a security
issue.

> [!WARNING]
> Do not open a public issue for a leaked credential, workflow bypass, replaced
> installer or release file, or channel rollback or replay problem.

## Report privately

Use [GitHub private vulnerability reporting](https://github.com/zoltsh/releases/security/advisories/new)
for `zoltsh/releases`. Private reporting is enabled.

Include the affected release or workflow, exact file or URL, observed behavior, and any
relevant timestamps. Do not include private keys or live credentials in the report.

## Sensitive material

Never commit a private signing key, storage credential, GitHub App private key, Maven
Central token, or personal access token.

## If a channel credential may have leaked

1. Stop that channel.
2. Keep its workflow, audit, and storage logs.
3. Revoke exposed storage credentials and remove exposed signing secrets from
   publication environments.
4. For an exposed signing key, follow the [embedded-key and client recovery procedure](./docs/RUNBOOKS.md#channel-credential-exposure).
   Current clients do not have an online key directory or revocation mechanism.
5. Resume publication only after replacement credentials, required client trust
   changes, and the pinned installer bootstrap have been verified.
6. Record the incident and the releases that may be affected.

The complete response procedures are in the
[incident runbooks](./docs/RUNBOOKS.md).
