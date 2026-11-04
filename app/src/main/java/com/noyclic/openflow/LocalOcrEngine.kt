package com.noyclic.openflow

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import com.noyclic.openflow.core.OcrScreenHeuristics
import com.noyclic.openflow.core.OcrTextLine
import java.io.File

internal data class OcrEngineResult(
    val status: String,
    val lineCount: Int = 0,
    val wordCount: Int = 0,
    val meanConfidence: Int = 0,
    val hasComposerHint: Boolean = false,
    val hasSendLabel: Boolean = false,
    val hasStopLabel: Boolean = false,
    val controlHints: List<String> = emptyList(),
    val elapsedMs: Long = 0L,
    val errorCode: String = "",
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
 * Single-thread-owned Tesseract engine. The two language models are packaged
 * in the APK and copied once into app-private storage before initialization.
 * No screenshot or recognized conversation text is written to disk.
 */
internal class LocalOcrEngine(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private var tess: TessBaseAPI? = null

    fun recognize(bitmap: Bitmap): OcrEngineResult {
        val startedAt = System.currentTimeMillis()
        return try {
            val engine = engine()
            engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            engine.setImage(bitmap)

            // Force Tesseract's recognition pass before requesting its iterator.
            // Some builds expose a null iterator until text recognition has run.
            val recognizedText = engine.getUTF8Text().orEmpty()
            val lines = ArrayList<OcrTextLine>(80)
            // Tesseract may return no ResultIterator when the frame has no
            // recognizable text. Treat it as an empty result, not a crash.
            val iterator = engine.getResultIterator()
                ?: return OcrEngineResult(
                    status = if (recognizedText.isBlank()) "NO_TEXT" else "TEXT_NO_ITERATOR",
                    lineCount = recognizedText.lineSequence().count { it.isNotBlank() },
                    wordCount = recognizedText.trim().split(Regex("\\s+")).count(String::isNotBlank),
                    elapsedMs = System.currentTimeMillis() - startedAt
                )
            try {
                val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
                iterator.begin()
                do {
                    if (iterator.isAtBeginningOf(level)) {
                        val text = iterator.getUTF8Text(level)?.trim().orEmpty()
                        if (text.isNotBlank()) {
                            val bounds = iterator.getBoundingRect(level)
                            if (bounds != null) {
                                lines += OcrTextLine(
                                    text = text,
                                    top = bounds.top,
                                    bottom = bounds.bottom,
                                    confidence = iterator.confidence(level)
                                )
                            }
                        }
                    }
                } while (iterator.next(level))
            } finally {
                iterator.delete()
            }

            val signals = OcrScreenHeuristics.classify(lines, bitmap.height)
            val wordCount = lines.sumOf { line ->
                line.text.trim().split(Regex("\\s+")).count(String::isNotBlank)
            }
            val confidence = if (lines.isEmpty()) 0 else
                lines.map { it.confidence }.average().toInt().coerceIn(0, 100)

            OcrEngineResult(
                status = if (lines.isEmpty()) "NO_TEXT" else "SUCCESS",
                lineCount = lines.size,
                wordCount = wordCount,
                meanConfidence = confidence,
                hasComposerHint = signals.hasComposerHint,
                hasSendLabel = signals.hasSendLabel,
                hasStopLabel = signals.hasStopLabel,
                controlHints = signals.controlHints,
                elapsedMs = System.currentTimeMillis() - startedAt,
                hasNewChatHint = signals.hasNewChatHint,
                hasVoiceInputHint = signals.hasVoiceInputHint,
                hasUsageLimitHint = signals.hasUsageLimitHint,
                hasSecurityChallengeHint = signals.hasSecurityChallengeHint,
                hasServiceErrorHint = signals.hasServiceErrorHint,
                visibleContentLineCount = signals.visibleContentLineCount,
                contentFingerprint = signals.contentFingerprint,
                sceneHint = signals.sceneHint
            )
        } catch (error: Exception) {
            OcrEngineResult(
                status = "FAILED",
                elapsedMs = System.currentTimeMillis() - startedAt,
                errorCode = error.javaClass.simpleName.ifBlank { "OCR_ERROR" }.take(64)
            )
        }
    }

    private fun engine(): TessBaseAPI {
        tess?.let { return it }

        val root = File(appContext.filesDir, "openflow_ocr")
        val tessdata = File(root, "tessdata")
        if (!tessdata.exists() && !tessdata.mkdirs()) {
            throw IllegalStateException("OCR_STORAGE_UNAVAILABLE")
        }

        listOf("eng", "rus").forEach { language ->
            val destination = File(tessdata, "$language.traineddata")
            if (!destination.isFile || destination.length() < 300_000L) {
                appContext.assets.open("tessdata/$language.traineddata").use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }

        val api = TessBaseAPI()
        if (!api.init(root.absolutePath, "rus+eng", TessBaseAPI.OEM_LSTM_ONLY)) {
            api.recycle()
            throw IllegalStateException("OCR_MODEL_INIT_FAILED")
        }
        api.setVariable("preserve_interword_spaces", "1")
        tess = api
        return api
    }

    override fun close() {
        tess?.recycle()
        tess = null
    }
}
