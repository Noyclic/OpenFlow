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
    @Test fun errorsChallengesRestrictionsAndUnknownPause() {
        listOf(ScreenSignal.SERVICE_ERROR, ScreenSignal.CAPTCHA, ScreenSignal.ACCESS_RESTRICTION, ScreenSignal.UNKNOWN)
            .forEach { assertEquals(GuardDecision.PAUSE_AUTOMATION, AutomationSafety.decide(it)) }
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

    private fun snapshot(text: String) = ScreenSnapshot(text, false, false, 0)
}
