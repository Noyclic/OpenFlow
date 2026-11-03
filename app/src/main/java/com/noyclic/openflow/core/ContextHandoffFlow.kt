package com.noyclic.openflow.core

enum class HandoffStage { IDLE, MANUAL_REVIEW, REQUESTING_SUMMARY, SUMMARY_READY, CONTEXT_COPIED }
enum class HandoffEvent { LONG_CHAT_DETECTED, SUMMARY_REQUEST_SENT, SUMMARY_RESPONSE_COMPLETED, SUMMARY_REQUEST_FAILED, CONTEXT_COPIED, RESET }

object ContextHandoffFlow {
    const val SUMMARY_REQUEST_PROMPT =
        "Prepare a complete continuation handoff for this conversation. Include the goal; exact current state; implemented work; important files and architecture; commands and test results; errors and unresolved issues; decisions and constraints; and the single best next step. Preserve concrete names and values. Do not invent successful tests or claim unfinished work is complete. Return a clearly structured summary suitable for pasting into a new chat."

    fun transition(stage: HandoffStage, event: HandoffEvent): HandoffStage = when (event) {
        HandoffEvent.RESET -> HandoffStage.IDLE
        HandoffEvent.LONG_CHAT_DETECTED ->
            if (stage == HandoffStage.IDLE || stage == HandoffStage.CONTEXT_COPIED) HandoffStage.MANUAL_REVIEW else stage
        HandoffEvent.SUMMARY_REQUEST_SENT ->
            if (stage == HandoffStage.MANUAL_REVIEW) HandoffStage.REQUESTING_SUMMARY else stage
        HandoffEvent.SUMMARY_RESPONSE_COMPLETED ->
            if (stage == HandoffStage.REQUESTING_SUMMARY) HandoffStage.SUMMARY_READY else stage
        HandoffEvent.SUMMARY_REQUEST_FAILED ->
            if (stage == HandoffStage.REQUESTING_SUMMARY) HandoffStage.MANUAL_REVIEW else stage
        HandoffEvent.CONTEXT_COPIED ->
            if (stage == HandoffStage.SUMMARY_READY || stage == HandoffStage.MANUAL_REVIEW) HandoffStage.CONTEXT_COPIED else stage
    }
}