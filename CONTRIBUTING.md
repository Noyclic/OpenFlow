# Contributing to OpenFlow

Thanks for helping improve OpenFlow. The project is an early prototype, so small, verifiable changes are especially useful.

## Before starting
1. Read [README](README.md), [roadmap](docs/ROADMAP.md) and [privacy notes](docs/PRIVACY.md).
2. Check existing issues before starting duplicate work.
3. Keep changes focused and explain the user-facing benefit.

## Engineering expectations
- Prefer native Android APIs unless a dependency has a clear benefit.
- Add tests for safety decisions and state transitions.
- Treat uncertain screen states as a reason to pause, not click.
- Document every new Android permission.
- Never implement quota bypass, account rotation, CAPTCHA bypass or restriction evasion.
- Do not silently collect or upload screenshots, credentials or conversation text.

## Pull requests

Include what changed, Android versions/devices tested, build and test results, permissions added or changed, and known limitations.

## Security reports

Do not publish credentials or private conversation screenshots in public issues. Use GitHub's private vulnerability reporting if enabled.
