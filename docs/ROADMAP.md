# OpenFlow Roadmap

A checked item means code exists in the repository. It does not imply that the feature has been verified on every device.

## Phase 0 — Project foundation
- [x] Native Android application structure.
- [x] Branded dashboard, settings and handoff workspace.
- [x] GitHub Actions debug build and unit-test workflow.
- [x] Conservative safety policy and bilingual unit tests.

## Phase 1 — Floating companion
- [x] User-started floating overlay.
- [x] Drag, pause, resume and stop controls.
- [x] Ongoing Android foreground notification.
- [ ] Verify overlay sizing and touch behavior on Redmi Note 13 4G.

## Phase 2 — ChatGPT state awareness
- [x] Opt-in Android Accessibility service restricted to ChatGPT package.
- [x] Detect common English/Russian usage-limit, CAPTCHA, restriction and error text.
- [x] Detect likely response completion using visible UI controls.
- [x] Fail closed on unknown states.
- [ ] Validate selectors and false-positive rate on the physical device.
- [ ] Improve message-count and long-chat estimation.

## Phase 3 — Controlled continuation
- [x] Explicit user switch for auto-continue.
- [x] Stable-response delay before sending a continuation prompt.
- [x] Duplicate-response guard and 25-message session cap.
- [ ] Verify composer and send-button selectors on current ChatGPT Android UI.
- [ ] Add configurable continuation prompt and session controls.

## Phase 4 — Context handoff
- [x] Manual handoff request and clipboard workspace.
- [ ] Automatically ask ChatGPT for a structured handoff summary.
- [ ] Extract and review the summary before moving chats.
- [ ] Assist with opening a new chat and injecting the handoff.
- [ ] Recover safely when ChatGPT UI selectors change.

## Phase 5 — Reasoning preference
- [ ] Add a visible reminder/check for the desired reasoning mode.
- [ ] Research UI support for selecting a reasoning mode without guessing.
- Note: an external Android app cannot guarantee a ChatGPT model/reasoning selector setting. Manual selection remains necessary unless a supported integration becomes available.

## Phase 6 — Device testing and release
- [ ] Test on Redmi Note 13 4G (Android 15).
- [ ] Measure battery, memory and accessibility-service reliability.
- [ ] Validate Android battery-management behavior.
- [ ] Prepare a signed release after privacy and reliability reviews.
