# OpenFlow

<p align="center">
  <img src="assets/openflow-banner.svg" alt="OpenFlow — AI workflow automation" width="100%" />
</p>

<p align="center">
  <strong>Your AI. Your Workflow. Your Automation.</strong>
  <br />
  An Android companion for making long-running AI conversations easier to manage.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/status-early%20development-orange" alt="Early development" />
  <img src="https://img.shields.io/badge/platform-Android-green" alt="Android" />
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="MIT License" />
</p>

---

## What is OpenFlow?

**OpenFlow** is a planned Android assistant that helps people manage long-running conversations with AI apps. It is designed around a simple idea: let the user stay in control while repetitive conversation-management tasks become easier.

The first target is an Android floating assistant that can recognize visible conversation states, help prepare context handoffs between chats, and offer carefully controlled UI actions.

> **Project status:** OpenFlow is at the beginning of development. The repository currently contains project documentation and planning; the Android application and the features below are not implemented yet.

## Product vision

- **Conversation awareness** — recognize visible states such as an assistant response finishing or a usage-limit notice appearing.
- **Context handoff** — help prepare a concise, structured summary before moving work into a fresh chat.
- **Floating controls** — provide an optional overlay for quick, user-approved actions.
- **User-controlled automation** — make actions visible, interruptible, and easy to disable.
- **Responsible limits handling** — stop when a service reports a usage limit; never try to bypass it.
- **Extensible design** — keep the architecture open to supporting more AI apps over time.

## Planned capabilities

| Area | Intended behavior | Status |
|---|---|---|
| Floating overlay | Optional on-screen assistant controls | Planned |
| Screen-state detection | Detect visible completion, error, and limit states | Planned |
| Context handoff | Prepare a carry-over summary for a new conversation | Planned |
| User-approved actions | Execute supported UI actions only with clear user control | Planned |
| Safety pause | Stop automation on limits, errors, or uncertain states | Planned |
| Reasoning-mode reminder | Detect or remind about the selected ChatGPT mode where possible | Research |
| App integrations | Add support for other AI apps | Future |

OpenFlow will not assume that every app exposes the same controls. Some actions may require manual confirmation or may not be technically available.

## Design principles

1. **The user is in control.** Always provide a visible pause/stop control and clear automation status.
2. **No limit evasion.** Stop on usage limits, account restrictions, CAPTCHAs, and service errors. Do not switch accounts or attempt to defeat restrictions.
3. **No hidden collection.** Do not capture, store, or transmit screen content unless the user has clearly enabled a feature that needs it.
4. **Least privilege.** Request Android permissions only when a feature needs them, explain why, and make optional permissions revocable.
5. **Honest capability reporting.** Mark unavailable or uncertain UI states rather than guessing and clicking.
6. **Respect app boundaries.** Prefer supported integrations and public APIs when available; use Android accessibility features transparently and in accordance with platform and service rules.

## Platform

- Initial target: Android.
- First test device: Redmi Note 13 4G (Android 15).
- The minimum supported Android version and implementation stack will be decided during the initial technical setup.

## Development roadmap

See **[ROADMAP.md](docs/ROADMAP.md)** for the staged plan.

The first milestone is a safe, non-invasive prototype: an overlay with manual controls and a reliable pause state. Screen recognition and any automated actions come later, after their behavior can be tested.

## Privacy and permissions

OpenFlow may eventually use Android overlay and accessibility capabilities. These are powerful permissions. The app should explain each permission before requesting it, process screen information locally where practical, and avoid collecting credentials, private messages, or unrelated screen content.

Read the current [Privacy & Permissions notes](docs/PRIVACY.md).

## Contributing

The project is in its planning stage. Before proposing a feature, please read [CONTRIBUTING.md](CONTRIBUTING.md) and the roadmap.

## License

OpenFlow is released under the [MIT License](LICENSE).

---

<p align="center">
  <sub>OpenFlow — keep the workflow moving, without giving up control.</sub>
</p>
