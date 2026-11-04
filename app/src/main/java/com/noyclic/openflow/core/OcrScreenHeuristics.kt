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
    val controlHints: List<String>
)

/**
 * Conservative text-only hints. A matching word is not treated as proof that
 * a button is clickable, and these signals never authorize a gesture.
 */
internal object OcrScreenHeuristics {
    fun classify(lines: List<OcrTextLine>, screenHeight: Int): OcrScreenSignals {
        val normalized = lines.map { it to it.text.lowercase() }
        val composerHint = normalized.any { (line, text) ->
            val nearBottom = line.bottom > screenHeight * 0.58f &&
                (line.top + line.bottom) / 2f > screenHeight * 0.62f
            nearBottom && containsAny(
                text,
                "ask anything",
                "message chatgpt",
                "send a message",
                "type a message",
                "сообщени",
                "напишите",
                "задайте вопрос",
                "спросить",
                "введите запрос"
            )
        }
        val sendLabel = normalized.any { (_, text) ->
            containsAny(text, "send", "отправить")
        }
        val stopLabel = normalized.any { (_, text) ->
            containsAny(text, "stop generating", "stop response", "остановить генерацию", "остановить ответ")
        }
        val newChatLabel = normalized.any { (_, text) ->
            containsAny(text, "new chat", "новый чат")
        }

        val hints = buildList {
            if (composerHint) add("composer text")
            if (sendLabel) add("send label")
            if (stopLabel) add("stop-generation label")
            if (newChatLabel) add("new-chat label")
        }
        return OcrScreenSignals(composerHint, sendLabel, stopLabel, hints)
    }

    private fun containsAny(value: String, vararg tokens: String): Boolean =
        tokens.any { token -> value.contains(token) }
}
