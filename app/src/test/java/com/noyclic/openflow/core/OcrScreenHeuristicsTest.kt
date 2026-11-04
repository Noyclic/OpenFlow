package com.noyclic.openflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun detectsActualChatGptReplyPlaceholderAtBottom() {
        val result = OcrScreenHeuristics.classify(
            listOf(OcrTextLine("Ответить ChatGPT", 2_145, 2_205, 84f)),
            screenHeight = 2_400
        )
        assertTrue(result.hasComposerHint)
        assertEquals("CHAT_READY", result.sceneHint)
        assertTrue(result.controlHints.contains("composer anchor"))
    }

    @Test
    fun acceptsReplyOnlyPlaceholderOnlyAtVeryBottom() {
        val bottom = OcrScreenHeuristics.classify(
            listOf(OcrTextLine("Ответить", 2_110, 2_180, 83f)),
            screenHeight = 2_400
        )
        val messageBody = OcrScreenHeuristics.classify(
            listOf(OcrTextLine("Ответить", 820, 865, 95f)),
            screenHeight = 2_400
        )
        assertTrue(bottom.hasComposerHint)
        assertFalse(messageBody.hasComposerHint)
    }

    @Test
    fun doesNotMistakeMessageTextNearTopForComposer() {
        val result = OcrScreenHeuristics.classify(
            listOf(
                OcrTextLine("Сообщение", 110, 145, 95f),
                OcrTextLine("Ответить ChatGPT", 180, 220, 92f)
            ),
            screenHeight = 2_400
        )
        assertFalse(result.hasComposerHint)
    }

    @Test
    fun sendWordInConversationIsNotASendControl() {
        val result = OcrScreenHeuristics.classify(
            listOf(OcrTextLine("Отправить отчёт завтра", 540, 590, 94f)),
            screenHeight = 2_400
        )
        assertFalse(result.hasSendLabel)
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
        assertTrue(result.hasNewChatHint)
        assertTrue(result.controlHints.contains("new-chat label"))
        assertEquals("LOADING", result.sceneHint)
    }

    @Test
    fun detectsBottomVoiceInputHintWithoutCallingItSend() {
        val result = OcrScreenHeuristics.classify(
            listOf(OcrTextLine("Микрофон", 2_180, 2_220, 91f)),
            screenHeight = 2_400
        )
        assertTrue(result.hasVoiceInputHint)
        assertFalse(result.hasSendLabel)
    }

    @Test
    fun safetyWarningsRequireGoodConfidence() {
        val weak = OcrScreenHeuristics.classify(
            listOf(OcrTextLine("Проверка безопасности", 900, 960, 20f)),
            screenHeight = 2_400
        )
        val strong = OcrScreenHeuristics.classify(
            listOf(OcrTextLine("Проверка безопасности", 900, 960, 91f)),
            screenHeight = 2_400
        )
        assertFalse(weak.hasSecurityChallengeHint)
        assertTrue(strong.hasSecurityChallengeHint)
        assertEquals("SECURITY_CHALLENGE", strong.sceneHint)
    }

    @Test
    fun detectsUsageLimitAsSafetyState() {
        val result = OcrScreenHeuristics.classify(
            listOf(OcrTextLine("Достигнут лимит сообщений", 850, 910, 92f)),
            screenHeight = 2_400
        )
        assertTrue(result.hasUsageLimitHint)
        assertEquals("USAGE_LIMIT", result.sceneHint)
    }

    @Test
    fun contentFingerprintChangesWhenVisiblePageChanges() {
        val first = OcrScreenHeuristics.classify(
            listOf(
                OcrTextLine("The first visible paragraph has enough text to count.", 500, 550, 90f),
                OcrTextLine("And a second line provides a stable content sample.", 560, 610, 88f)
            ),
            screenHeight = 2_400
        )
        val same = OcrScreenHeuristics.classify(
            listOf(
                OcrTextLine("The first visible paragraph has enough text to count.", 500, 550, 90f),
                OcrTextLine("And a second line provides a stable content sample.", 560, 610, 88f)
            ),
            screenHeight = 2_400
        )
        val next = OcrScreenHeuristics.classify(
            listOf(
                OcrTextLine("A completely different paragraph is now visible here.", 500, 550, 90f),
                OcrTextLine("Another unique line confirms the page has moved on.", 560, 610, 88f)
            ),
            screenHeight = 2_400
        )
        assertEquals(first.contentFingerprint, same.contentFingerprint)
        assertNotEquals(first.contentFingerprint, next.contentFingerprint)
        assertEquals(2, first.visibleContentLineCount)
    }

    @Test
    fun emptyOrInvalidFrameReturnsUnknownSignals() {
        assertEquals("UNKNOWN", OcrScreenHeuristics.classify(emptyList(), 2_400).sceneHint)
        assertFalse(OcrScreenHeuristics.classify(
            listOf(OcrTextLine("Ответить ChatGPT", 2_100, 2_200, 90f)), 0
        ).hasComposerHint)
    }
}
