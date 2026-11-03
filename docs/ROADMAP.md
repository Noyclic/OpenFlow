# OpenFlow Roadmap

A checked item means code exists in the repository. It does not imply that the feature has been verified on every device.

## Phase 0 — Project foundation
- [x] Add a native Android application structure.
- [x] Add a branded dashboard, settings and handoff workspace.
- [x] Add a GitHub Actions debug build and unit-test workflow.
- [x] Add conservative safety policy and unit tests.

## Phase 1 — Floating companion
- [x] Add a user-started floating overlay.
- [x] Add drag, pause, resume and stop controls.
- [x] Show an ongoing Android foreground notification.
- [ ] Verify overlay sizing and touch behavior on physical devices.
- [ ] Add a local activity log.

## Phase 2 — Conversation-state awareness
- [ ] Research compliant Android accessibility approaches and supported integrations.
- [ ] Detect response-complete, loading, error and usage-limit states.
- [ ] Add confidence thresholds and a manual-review mode.
- [ ] Verify ambiguous states always pause.

## Phase 3 — Context handoff
- [x] Add a handoff request template and clipboard workflow.
- [ ] Add structured project profiles and reviewed handoff history.
- [ ] Research reliable context capture without silently extracting private conversation content.
- [ ] Assist with new-chat navigation through supported, user-visible actions.

## Phase 4 — Controlled automation
- [x] Add a fail-closed safety decision model.
- [x] Keep automatic message sending disabled in the prototype.
- [ ] Add explicit per-action user approval.
- [ ] Stop on limits, service errors, CAPTCHA challenges, restrictions and uncertain screens.
- [ ] Research whether ChatGPT reasoning mode can be checked through supported UI controls.

## Phase 5 — Device testing and expansion
- [ ] Test on Redmi Note 13 4G (Android 15).
- [ ] Measure battery use, memory use and overlay reliability.
- [ ] Validate Android battery-management behavior.
- [ ] Evaluate other AI apps and supported integrations.
- [ ] Prepare a signed release after permissions, privacy and reliability reviews.
