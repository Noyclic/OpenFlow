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
        listOf(
            ScreenSignal.SERVICE_ERROR, ScreenSignal.CAPTCHA,
            ScreenSignal.ACCESS_RESTRICTION, ScreenSignal.UNKNOWN
        ).forEach { signal ->
            assertEquals("Signal $signal must pause", GuardDecision.PAUSE_AUTOMATION, AutomationSafety.decide(signal))
        }
    }
}
