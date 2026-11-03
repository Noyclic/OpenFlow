# OpenFlow Roadmap

This roadmap describes the intended development sequence. Items are goals, not claims that a feature already exists.

## Phase 0 — Project foundation
- [ ] Confirm Android project structure and build tooling.
- [ ] Add a minimal app shell and application identity.
- [ ] Establish code style, versioning, and CI build checks.
- [ ] Document supported Android versions and permission rationale.

## Phase 1 — Safe floating assistant
- [ ] Build the main activity and a minimal settings screen.
- [ ] Add an optional floating overlay with clear active/paused states.
- [ ] Add a prominent emergency stop control.
- [ ] Persist user preferences locally.
- [ ] Verify overlay behavior across app switching and screen rotation.

## Phase 2 — Conversation-state awareness
- [ ] Research Android accessibility APIs and supported alternatives.
- [ ] Detect only the UI states needed for the workflow: response complete, loading, error, and usage limit.
- [ ] Show confidence/uncertainty instead of acting on ambiguous states.
- [ ] Add a manual review mode before enabling any automatic action.

## Phase 3 — Context handoff
- [ ] Define a structured handoff format: project, current state, completed work, open issues, next steps, and important constraints.
- [ ] Let the user review and edit the handoff summary.
- [ ] Assist with opening a new chat and transferring the summary.
- [ ] Verify that a handoff never silently discards the previous conversation.

## Phase 4 — Controlled automation
- [ ] Add explicit per-action permissions and configurable safeguards.
- [ ] Require confirmation for actions that send messages or change conversations.
- [ ] Stop on usage limits, service errors, CAPTCHAs, or unexpected screens.
- [ ] Never attempt account switching, quota resets, or other restriction evasion.

## Phase 5 — Compatibility and expansion
- [ ] Test on the Redmi Note 13 4G (Android 15).
- [ ] Measure battery use, memory use, and overlay stability.
- [ ] Evaluate additional AI apps and supported integration methods.
- [ ] Publish a first test APK only after the safety and reliability checks pass.

## Release checklist
- [ ] Clean debug build succeeds.
- [ ] Core logic has automated tests.
- [ ] Permissions are explained in-app.
- [ ] Pause/stop behavior is tested.
- [ ] Limit/error detection fails safe.
- [ ] Privacy notes match actual app behavior.
