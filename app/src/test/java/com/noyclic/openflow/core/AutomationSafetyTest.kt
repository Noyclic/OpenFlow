package com.noyclic.openflow.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationSafetyTest {
    @Test fun loadingKeepsMonitoring() {
        assertEquals(GuardDecision.KEEP_MONITORING, AutomationSafety.decide(ScreenSignal.LOADING))
    }
    @Test fun completedResponseWaitsForUser() {
        assertEquals(GuardDecision.WAIT_FOR_USER, AutomationSafety.decide(ScreenSignal.RESPONSE_COMPLETE))
    }
    @Test fun usageLimitAlwaysPauses() {
        assertEquals(GuardDecision.PAUSE_AUTOMATION, AutomationSafety.decide(ScreenSignal.USAGE_LIMIT))
    }
    @Test fun explicitErrorsAndChallengesPause() {
        listOf(ScreenSignal.SERVICE_ERROR, ScreenSignal.CAPTCHA, ScreenSignal.ACCESS_RESTRICTION)
            .forEach { assertEquals(GuardDecision.PAUSE_AUTOMATION, AutomationSafety.decide(it)) }
    }
    @Test fun unknownAndReadyChatScreensKeepPassiveMonitoring() {
        assertEquals(GuardDecision.KEEP_MONITORING, AutomationSafety.decide(ScreenSignal.UNKNOWN))
        assertEquals(GuardDecision.KEEP_MONITORING, AutomationSafety.decide(ScreenSignal.CHAT_READY))
    }
    @Test fun composerIdentifiesReadyChatAndCompletedConversation() {
        assertEquals(ScreenSignal.CHAT_READY, ScreenSnapshot("Ask anything", false, false, 0,
            hasComposerControl = true).signal())
        assertEquals(ScreenSignal.RESPONSE_COMPLETE, ScreenSnapshot("A visible response that is long enough to count.",
            false, false, 1, hasComposerControl = true).signal())
    }
    @Test fun detectsRussianAndEnglishLimits() {
        listOf("You've reached your limit", "Достигнут лимит сообщений", "Попробуйте позже")
            .forEach { assertEquals(ScreenSignal.USAGE_LIMIT, snapshot(it).signal()) }
    }
    @Test fun captchaAndRestrictionAreNeverAutomated() {
        assertEquals(ScreenSignal.CAPTCHA, snapshot("Please complete CAPTCHA").signal())
        assertEquals(ScreenSignal.ACCESS_RESTRICTION, snapshot("Account restricted").signal())
    }
    @Test fun rejectedBackwardProbeFallsThroughToForwardScan() {
        val tracker = ScrollProgressTracker(maxSteps = 4)
        assertEquals(true, tracker.recordAttempt())
        assertEquals(
            ScrollProgressTracker.Result.CONTINUE,
            tracker.recordScrollRejected(ScrollProgressTracker.Direction.BACKWARD_PROBE)
        )
        assertEquals(true, tracker.recordAttempt())
        assertEquals(
            ScrollProgressTracker.Result.UNCERTAIN,
            tracker.recordScrollRejected(ScrollProgressTracker.Direction.FORWARD)
        )
    }
    @Test fun backwardMovementAllowsForwardScanToConfirmBottom() {
        val tracker = ScrollProgressTracker(maxSteps = 4)
        assertEquals(true, tracker.recordAttempt())
        assertEquals(
            ScrollProgressTracker.Result.CONTINUE,
            tracker.recordFingerprintChanged()
        )
        assertEquals(true, tracker.recordAttempt())
        assertEquals(
            ScrollProgressTracker.Result.REACHED_END,
            tracker.recordScrollRejected(ScrollProgressTracker.Direction.FORWARD)
        )
    }
    @Test fun unchangedContentAfterAcceptedScrollIsNotProofOfBottom() {
        val tracker = ScrollProgressTracker()
        tracker.recordFingerprintChanged()
        assertEquals(
            ScrollProgressTracker.Result.UNCERTAIN,
            tracker.recordFingerprintUnchanged()
        )
    }
    @Test fun scrollStepLimitIsBounded() {
        val tracker = ScrollProgressTracker(maxSteps = 2)
        assertEquals(true, tracker.recordAttempt())
        assertEquals(true, tracker.recordAttempt())
        assertEquals(false, tracker.recordAttempt())
    }

    @Test fun messageScanCountsUniqueBlocksAcrossOverlappingPages() {
        val scan = MessageScanAccumulator()
        scan.record(setOf("a", "b", "c"))
        scan.record(setOf("b", "c", "d"))
        assertEquals(4, scan.uniqueCount)
        scan.reset()
        assertEquals(0, scan.uniqueCount)
    }

    @Test fun handoffRequiresOrderedSummaryAndCopyStages() {
        var stage = HandoffStage.IDLE
        stage = ContextHandoffFlow.transition(stage, HandoffEvent.LONG_CHAT_DETECTED)
        assertEquals(HandoffStage.MANUAL_REVIEW, stage)
        stage = ContextHandoffFlow.transition(stage, HandoffEvent.SUMMARY_REQUEST_SENT)
        assertEquals(HandoffStage.REQUESTING_SUMMARY, stage)
        stage = ContextHandoffFlow.transition(stage, HandoffEvent.SUMMARY_RESPONSE_COMPLETED)
        assertEquals(HandoffStage.SUMMARY_READY, stage)
        stage = ContextHandoffFlow.transition(stage, HandoffEvent.CONTEXT_COPIED)
        assertEquals(HandoffStage.CONTEXT_COPIED, stage)
        assertEquals(HandoffStage.IDLE, ContextHandoffFlow.transition(stage, HandoffEvent.RESET))
    }

    @Test fun invalidHandoffTransitionCannotSkipSummaryRequest() {
        assertEquals(HandoffStage.IDLE,
            ContextHandoffFlow.transition(HandoffStage.IDLE, HandoffEvent.SUMMARY_RESPONSE_COMPLETED))
    }

    private fun snapshot(text: String) = ScreenSnapshot(text, false, false, 0)
}
