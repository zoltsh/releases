# Contributing to Zolt releases

[README](./README.md) · [Architecture](./docs/ARCHITECTURE.md) ·
[Setup](./docs/SETUP.md) · [Security](./SECURITY.md)

This repository decides what Zolt publishes and what users download. Changes should be
small, explicit, and easy to verify.

## Start in the right repository

- Product code and user-facing Zolt changes belong in
  [`zoltsh/zolt`](https://github.com/zoltsh/zolt).
- Release policy, workflows, schemas, and publication controls belong here.

## Guardrails

1. Keep unrelated cleanup out of release changes.
2. Pin every external GitHub Action to a full 40-character commit SHA.
3. Never give a build job a publication secret.
4. Never run candidate source or candidate binaries in a publication job.
5. Update policy, schemas, tests, and runbooks together when a release contract changes.

## Validate your change

Run the complete local check:

```sh
scripts/check
```

The check builds and tests the Java controller, validates the Zolt lockfile and
dependency policy, packages the application, and runs the repository policy check from
the packaged artifact.

## Open a pull request

Explain what changed, why it is safe, and whether publication access changed. Controller
and policy changes follow the protected review rules in
[Repository setup](./docs/SETUP.md).
