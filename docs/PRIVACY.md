# Privacy & Permissions

OpenFlow is designed around visible, user-controlled Android workflows. Overlay and accessibility capabilities can expose sensitive information, so permissions must be limited and explained.

## Permissions in the current prototype

- **Display over other apps** — required only for the floating companion.
- **Post notifications** — used for the ongoing foreground-service notification while the companion is active.
- **Foreground service** — keeps the user-started overlay available while another app is in front.

The current app does not request accessibility, screenshot, camera, microphone, contacts or storage access.

## Screen and conversation content

The app does not inspect ChatGPT or other apps, capture screenshots, read conversation text or upload screen content. The handoff editor is temporary and uses the Android clipboard only when the user taps a copy button.

Future screen-state recognition requires a separate privacy and compatibility review.

## Safety

- Never bypass service limits, account restrictions, CAPTCHA challenges or access controls.
- Pause rather than guess when a screen state is unclear.
- Keep status and a stop control visible.
- Do not silently transmit conversation text or screenshots.

## Android platform notes

The overlay uses Android's special-use foreground-service category and system overlay permission. Device manufacturers may apply extra battery restrictions. Google Play distribution may require additional foreground-service declarations and policy review.
