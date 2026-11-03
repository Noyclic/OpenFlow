# Contributing to OpenFlow

Thanks for your interest in OpenFlow! The project is in its early planning stage, so clear and carefully scoped contributions are especially useful.

## Before starting
1. Read the [README](README.md) and [roadmap](docs/ROADMAP.md).
2. Check existing issues before starting duplicate work.
3. Keep changes focused and explain the user-facing benefit.

## Development expectations
- Prefer small, reviewable changes.
- Do not add permissions without documenting why they are needed.
- Add tests for state detection and automation decisions.
- Treat uncertain screen states as a reason to pause, not click.
- Never implement quota bypass, account rotation, or restriction evasion.
- Do not add analytics or network transmission of screen content without explicit design approval and clear user consent.

## Pull requests
A useful pull request includes:
- What changed and why.
- Android versions/devices tested.
- Build and test results.
- Permissions added or changed.
- Known limitations and screenshots when UI changes are involved.

## Reporting security issues
Do not publish credentials, private conversation screenshots, or exploitable details in public issues. Use GitHub's private vulnerability reporting if it is enabled for this repository.
