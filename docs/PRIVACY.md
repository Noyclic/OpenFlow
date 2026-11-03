# Privacy & Permissions

OpenFlow is designed around visible, user-controlled Android workflows. Accessibility and overlay capabilities can expose sensitive information, so permissions are limited and explained.

## Permissions

- **Display over other apps** — required for the visible floating companion.
- **Post notifications** — used for the foreground notification and safety alerts.
- **Foreground service** — keeps the user-started overlay available.
- **Accessibility service (optional)** — when enabled by the user in Android Settings, observes the active ChatGPT app accessibility tree to detect response state and safety notices.

## Screen and conversation content

Accessibility inspection is restricted to the ChatGPT Android package. It is processed on-device. OpenFlow does not capture screenshots, use the camera or microphone, or upload conversation text. Conversation text is not retained as a history by the monitor. A continuation prompt is entered only when the user separately enables auto-continue. A structured summary request can also be sent once at the long-chat threshold, but only after a separate explicit opt-in. The monitor keeps short fingerprints of distinct visible text blocks in memory while scanning; it does not retain their message bodies as a history.

The accessibility service is powerful Android access. Enable it only if you understand and want the described behavior. You can disable it at any time in Android Accessibility settings.

## Safety

- A usage limit, CAPTCHA, access restriction, service error or unknown state pauses automation.
- OpenFlow never rotates accounts, refreshes to evade a quota, bypasses CAPTCHA, or defeats service restrictions.
- Auto-continue is off by default and has a per-session cap.
- Long-chat detection is approximate and based on visible text blocks discovered during a bounded scroll scan.
- The optional summary-request switch sends one message when that estimate reaches its threshold. It waits for a detected generation cycle and pauses for manual review; it does not automatically copy the response, create a new chat or inject the handoff.
- Keep status and a stop control visible.

## Reasoning mode

An external Android app cannot reliably force ChatGPT's model/reasoning selector. Choose the desired reasoning mode manually in ChatGPT. OpenFlow's continuation prompt can request careful analysis, but that is not the same as changing ChatGPT's model setting.

## Android platform notes

The overlay uses Android's special-use foreground-service category and system overlay permission. Device manufacturers may apply extra battery restrictions. Google Play distribution may require additional foreground-service declarations and policy review.
