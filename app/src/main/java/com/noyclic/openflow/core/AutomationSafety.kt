package com.noyclic.openflow.core

enum class ScreenSignal {
    LOADING, RESPONSE_COMPLETE, USAGE_LIMIT, SERVICE_ERROR, CAPTCHA, ACCESS_RESTRICTION, UNKNOWN
}

enum class GuardDecision {
    KEEP_MONITORING, WAIT_FOR_USER, PAUSE_AUTOMATION
}

data class ScreenSnapshot(
    val text: String,
    val hasStopGenerationControl: Boolean,
    val hasSendControl: Boolean,
    val visibleMessageCount: Int,
    val messageKeys: Set<String> = emptySet()
) {
    val normalizedText: String = text.lowercase()
    val fingerprint: String = normalizedText.hashCode().toString()

    fun signal(): ScreenSignal {
        if (containsAny(normalizedText, LIMIT_MARKERS)) return ScreenSignal.USAGE_LIMIT
        if (containsAny(normalizedText, CAPTCHA_MARKERS)) return ScreenSignal.CAPTCHA
        if (containsAny(normalizedText, RESTRICTION_MARKERS)) return ScreenSignal.ACCESS_RESTRICTION
        if (containsAny(normalizedText, ERROR_MARKERS)) return ScreenSignal.SERVICE_ERROR
        if (hasStopGenerationControl) return ScreenSignal.LOADING
        if (hasSendControl && visibleMessageCount > 0 && normalizedText.isNotBlank()) {
            return ScreenSignal.RESPONSE_COMPLETE
        }
        return ScreenSignal.UNKNOWN
    }

    private fun containsAny(value: String, markers: List<String>) = markers.any(value::contains)

    companion object {
        private val LIMIT_MARKERS = listOf(
            "you've reached your limit", "you have reached your limit", "usage limit",
            "daily limit", "try again after", "лимит сообщений", "достигнут лимит",
            "вы достигли лимита", "попробуйте позже", "слишком много запросов"
        )
        private val CAPTCHA_MARKERS = listOf("captcha", "verify you are human", "подтвердите, что вы человек")
        private val RESTRICTION_MARKERS = listOf(
            "access denied", "account restricted", "temporarily restricted",
            "доступ ограничен", "аккаунт ограничен"
        )
        private val ERROR_MARKERS = listOf(
            "something went wrong", "network error", "internal server error",
            "произошла ошибка", "ошибка сети", "не удалось загрузить"
        )
    }
}

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
