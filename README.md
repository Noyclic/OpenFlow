# OpenFlow

<p align="center">
  <img src="assets/openflow-banner.svg" alt="OpenFlow — AI workflow automation" width="100%" />
</p>

<p align="center">
  <strong>Your AI. Your Workflow. Your Automation.</strong>
  <br />A user-controlled Android companion for long-running AI conversations.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/status-prototype-19B99A" alt="Prototype" />
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+" />
  <img src="https://img.shields.io/badge/build-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white" alt="GitHub Actions" />
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="MIT License" />
</p>

---

## What is OpenFlow?

**OpenFlow** is an Android assistant designed to make long-running conversations with AI apps easier to manage. It combines a floating companion, context-handoff tools and conservative automation safeguards.

The first test device is a **Redmi Note 13 4G running Android 15**.

## What's in the current prototype?

| Feature | State |
|---|---|
| Dark OpenFlow dashboard | Implemented |
| Settings and permission guidance | Implemented |
| Movable floating companion overlay | Implemented |
| Pause, resume and stop controls | Implemented |
| Ongoing Android foreground notification | Implemented |
| Context-handoff request and clipboard workspace | Implemented |
| Conservative safety decision policy + unit tests | Implemented |
| Automatic ChatGPT screen recognition | Not implemented |
| Automatic new-chat creation | Not implemented |
| Automatic message sending | Disabled |
| ChatGPT reasoning-mode control | Research required |
| AI provider/API integration | Not implemented |

The app does **not** read ChatGPT conversations or inspect other apps. The handoff screen helps you request a summary, paste it into OpenFlow, and copy it into a new chat manually. The safety policy is a tested foundation for a future detector; it is not connected to screen recognition yet.

## First run

1. Install the debug APK from the **OpenFlow-debug-apk** artifact in the latest successful GitHub Actions run.
2. Open OpenFlow and tap **Launch floating assistant**.
3. Grant **Display over other apps** when Android asks.
4. Grant notification permission when prompted, then tap Launch again.
5. Use the floating panel to pause, resume or stop it. You can also stop it from the ongoing notification.

Android or the device manufacturer may restrict overlays and foreground services based on system settings and battery management.

## Build from source

Requirements: JDK 17, Android SDK Platform 36 and Gradle 8.13.

```bash
gradle --no-daemon testDebugUnitTest
gradle --no-daemon assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions runs unit tests and builds a debug APK on pushes to `main`, pull requests and manual workflow dispatches. The APK is uploaded as a workflow artifact.

## Design principles

1. **User control first.** Keep active automation visible and provide immediate pause and stop controls.
2. **Fail closed.** Limits, errors, access restrictions, CAPTCHA challenges and unknown states must stop automation.
3. **No limit evasion.** Never rotate accounts, reset quotas, bypass CAPTCHAs or defeat service restrictions.
4. **Privacy by default.** Do not silently collect or upload screenshots, credentials or conversation content.
5. **Honest feature states.** Planned features are never presented as implemented.

## Roadmap and privacy

See [docs/ROADMAP.md](docs/ROADMAP.md), [docs/PRIVACY.md](docs/PRIVACY.md) and [CONTRIBUTING.md](CONTRIBUTING.md).

## License

OpenFlow is released under the [MIT License](LICENSE).
