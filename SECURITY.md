# Security policy

GitMax handles access tokens and SSH keys, so security reports are taken seriously.

## Reporting a vulnerability

Please do **not** open a public issue for a security problem. Use GitHub's private reporting instead:
**Security › Report a vulnerability** on the repository page. Describe what you found, how to reproduce it and, if you
can, what you would expect instead. You will get an answer as soon as possible.

## What to expect from the app

- Tokens and SSH private keys are stored only in the Android Keystore-backed `SecretVault`, and never in logs, remote URLs,
  `.git/config`, backups or error messages.
- Credentials are only sent to the host of the account they belong to.
- Connections use HTTPS (or SSH). Plain `http://` is possible only for a self-hosted server that you explicitly connected that way and confirmed, never for github.com or gitlab.com, and the token is never sent over HTTP to any other server.
- Cloud backup and device transfer are disabled; there is no analytics and no crash reporting.

Details are in the [README](README.md#security-and-privacy).

## Supported versions

Only the latest commit on `main` is supported.
