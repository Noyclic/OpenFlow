package com.noyclic.openflow.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextHandoffFlowTest {
    @Test fun automaticTransferCompletesOnlyAfterSummaryIsReady() {
        var stage = HandoffStage.IDLE
        stage = ContextHandoffFlow.transition(stage, HandoffEvent.LONG_CHAT_DETECTED)
        stage = ContextHandoffFlow.transition(stage, HandoffEvent.SUMMARY_REQUEST_SENT)
        stage = ContextHandoffFlow.transition(stage, HandoffEvent.SUMMARY_RESPONSE_COMPLETED)
        assertEquals(HandoffStage.SUMMARY_READY, stage)
        stage = ContextHandoffFlow.transition(stage, HandoffEvent.AUTO_TRANSFER_STARTED)
        assertEquals(HandoffStage.TRANSFERRING, stage)
        stage = ContextHandoffFlow.transition(stage, HandoffEvent.AUTO_TRANSFER_COMPLETED)
        assertEquals(HandoffStage.COMPLETED, stage)
    }

    @Test fun failedAutomaticTransferReturnsToRecoverableSummaryState() {
        var stage = HandoffStage.SUMMARY_READY
        stage = ContextHandoffFlow.transition(stage, HandoffEvent.AUTO_TRANSFER_STARTED)
        stage = ContextHandoffFlow.transition(stage, HandoffEvent.AUTO_TRANSFER_FAILED)
        assertEquals(HandoffStage.SUMMARY_READY, stage)
    }

    @Test fun transferCannotStartBeforeSummaryIsReady() {
        assertEquals(
            HandoffStage.REQUESTING_SUMMARY,
            ContextHandoffFlow.transition(HandoffStage.REQUESTING_SUMMARY, HandoffEvent.AUTO_TRANSFER_STARTED)
        )
    }

    @Test fun completedHandoffCanStartAnotherCycle() {
        assertEquals(
            HandoffStage.MANUAL_REVIEW,
            ContextHandoffFlow.transition(HandoffStage.COMPLETED, HandoffEvent.LONG_CHAT_DETECTED)
        )
    }
}
