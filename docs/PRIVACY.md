# Privacy & Permissions

OpenFlow is intended to assist with visible UI workflows. Screen and accessibility permissions can expose sensitive information, so privacy must be designed in from the start.

## Permission principles

- Request overlay permission only for the floating assistant.
- Request accessibility access only if a specific, documented feature requires it.
- Explain what each permission enables before opening Android settings.
- Provide an obvious way to pause automation and disable optional capabilities.
- Do not request notification, storage, camera, microphone, or network permissions without a concrete feature requirement.

## Screen content

The planned screen-state detector should inspect only the minimum UI information needed to determine whether a response is complete, an error is visible, or a usage limit has been reached.

The initial implementation should prefer on-device processing. It must not silently upload screenshots, conversation text, credentials, or unrelated screen content.

## Automation safeguards

- Never attempt to bypass service limits, account restrictions, CAPTCHA challenges, or access controls.
- Stop when a usage-limit or restriction message is detected.
- Stop rather than guess when the screen state is ambiguous.
- Keep user-visible status and an immediate stop control available.

## Current status

This document describes requirements for future development. It does not mean that screen recognition, data processing, or automation has already been implemented.
