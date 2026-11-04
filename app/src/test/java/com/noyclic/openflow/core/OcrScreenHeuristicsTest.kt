package com.noyclic.openflow.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrScreenHeuristicsTest {
    @Test
    fun detectsRussianComposerAndSendLabelNearBottom() {
        val result = OcrScreenHeuristics.classify(
            listOf(
                OcrTextLine("Напишите сообщение", 2_120, 2_165, 88f),
                OcrTextLine("Отправить", 2_180, 2_210, 91f)
            ),
            screenHeight = 2_400
        )

        assertTrue(result.hasComposerHint)
        assertTrue(result.hasSendLabel)
        assertFalse(result.hasStopLabel)
    }

    @Test
    fun doesNotMistakeMessageTextNearTopForComposer() {
        val result = OcrScreenHeuristics.classify(
            listOf(OcrTextLine("Сообщение", 110, 145, 95f)),
            screenHeight = 2_400
        )

        assertFalse(result.hasComposerHint)
    }

    @Test
    fun detectsStopGenerationAndNewChatLabels() {
        val result = OcrScreenHeuristics.classify(
            listOf(
                OcrTextLine("Остановить генерацию", 1_800, 1_840, 92f),
                OcrTextLine("Новый чат", 50, 90, 94f)
            ),
            screenHeight = 2_400
        )

        assertTrue(result.hasStopLabel)
        assertTrue(result.controlHints.contains("new-chat label"))
    }
}
