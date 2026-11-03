package com.noyclic.openflow.core

enum class ScreenSignal {
    LOADING, RESPONSE_COMPLETE, USAGE_LIMIT, SERVICE_ERROR, CAPTCHA, ACCESS_RESTRICTION, UNKNOWN
}
enum class GuardDecision {
    KEEP_MONITORING, WAIT_FOR_USER, PAUSE_AUTOMATION
}

/** Conservative policy for a future detector. It does not inspect screens or send messages. */
object AutomationSafety {
    fun decide(signal: ScreenSignal): GuardDecision = when (signal) {
        ScreenSignal.LOADING -> GuardDecision.KEEP_MONITORING
        ScreenSignal.RESPONSE_COMPLETE -> GuardDecision.WAIT_FOR_USER
        ScreenSignal.USAGE_LIMIT,
        ScreenSignal.SERVICE_ERROR,
        ScreenSignal.CAPTCHA,
        ScreenSignal.ACCESS_RESTRICTION,
        ScreenSignal.UNKNOWN -> GuardDecision.PAUSE_AUTOMATION
    }
}
