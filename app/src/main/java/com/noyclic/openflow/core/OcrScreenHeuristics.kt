package com.noyclic.openflow.core

internal data class OcrTextLine(
    val text: String,
    val top: Int,
    val bottom: Int,
    val confidence: Float
)

internal data class OcrScreenSignals(
    val hasComposerHint: Boolean,
    val hasSendLabel: Boolean,
    val hasStopLabel: Boolean,
    val controlHints: List<String>,
    val hasNewChatHint: Boolean = false,
    val hasVoiceInputHint: Boolean = false,
    val hasUsageLimitHint: Boolean = false,
    val hasSecurityChallengeHint: Boolean = false,
    val hasServiceErrorHint: Boolean = false,
    val visibleContentLineCount: Int = 0,
    val contentFingerprint: String = "",
    val sceneHint: String = "UNKNOWN"
)

/**
 * Privacy-preserving OCR classification.
 *
 * Text is inspected in memory only. The caller may keep booleans, counts and a
 * short-lived fingerprint, but must not persist recognized conversation text.
 * A text match is only a screen hint; it never proves that a control is clickable.
 */
internal object OcrScreenHeuristics {
    private const val MIN_CONTROL_CONFIDENCE = 28f
    private const val MIN_SAFETY_CONFIDENCE = 55f

    fun classify(lines: List<OcrTextLine>, screenHeight: Int): OcrScreenSignals {
        if (screenHeight <= 0 || lines.isEmpty()) return OcrScreenSignals(
            hasComposerHint = false,
            hasSendLabel = false,
            hasStopLabel = false,
            controlHints = emptyList()
        )

        val normalized = lines.map { line -> line to normalize(line.text) }
        fun hasToken(tokens: List<String>, confidence: Float = MIN_CONTROL_CONFIDENCE): Boolean =
            normalized.any { (line, text) ->
                line.confidence >= confidence && tokens.any(text::contains)
            }

        val composerHint = normalized.any { (line, text) ->
            if (line.confidence < MIN_CONTROL_CONFIDENCE) return@any false
            val center = (line.top + line.bottom) / 2f
            val nearBottom = line.bottom >= screenHeight * 0.66f &&
                center >= screenHeight * 0.70f
            val veryBottom = line.bottom >= screenHeight * 0.80f &&
                center >= screenHeight * 0.81f
            val knownPlaceholder = containsAny(
                text,
                "ask anything",
                "message chatgpt",
                "send a message",
                "type a message",
                "reply to chatgpt",
                "ответить chatgpt",
                "ответить чатгпт",
                "сообщени",
                "напишите",
                "задайте вопрос",
                "спросить",
                "введите запрос"
            )
            (nearBottom && knownPlaceholder) || (veryBottom && text == "ответить")
        }

        // Words in conversation content are not treated as a send button.
        // A visible text label must be in the composer band and match exactly.
        val sendLabels = setOf(
            "send", "send message", "send prompt",
            "отправить", "отправить сообщение"
        )
        val sendLabel = normalized.any { (line, text) ->
            val center = (line.top + line.bottom) / 2f
            line.confidence >= MIN_CONTROL_CONFIDENCE &&
                line.bottom >= screenHeight * 0.72f &&
                center >= screenHeight * 0.74f &&
                text in sendLabels
        }

        val stopLabel = hasToken(listOf(
            "stop generating", "stop response",
            "остановить генерацию", "остановить ответ"
        ))
        val newChatLabel = hasToken(listOf(
            "new chat", "new conversation",
            "новый чат", "новый диалог", "создать чат", "начать новый чат"
        ))
        val voiceInputHint = normalized.any { (line, text) ->
            val center = (line.top + line.bottom) / 2f
            line.confidence >= MIN_CONTROL_CONFIDENCE &&
                line.bottom >= screenHeight * 0.72f &&
                center >= screenHeight * 0.74f &&
                containsAny(text, "voice mode", "microphone", "диктовка", "микрофон", "голосовой режим")
        }
        val usageLimit = hasToken(listOf(
            "you have reached your limit", "you've reached your limit",
            "usage limit", "daily limit", "try again after",
            "достигнут лимит", "вы достигли лимита", "лимит сообщений",
            "попробуйте позже"
        ), MIN_SAFETY_CONFIDENCE)
        val securityChallenge = hasToken(listOf(
            "captcha", "verify you are human", "security check",
            "подтвердите что вы человек", "проверка безопасности"
        ), MIN_SAFETY_CONFIDENCE)
        val serviceError = hasToken(listOf(
            "something went wrong", "network error", "internal server error",
            "произошла ошибка", "ошибка сети", "не удалось загрузить"
        ), MIN_SAFETY_CONFIDENCE)

        // A short-lived hash allows scroll verification without retaining OCR text.
        val contentLines = normalized.filter { (line, text) ->
            line.confidence >= MIN_CONTROL_CONFIDENCE &&
                line.top >= screenHeight * 0.10f &&
                line.bottom <= screenHeight * 0.82f &&
                text.length >= 18 &&
                !containsAny(
                    text,
                    "new chat", "новый чат", "copy", "share",
                    "thumbs up", "thumbs down", "ответить chatgpt",
                    "stop generating", "остановить генерацию"
                )
        }.map { it.second }

        val contentFingerprint = if (contentLines.isEmpty()) "" else {
            val stableContent = contentLines.joinToString("|")
            "${contentLines.size}:${stableContent.hashCode().toString(16)}"
        }

        val hints = buildList {
            if (composerHint) add("composer anchor")
            if (sendLabel) add("send label")
            if (stopLabel) add("stop-generation label")
            if (newChatLabel) add("new-chat label")
            if (voiceInputHint) add("voice-input hint")
            if (usageLimit) add("usage-limit warning")
            if (securityChallenge) add("security challenge")
            if (serviceError) add("service error")
            if (contentLines.isNotEmpty()) add("visible conversation content")
        }

        val sceneHint = when {
            usageLimit -> "USAGE_LIMIT"
            securityChallenge -> "SECURITY_CHALLENGE"
            serviceError -> "SERVICE_ERROR"
            stopLabel -> "LOADING"
            composerHint && contentLines.size >= 2 -> "RESPONSE_VISIBLE"
            composerHint -> "CHAT_READY"
            else -> "UNKNOWN"
        }

        return OcrScreenSignals(
            hasComposerHint = composerHint,
            hasSendLabel = sendLabel,
            hasStopLabel = stopLabel,
            controlHints = hints,
            hasNewChatHint = newChatLabel,
            hasVoiceInputHint = voiceInputHint,
            hasUsageLimitHint = usageLimit,
            hasSecurityChallengeHint = securityChallenge,
            hasServiceErrorHint = serviceError,
            visibleContentLineCount = contentLines.size,
            contentFingerprint = contentFingerprint,
            sceneHint = sceneHint
        )
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace('ё', 'е')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun containsAny(value: String, vararg tokens: String): Boolean =
        tokens.any(value::contains)

    private fun containsAny(value: String, tokens: List<String>): Boolean =
        tokens.any(value::contains)
}
