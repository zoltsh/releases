# Security

[README](./README.md) · [Architecture](./docs/ARCHITECTURE.md) ·
[Incident runbooks](./docs/RUNBOOKS.md)

This repository controls what Zolt publishes and what users download. A problem in its
workflows, policy, signing, or release metadata may be a security issue.

> [!WARNING]
> Do not open a public issue for a leaked credential, workflow bypass, replaced release
> file, or channel rollback or replay problem.

## Report privately

Private reporting must be ready before public releases are enabled:

1. Enable GitHub private vulnerability reporting for `zoltsh/releases`.
2. Configure and monitor `security@zolt.sh`.
3. Publish confirmed response expectations here.

Include the affected release or workflow, exact file or URL, observed behavior, and any
relevant timestamps. Do not include private keys or live credentials in the report.

## Sensitive material

Never commit a private signing key, storage credential, GitHub App private key, Maven
Central token, or personal access token.

## If a channel credential may have leaked

1. Stop that channel.
2. Keep its workflow, audit, and storage logs.
3. Revoke the exposed key or credential.
4. Authorize a replacement with the offline root key.
5. Publish a new signed channel file with a higher sequence number.
6. Record the incident and the releases that may be affected.

The complete response procedures are in the
[incident runbooks](./docs/RUNBOOKS.md).
