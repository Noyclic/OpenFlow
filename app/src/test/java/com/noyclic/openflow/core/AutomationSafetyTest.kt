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
    @Test fun stopGenerationMeansStillLoading() {
        assertEquals(ScreenSignal.LOADING, snapshot("assistant response", stop = true, send = true).signal())
    }
    @Test fun sendControlAndVisibleMessagesMeanResponseComplete() {
        assertEquals(ScreenSignal.RESPONSE_COMPLETE, snapshot("assistant response", send = true).signal())
    }
    private fun snapshot(text: String, stop: Boolean = false, send: Boolean = false) =
        ScreenSnapshot(text, stop, send, if (text.isBlank()) 0 else 2)
}
