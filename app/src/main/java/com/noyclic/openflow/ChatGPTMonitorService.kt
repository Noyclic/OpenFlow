package com.noyclic.openflow

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.noyclic.openflow.core.AutomationSafety
import com.noyclic.openflow.core.ContextHandoffFlow
import com.noyclic.openflow.core.HandoffEvent
import com.noyclic.openflow.core.HandoffStage
import com.noyclic.openflow.core.MessageScanAccumulator
import com.noyclic.openflow.core.GuardDecision
import com.noyclic.openflow.core.ScreenSignal
import com.noyclic.openflow.core.ScreenSnapshot
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opt-in, on-device observer for the official ChatGPT Android app.
 * It only inspects the active accessibility tree after the user enables this service in Android Settings.
 */
class ChatGPTMonitorService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val state by lazy { getSharedPreferences("openflow_state", MODE_PRIVATE) }
    private var pendingFingerprint: String? = null
    private var lastAutoSentFingerprint: String? = null
    private var lastScannedFingerprint: String? = null
    private var stableCheck: Runnable? = null
    private var seekingLatest = false
    private var lastScrollFingerprint: String? = null
    private var scrollCheck: Runnable? = null
    private var scrollDirection = com.noyclic.openflow.core.ScrollProgressTracker.Direction.BACKWARD_PROBE
    private var scrollTracker = com.noyclic.openflow.core.ScrollProgressTracker()
    private val messageScan = MessageScanAccumulator()
    private var handoffTimeout: Runnable? = null
    private var lastDiagnosticsPackage: String? = null
    private var lastDiagnosticsWriteAt: Long = 0L
    private var lastUncertainFingerprint: String? = null
    private var lastScreenshotProbeAt: Long = 0L
    private val ocrInFlight = AtomicBoolean(false)
    private val ocrExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "OpenFlow-OCR").apply { isDaemon = true }
    }
    // Accessed only on ocrExecutor.
    private var localOcrEngine: LocalOcrEngine? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 350
            // Observe package transitions too: a strict package filter hides the very
            // mismatch this diagnostic is intended to detect.
            packageNames = null
        }
        state.edit()
            .putBoolean("accessibility_connected", true)
            .putString("diag_service_state", "connected")
            .putString("diag_expected_package", CHATGPT_PACKAGE)
            .apply()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()
        val eventPackage = event.packageName?.toString().orEmpty()
        // Other apps contribute package/event metadata only; their trees are never inspected.
        if (eventPackage != packageName && (
            eventPackage != lastDiagnosticsPackage || now - lastDiagnosticsWriteAt >= 5_000L ||
            eventPackage == CHATGPT_PACKAGE
        )) {
            lastDiagnosticsPackage = eventPackage
            lastDiagnosticsWriteAt = now
            state.edit()
                .putString("diag_last_event_package", eventPackage.ifBlank { "(empty)" })
                .putString("diag_last_event_class", event.className?.toString().orEmpty())
                .putInt("diag_last_event_type", event.eventType)
                .putLong("diag_last_event_time", now)
                .apply()
        }
        if (eventPackage != CHATGPT_PACKAGE) return
        state.edit()
            .putString("diag_last_chatgpt_event_package", eventPackage)
            .putString("diag_last_chatgpt_event_class", event.className?.toString().orEmpty())
            .putInt("diag_last_chatgpt_event_type", event.eventType)
            .putLong("diag_last_chatgpt_event_time", now)
            .apply()

        val root = findChatGptRoot()
        if (root == null) {
            state.edit()
                .putString("diag_chatgpt_root_status", "NOT_FOUND")
                .putString("diag_last_signal", "CHATGPT_EVENT_WITHOUT_WINDOW")
                .putLong("diag_chatgpt_tree_time", now)
                .apply()
            if (state.getBoolean("automation_enabled", false) &&
                handoffStage() != HandoffStage.TRANSFERRING
            ) {
                state.edit()
                    .putString("monitor_status", "Событие ChatGPT получено, но дерево окна пока недоступно. Жду…")
                    .apply()
            }
            return
        }
        recordChatGptDiagnostics(root)
        probeChatGptScreenshot()
        // Classification is also collected while automation is paused so the user
        // can diagnose visibility without enabling any automatic actions.
        val snapshot = inspect(root)
        val signal = snapshot.signal()
        state.edit()
            .putString("diag_last_signal", signal.name)
            .putBoolean("diag_has_composer", snapshot.hasComposerControl)
            .putBoolean("diag_has_send", snapshot.hasSendControl)
            .putInt("diag_message_blocks", snapshot.visibleMessageCount)
            .putLong("diag_signal_time", now)
            .apply()

        if (!state.getBoolean("automation_enabled", false)) return
        if (handoffStage() == HandoffStage.TRANSFERRING) return

        when (AutomationSafety.decide(signal)) {
            GuardDecision.PAUSE_AUTOMATION -> pauseFor(signal)
            GuardDecision.KEEP_MONITORING -> {
                if (signal == ScreenSignal.LOADING &&
                    handoffStage() == HandoffStage.REQUESTING_SUMMARY
                ) {
                    state.edit().putBoolean("handoff_summary_generation_seen", true).apply()
                }
                pendingFingerprint = null
                stableCheck?.let(handler::removeCallbacks)
                stableCheck = null
                cancelSeekLatest()
                val status = when (signal) {
                    ScreenSignal.LOADING -> "Ответ генерируется…"
                    ScreenSignal.CHAT_READY -> "ChatGPT найден. Жду сообщения…"
                    else -> "Экран пока распознан не полностью. Наблюдаю без автодействий."
                }
                state.edit().putString("monitor_status", status).apply()
            }
            GuardDecision.WAIT_FOR_USER -> {
                if (handoffStage() == HandoffStage.REQUESTING_SUMMARY &&
                    !state.getBoolean("handoff_summary_generation_seen", false)
                ) {
                    state.edit().putString("monitor_status", "Запрос сводки отправлен. Ожидаю начала ответа ChatGPT…").apply()
                    return
                }
                if (!seekingLatest && snapshot.fingerprint != lastScannedFingerprint &&
                    snapshot.fingerprint != lastUncertainFingerprint
                ) beginSeekLatest(snapshot)
            }
        }
    }

    private fun findChatGptRoot(): AccessibilityNodeInfo? {
        val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
        if (activeRoot?.packageName?.toString() == CHATGPT_PACKAGE) return activeRoot
        val availableWindows = runCatching { windows }.getOrNull() ?: return null
        for (window in availableWindows) {
            val candidate = runCatching { window.root }.getOrNull() ?: continue
            if (candidate.packageName?.toString() == CHATGPT_PACKAGE) return candidate
        }
        return null
    }

    /**
     * Capture only the official ChatGPT window, run Russian+English OCR locally,
     * and retain diagnostic signals only. Pixel data and conversation text are
     * discarded after recognition and never leave the device.
     */
    private fun probeChatGptScreenshot() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            state.edit()
                .putString("diag_screenshot_status", "UNSUPPORTED_API")
                .putString("diag_ocr_status", "UNSUPPORTED_API")
                .putLong("diag_screenshot_time", System.currentTimeMillis())
                .apply()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastScreenshotProbeAt < SCREENSHOT_OCR_INTERVAL_MS) return
        if (!ocrInFlight.compareAndSet(false, true)) return
        lastScreenshotProbeAt = now
        state.edit()
            .putString("diag_screenshot_status", "REQUESTING")
            .putString("diag_ocr_status", "CAPTURING")
            .putLong("diag_screenshot_time", now)
            .apply()

        try {
            val callbackExecutor = java.util.concurrent.Executor { command ->
                runCatching { ocrExecutor.execute(command) }
            }
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                callbackExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        var bitmap: Bitmap? = null
                        val width = buffer.width
                        val height = buffer.height
                        try {
                            val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                ?: throw IllegalStateException("BITMAP_WRAP_FAILED")
                            try {
                                bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            } finally {
                                hardwareBitmap.recycle()
                            }
                            val softwareBitmap = bitmap
                                ?: throw IllegalStateException("BITMAP_COPY_FAILED")
                            val engine = localOcrEngine
                                ?: LocalOcrEngine(applicationContext).also { localOcrEngine = it }
                            val ocr = engine.recognize(softwareBitmap)
                            handler.post {
                                ocrInFlight.set(false)
                                state.edit()
                                    .putString("diag_screenshot_status", "SUCCESS")
                                    .putInt("diag_screenshot_width", width)
                                    .putInt("diag_screenshot_height", height)
                                    .putInt("diag_screenshot_error", 0)
                                    .putLong("diag_screenshot_time", System.currentTimeMillis())
                                    .putString("diag_ocr_status", ocr.status)
                                    .putInt("diag_ocr_lines", ocr.lineCount)
                                    .putInt("diag_ocr_words", ocr.wordCount)
                                    .putInt("diag_ocr_confidence", ocr.meanConfidence)
                                    .putBoolean("diag_ocr_composer", ocr.hasComposerHint)
                                    .putBoolean("diag_ocr_send", ocr.hasSendLabel)
                                    .putBoolean("diag_ocr_stop", ocr.hasStopLabel)
                                    .putString("diag_ocr_controls", ocr.controlHints.joinToString(" | "))
                                    .putLong("diag_ocr_duration", ocr.elapsedMs)
                                    .putString("diag_ocr_error", ocr.errorCode)
                                    .putLong("diag_ocr_time", System.currentTimeMillis())
                                    .apply()
                            }
                        } catch (error: Throwable) {
                            val errorName = error.javaClass.simpleName.ifBlank { "OCR_ERROR" }
                            handler.post {
                                ocrInFlight.set(false)
                                state.edit()
                                    .putString("diag_screenshot_status", "SUCCESS")
                                    .putInt("diag_screenshot_width", width)
                                    .putInt("diag_screenshot_height", height)
                                    .putInt("diag_screenshot_error", 0)
                                    .putString("diag_ocr_status", "FAILED")
                                    .putString("diag_ocr_error", errorName.take(64))
                                    .putLong("diag_ocr_time", System.currentTimeMillis())
                                    .apply()
                            }
                        } finally {
                            bitmap?.recycle()
                            runCatching { buffer.close() }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        handler.post {
                            ocrInFlight.set(false)
                            state.edit()
                                .putString("diag_screenshot_status", "FAILED")
                                .putInt("diag_screenshot_error", errorCode)
                                .putString("diag_ocr_status", "SCREENSHOT_FAILED")
                                .putString("diag_ocr_error", "SCREENSHOT_ERROR_$errorCode")
                                .putLong("diag_screenshot_time", System.currentTimeMillis())
                                .putLong("diag_ocr_time", System.currentTimeMillis())
                                .apply()
                        }
                    }
                }
            )
        } catch (error: Throwable) {
            ocrInFlight.set(false)
            state.edit()
                .putString("diag_screenshot_status", "REQUEST_EXCEPTION")
                .putString("diag_ocr_status", "FAILED")
                .putString("diag_ocr_error", error.javaClass.simpleName.take(64))
                .putLong("diag_screenshot_time", System.currentTimeMillis())
                .putLong("diag_ocr_time", System.currentTimeMillis())
                .apply()
        }
    }

    private fun inspect(root: AccessibilityNodeInfo): ScreenSnapshot {
        val texts = ArrayList<String>(96)
        var stop = false
        var send = false
        var composer = false
        val messageKeys = linkedSetOf<String>()
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 24 || texts.size >= 220) return
            val value = node.text?.toString()?.trim().orEmpty()
            val description = node.contentDescription?.toString()?.trim().orEmpty()
            val hint = node.hintText?.toString()?.trim().orEmpty()
            val viewId = node.viewIdResourceName.orEmpty().lowercase()
            val label = (value + " " + description).lowercase()
            if (value.isNotEmpty()) {
                texts += value
                if (value.length >= 24 && !node.isEditable && node.isVisibleToUser) {
                    val normalized = value.lowercase().replace(Regex("\\s+"), " ").trim()
                    messageKeys += normalized.hashCode().toString(16)
                }
            }
            if (description.isNotEmpty()) texts += description
            if (node.isVisibleToUser && node.isEnabled) {
                if (node.isEditable || (node.isFocusable && (
                    hint.contains("message", ignoreCase = true) ||
                    hint.contains("ask anything", ignoreCase = true) ||
                    hint.contains("сообщ", ignoreCase = true) ||
                    viewId.contains("composer") || viewId.contains("message_input") ||
                    viewId.contains("prompt_input")
                ))) composer = true
                if (label.contains("stop generating") || label.contains("остановить генерацию") ||
                    label.contains("stop response") || label.contains("остановить ответ") ||
                    viewId.contains("stop_generating") || viewId.contains("stop-generation")
                ) stop = true
                val isSendLabel = label.trim() in setOf(
                    "send", "send message", "send prompt", "отправить", "отправить сообщение"
                )
                if ((isSendLabel || viewId.contains("send")) && node.isClickable) send = true
            }
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        visit(root, 0)
        return ScreenSnapshot(texts.joinToString("\n"), stop, send, messageKeys.size, messageKeys, composer)
    }

    /**
     * Local, privacy-conscious visibility diagnostics. For ChatGPT only, collect
     * structure and short UI labels; long conversation text is deliberately omitted.
     */
    private fun recordChatGptDiagnostics(root: AccessibilityNodeInfo) {
        var nodes = 0
        var textNodes = 0
        var editableNodes = 0
        var clickableNodes = 0
        var scrollableNodes = 0
        val labels = linkedSetOf<String>()
        val viewIds = linkedSetOf<String>()
        val classes = linkedSetOf<String>()

        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 24 || nodes >= 500) return
            nodes++
            if (!node.isVisibleToUser) return
            val value = node.text?.toString()?.trim().orEmpty()
            val description = node.contentDescription?.toString()?.trim().orEmpty()
            if (value.isNotEmpty()) textNodes++
            if (node.isEditable) editableNodes++
            if (node.isClickable) clickableNodes++
            if (node.isScrollable) scrollableNodes++

            val viewId = node.viewIdResourceName.orEmpty()
            val isControl = node.isClickable || node.isFocusable || node.isEditable ||
                viewId.contains("composer", ignoreCase = true) ||
                viewId.contains("send", ignoreCase = true) ||
                viewId.contains("button", ignoreCase = true) ||
                viewId.contains("input", ignoreCase = true)
            // Store concise control labels only, never conversation message text.
            if (isControl && value.length in 1..44) labels.add(value)
            if (isControl && description.length in 1..56) labels.add(description)
            if (viewId.isNotBlank()) viewIds.add(viewId)
            node.className?.toString()?.takeIf { it.isNotBlank() }?.let(classes::add)

            for (index in 0 until node.childCount) visit(node.getChild(index), depth + 1)
        }

        visit(root, 0)
        state.edit()
            .putString("diag_chatgpt_root_package", root.packageName?.toString().orEmpty())
            .putString("diag_chatgpt_root_class", root.className?.toString().orEmpty())
            .putString("diag_chatgpt_root_status", "VISIBLE")
            .putInt("diag_chatgpt_nodes", nodes)
            .putInt("diag_chatgpt_text_nodes", textNodes)
            .putInt("diag_chatgpt_editable_nodes", editableNodes)
            .putInt("diag_chatgpt_clickable_nodes", clickableNodes)
            .putInt("diag_chatgpt_scrollable_nodes", scrollableNodes)
            .putString("diag_chatgpt_labels", labels.take(35).joinToString(" | "))
            .putString("diag_chatgpt_view_ids", viewIds.take(25).joinToString(" | "))
            .putString("diag_chatgpt_classes", classes.take(12).joinToString(" | "))
            .putLong("diag_chatgpt_tree_time", System.currentTimeMillis())
            .apply()
    }

    private fun pauseFor(signal: ScreenSignal) {
        state.edit()
            .putBoolean("automation_enabled", false)
            .putBoolean("auto_continue_enabled", false)
            .putString("monitor_status", when (signal) {
                ScreenSignal.USAGE_LIMIT -> "Достигнут лимит. Автоматизация приостановлена."
                ScreenSignal.CAPTCHA -> "Проверка безопасности. Требуется твоё действие."
                ScreenSignal.ACCESS_RESTRICTION -> "Ограничение доступа. Автоматизация остановлена."
                ScreenSignal.SERVICE_ERROR -> "Ошибка ChatGPT. Автоматизация приостановлена."
                else -> "Не удалось надёжно определить экран. Пауза."
            })
            .putString("stop_reason", signal.name)
            .apply()
        pendingFingerprint = null
        stableCheck?.let(handler::removeCallbacks)
        stableCheck = null
        cancelSeekLatest()
        if (handoffStage() == HandoffStage.REQUESTING_SUMMARY) {
            cancelHandoffTimeout()
            transitionHandoff(HandoffEvent.SUMMARY_REQUEST_FAILED)
        }
        showStatusNotification(state.getString("monitor_status", "OpenFlow paused") ?: "OpenFlow paused")
    }

    private fun handoffStage(): HandoffStage {
        val stored = state.getString("handoff_stage", HandoffStage.IDLE.name) ?: HandoffStage.IDLE.name
        return runCatching { HandoffStage.valueOf(stored) }.getOrDefault(HandoffStage.IDLE)
    }

    private fun transitionHandoff(event: HandoffEvent) {
        state.edit().putString("handoff_stage", ContextHandoffFlow.transition(handoffStage(), event).name).apply()
    }

    private fun requestHandoff(root: AccessibilityNodeInfo, messageCount: Int) {
        if (state.getBoolean("handoff_required", false)) return
        transitionHandoff(HandoffEvent.LONG_CHAT_DETECTED)
        state.edit()
            .putBoolean("handoff_required", true)
            .putInt("last_visible_message_count", messageCount)
            .putBoolean("auto_continue_enabled", false)
            .apply()
        if (state.getBoolean("auto_handoff_request_enabled", false)) {
            state.edit()
                .putBoolean("handoff_summary_generation_seen", false)
                .putString("monitor_status", "Длинный чат найден. Отправляю запрос на сводку…")
                .apply()
            if (sendTextMessage(root, ContextHandoffFlow.SUMMARY_REQUEST_PROMPT)) {
                transitionHandoff(HandoffEvent.SUMMARY_REQUEST_SENT)
                state.edit().putString("monitor_status", "Запрос сводки отправлен. Ожидаю ответ ChatGPT…").apply()
                scheduleHandoffTimeout()
                showStatusNotification("Запросил сводку. После ответа проверь её в OpenFlow.")
            } else {
                state.edit()
                    .putBoolean("automation_enabled", false)
                    .putString("monitor_status", "Не удалось безопасно отправить запрос сводки. Проверь поле ввода вручную.")
                    .apply()
                showStatusNotification("Не удалось отправить запрос сводки. Автоматизация остановлена.")
            }
        } else {
            state.edit()
                .putBoolean("automation_enabled", false)
                .putString("monitor_status", "Чат длинный. Подготовь перенос контекста вручную.")
                .apply()
            showStatusNotification("Чат длинный — пора подготовить перенос контекста.")
        }
    }

    private fun scheduleHandoffTimeout() {
        handoffTimeout?.let(handler::removeCallbacks)
        val timeout = Runnable {
            if (handoffStage() != HandoffStage.REQUESTING_SUMMARY) return@Runnable
            transitionHandoff(HandoffEvent.SUMMARY_REQUEST_FAILED)
            state.edit()
                .putBoolean("automation_enabled", false)
                .putBoolean("auto_continue_enabled", false)
                .putString("monitor_status", "Не удалось подтвердить готовность сводки за 3 минуты. Проверь ChatGPT вручную.")
                .apply()
            showStatusNotification("Ожидание сводки завершено по тайм-ауту. Проверь ответ вручную.")
            handoffTimeout = null
        }
        handoffTimeout = timeout
        handler.postDelayed(timeout, HANDOFF_TIMEOUT_MS)
    }

    private fun cancelHandoffTimeout() {
        handoffTimeout?.let(handler::removeCallbacks)
        handoffTimeout = null
    }

    /**
     * Move the ChatGPT conversation to its newest visible content before making
     * a completion/handoff decision. Scrolling is bounded and stops when the
     * accessibility tree no longer changes, no scrollable container remains,
     * or the safety limit is reached.
     */
    private fun beginSeekLatest(snapshot: ScreenSnapshot) {
        seekingLatest = true
        lastUncertainFingerprint = null
        messageScan.reset()
        messageScan.record(snapshot.messageKeys)
        scrollDirection = com.noyclic.openflow.core.ScrollProgressTracker.Direction.BACKWARD_PROBE
        scrollTracker = com.noyclic.openflow.core.ScrollProgressTracker(MAX_SCROLL_STEPS)
        lastScrollFingerprint = snapshot.fingerprint
        state.edit().putString("monitor_status", "Проверяю последние сообщения…").apply()
        seekLatestStep()
    }

    private fun seekLatestStep() {
        if (!state.getBoolean("automation_enabled", false)) {
            cancelSeekLatest()
            return
        }
        val root = findChatGptRoot()
        if (root == null) {
            finishSeekLatest(pause = true)
            return
        }
        if (!scrollTracker.recordAttempt()) {
            finishSeekLatest(pause = true)
            return
        }
        val scrollable = findScrollable(root)
        if (scrollable == null) {
            finishSeekLatest(pause = true)
            return
        }

        val attemptedDirection = scrollDirection
        val action = if (attemptedDirection ==
            com.noyclic.openflow.core.ScrollProgressTracker.Direction.BACKWARD_PROBE
        ) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD

        if (!scrollable.performAction(action)) {
            when (scrollTracker.recordScrollRejected(attemptedDirection)) {
                com.noyclic.openflow.core.ScrollProgressTracker.Result.CONTINUE -> {
                    scrollDirection = com.noyclic.openflow.core.ScrollProgressTracker.Direction.FORWARD
                    seekLatestStep()
                }
                com.noyclic.openflow.core.ScrollProgressTracker.Result.REACHED_END ->
                    finishSeekLatest(pause = false)
                com.noyclic.openflow.core.ScrollProgressTracker.Result.UNCERTAIN ->
                    finishSeekLatest(pause = true)
            }
            return
        }

        val beforeFingerprint = inspect(root).fingerprint
        val check = Runnable {
            if (!state.getBoolean("automation_enabled", false)) {
                cancelSeekLatest()
                return@Runnable
            }
            val currentRoot = findChatGptRoot()
            if (currentRoot == null) {
                finishSeekLatest(pause = true)
                return@Runnable
            }
            val current = inspect(currentRoot)
            messageScan.record(current.messageKeys)
            when (current.signal()) {
                ScreenSignal.LOADING -> {
                    cancelSeekLatest()
                    state.edit().putString("monitor_status", "Ответ ещё генерируется…").apply()
                }
                ScreenSignal.RESPONSE_COMPLETE -> {
                    if (current.fingerprint == beforeFingerprint) {
                        finishSeekLatest(pause = true)
                    } else {
                        scrollTracker.recordFingerprintChanged()
                        lastScrollFingerprint = current.fingerprint
                        if (attemptedDirection ==
                            com.noyclic.openflow.core.ScrollProgressTracker.Direction.BACKWARD_PROBE
                        ) {
                            scrollDirection = com.noyclic.openflow.core.ScrollProgressTracker.Direction.FORWARD
                        }
                        seekLatestStep()
                    }
                }
                else -> {
                    if (AutomationSafety.decide(current.signal()) == GuardDecision.PAUSE_AUTOMATION) {
                        pauseFor(current.signal())
                    } else {
                        finishSeekLatest(pause = true)
                    }
                }
            }
        }
        scrollCheck = check
        handler.postDelayed(check, SCROLL_SETTLE_MS)
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE

        fun subtreeTextStats(node: AccessibilityNodeInfo, depth: Int): Pair<Int, Int> {
            if (depth > 24) return 0 to 0
            val own = node.text?.toString()?.trim().orEmpty()
            val hasLongText = if (own.length >= 40) 1 else 0
            var longTextNodes = hasLongText
            var textNodes = if (own.isNotEmpty()) 1 else 0
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                val childStats = subtreeTextStats(child, depth + 1)
                longTextNodes += childStats.first
                textNodes += childStats.second
            }
            return longTextNodes to textNodes
        }

        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 24) return
            if (node.isScrollable && node.isVisibleToUser) {
                val stats = subtreeTextStats(node, 0)
                if (stats.first > 0) {
                    val score = depth * 100 + stats.first * 10 + stats.second + node.childCount
                    if (score > bestScore) {
                        best = node
                        bestScore = score
                    }
                }
            }
            for (index in 0 until node.childCount) visit(node.getChild(index), depth + 1)
        }

        visit(root, 0)
        return best
    }

    private fun cancelSeekLatest() {
        scrollCheck?.let(handler::removeCallbacks)
        scrollCheck = null
        seekingLatest = false
        lastScrollFingerprint = null
    }

    private fun finishSeekLatest(pause: Boolean) {
        val attemptedFingerprint = lastScrollFingerprint
        cancelSeekLatest()
        if (pause) {
            lastUncertainFingerprint = attemptedFingerprint
            messageScan.reset()
            state.edit()
                .putBoolean("auto_continue_enabled", false)
                .putString("monitor_status", "Не удалось подтвердить конец переписки. Наблюдение продолжается без автодействий.")
                .putString("stop_reason", "UNCERTAIN_SCROLL")
                .apply()
            showStatusNotification("Не удалось проверить край переписки. OpenFlow продолжает только наблюдение.")
            return
        }
        val root = findChatGptRoot()
        if (root == null) {
            lastUncertainFingerprint = attemptedFingerprint
            state.edit()
                .putBoolean("auto_continue_enabled", false)
                .putString("monitor_status", "Окно ChatGPT временно недоступно. Наблюдение продолжается без автодействий.")
                .putString("stop_reason", "CHATGPT_WINDOW_UNAVAILABLE")
                .apply()
            showStatusNotification("Окно ChatGPT недоступно. Автодействия остановлены, наблюдение продолжается.")
            return
        }
        val latest = inspect(root)
        messageScan.record(latest.messageKeys)
        if (latest.signal() != ScreenSignal.RESPONSE_COMPLETE) {
            when (AutomationSafety.decide(latest.signal())) {
                GuardDecision.PAUSE_AUTOMATION -> pauseFor(latest.signal())
                GuardDecision.KEEP_MONITORING -> state.edit()
                    .putString("monitor_status", "Окно ChatGPT готово, но проверка ответа пока не завершена. Наблюдаю без автодействий.")
                    .putBoolean("auto_continue_enabled", false)
                    .apply()
                GuardDecision.WAIT_FOR_USER -> {
                    state.edit()
                        .putString("monitor_status", "Не удалось подтвердить завершение ответа. Автодействия пропущены.")
                        .putBoolean("auto_continue_enabled", false)
                        .apply()
                }
            }
            return
        }
        lastScannedFingerprint = latest.fingerprint
        state.edit().putString("monitor_status", "Последние сообщения проверены").apply()
        if (handoffStage() == HandoffStage.REQUESTING_SUMMARY) {
            cancelHandoffTimeout()
            if (state.getBoolean("handoff_summary_generation_seen", false)) {
                transitionHandoff(HandoffEvent.SUMMARY_RESPONSE_COMPLETED)
                if (state.getBoolean("auto_handoff_transfer_enabled", false)) {
                    startAutomaticHandoff(root)
                } else {
                    state.edit()
                        .putBoolean("automation_enabled", false)
                        .putBoolean("auto_continue_enabled", false)
                        .putString("monitor_status", "Сводка готова. Проверь её и перенеси в новый чат вручную.")
                        .apply()
                    showStatusNotification("Сводка готова. Проверь её в ChatGPT и перенеси в новый чат.")
                }
            } else {
                transitionHandoff(HandoffEvent.SUMMARY_REQUEST_FAILED)
                state.edit()
                    .putBoolean("automation_enabled", false)
                    .putString("monitor_status", "Не удалось подтвердить начало генерации сводки. Проверь ChatGPT вручную.")
                    .apply()
                showStatusNotification("Не удалось подтвердить генерацию сводки. Автоматизация остановлена.")
            }
            return
        }
        val scannedMessageCount = messageScan.uniqueCount
        if (scannedMessageCount >= state.getInt("long_chat_threshold", DEFAULT_LONG_CHAT_THRESHOLD)) {
            requestHandoff(root, scannedMessageCount)
        } else if (state.getBoolean("auto_continue_enabled", false) &&
            latest.fingerprint != lastAutoSentFingerprint) {
            scheduleContinue(latest.fingerprint)
        }
    }

    /**
     * Opt-in handoff: extract the newest visible summary, open a clearly labelled
     * new-chat action, verify that the old summary is no longer on screen, then
     * place the handoff into the new composer. Any uncertain step pauses automation.
     */
    private fun startAutomaticHandoff(root: AccessibilityNodeInfo) {
        val summary = extractLatestSummary(root)
        state.edit().putString("handoff_pending_summary", summary).apply()
        if (summary.length < MIN_HANDOFF_SUMMARY_CHARS) {
            failAutomaticHandoff("Не удалось надёжно прочитать сводку с экрана. Перенос остановлен; проверь ответ вручную.")
            return
        }
        val newChat = findNewChatAction(root)
        if (newChat == null) {
            failAutomaticHandoff("Не нашёл кнопку «Новый чат». Сводка сохранена локально; открой новый чат вручную.")
            state.edit().putString("handoff_pending_summary", summary).apply()
            return
        }
        state.edit()
            .putString("handoff_pending_summary", summary)
            .putString("monitor_status", "Сводка прочитана. Открываю новый чат…")
            .apply()
        transitionHandoff(HandoffEvent.AUTO_TRANSFER_STARTED)
        if (!newChat.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            failAutomaticHandoff("Не удалось нажать «Новый чат». Сводка сохранена для ручного переноса.")
            return
        }
        handler.postDelayed({
            if (!state.getBoolean("automation_enabled", false)) {
                failAutomaticHandoff("Автоматизация была остановлена до открытия нового чата.")
                return@postDelayed
            }
            val newRoot = findChatGptRoot()
            if (newRoot == null) {
                failAutomaticHandoff("Не удалось проверить экран нового чата.")
                return@postDelayed
            }
            val visibleText = inspect(newRoot).text
            if (visibleText.contains(summary.take(100), ignoreCase = true)) {
                failAutomaticHandoff("Экран не изменился после нажатия. Не отправляю контекст, чтобы не продублировать его в старом чате.")
                return@postDelayed
            }
            val input = findEditable(newRoot)
            if (input == null || !input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                failAutomaticHandoff("Новый чат открылся, но поле ввода не найдено. Сводка сохранена локально.")
                return@postDelayed
            }
            val handoffMessage = "Продолжаем предыдущую задачу. Ниже проверенная сводка предыдущего чата. Сначала прочитай её целиком, затем продолжай с указанного следующего шага. Не утверждай, что незавершённая работа уже выполнена.\n\n$summary"
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, handoffMessage)
            }
            if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                failAutomaticHandoff("Не удалось вставить сводку в новый чат. Текст сохранён в OpenFlow.")
                return@postDelayed
            }
            handler.postDelayed({
                val sendRoot = findChatGptRoot()
                val sendButton = sendRoot?.let(::findSendButton)
                if (sendButton == null || !sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    clearComposer(input)
                    failAutomaticHandoff("Текст вставлен, но кнопку отправки не удалось безопасно определить. Отправь его вручную.")
                    return@postDelayed
                }
                transitionHandoff(HandoffEvent.AUTO_TRANSFER_COMPLETED)
                state.edit()
                    .putBoolean("handoff_required", false)
                    .putBoolean("handoff_summary_generation_seen", false)
                    .putBoolean("auto_continue_enabled", false)
                    .remove("handoff_pending_summary")
                    .putString("monitor_status", "Контекст отправлен в новый чат. Ожидаю ответ ChatGPT…")
                    .apply()
                lastScannedFingerprint = null
                lastAutoSentFingerprint = null
                messageScan.reset()
                showStatusNotification("Контекст перенесён в новый чат. OpenFlow продолжает наблюдение.")
            }, NEW_CHAT_COMPOSER_SETTLE_MS)
        }, NEW_CHAT_SETTLE_MS)
    }

    private fun extractLatestSummary(root: AccessibilityNodeInfo): String {
        val blocks = ArrayList<String>()
        val seen = HashSet<String>()
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 24 || blocks.size >= 100) return
            val value = node.text?.toString()?.trim().orEmpty()
            if (!node.isEditable && node.isVisibleToUser && value.length >= 80) {
                val normalized = value.lowercase().replace(Regex("\\s+"), " ").trim()
                val isOurPrompt = normalized.contains("prepare a complete continuation handoff") ||
                    normalized.contains("подготовь полный контекст для продолжения") ||
                    normalized.contains("continue from this project handoff")
                if (!isOurPrompt && seen.add(normalized)) blocks += value
            }
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        visit(root, 0)
        return blocks.takeLast(MAX_HANDOFF_TEXT_BLOCKS).joinToString("\n\n")
            .takeLast(MAX_HANDOFF_SUMMARY_CHARS)
            .trim()
    }

    private fun findNewChatAction(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val labels = setOf(
            "new chat", "new conversation", "start a new chat",
            "новый чат", "новый диалог", "создать чат", "начать новый чат"
        )
        var match: AccessibilityNodeInfo? = null
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 24 || match != null) return
            val label = ((node.text?.toString().orEmpty()) + " " +
                (node.contentDescription?.toString().orEmpty()))
                .lowercase().replace(Regex("\\s+"), " ").trim()
            if (node.isVisibleToUser && node.isEnabled && label in labels) {
                match = if (node.isClickable) node else node.parent?.takeIf { it.isClickable && it.isEnabled }
            }
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        visit(root, 0)
        return match
    }

    private fun failAutomaticHandoff(reason: String) {
        if (handoffStage() == HandoffStage.TRANSFERRING) {
            transitionHandoff(HandoffEvent.AUTO_TRANSFER_FAILED)
        }
        state.edit()
            .putBoolean("automation_enabled", false)
            .putBoolean("auto_continue_enabled", false)
            .putString("monitor_status", reason)
            .apply()
        showStatusNotification(reason)
    }

    private fun scheduleContinue(fingerprint: String) {
        if (pendingFingerprint == fingerprint) return
        pendingFingerprint = fingerprint
        stableCheck?.let(handler::removeCallbacks)
        val check = Runnable {
            stableCheck = null
            val root = findChatGptRoot()
            if (root == null) {
                pendingFingerprint = null
                state.edit()
                    .putBoolean("auto_continue_enabled", false)
                    .putString("monitor_status", "Окно ChatGPT недоступно. Автопродолжение выключено; наблюдение продолжается.")
                    .apply()
                showStatusNotification("ChatGPT временно недоступен. Автопродолжение выключено.")
                return@Runnable
            }
            val current = inspect(root)
            if (!state.getBoolean("automation_enabled", false) ||
                !state.getBoolean("auto_continue_enabled", false) ||
                current.signal() != ScreenSignal.RESPONSE_COMPLETE ||
                current.fingerprint != fingerprint
            ) {
                pendingFingerprint = null
                return@Runnable
            }
            val prompt = state.getString(
                "continue_prompt",
                "Продолжай работу с того места, где остановился. Не повторяй уже выполненное."
            ) ?: "Продолжай работу с того места, где остановился. Не повторяй уже выполненное."
            val sent = sendTextMessage(root, prompt)
            if (sent) {
                lastAutoSentFingerprint = fingerprint
                val count = state.getInt("auto_continue_count", 0) + 1
                val keepGoing = count < MAX_AUTO_CONTINUES
                state.edit()
                    .putInt("auto_continue_count", count)
                    .putBoolean("auto_continue_enabled", keepGoing)
                    .putString(
                        "monitor_status",
                        if (keepGoing) "Продолжение отправлено ($count/$MAX_AUTO_CONTINUES)"
                        else "Лимит автопродолжений сессии достигнут. Пауза."
                    )
                    .apply()
            } else {
                state.edit()
                    .putBoolean("auto_continue_enabled", false)
                    .putString("monitor_status", "Не нашёл надёжное активное поле ввода или кнопку отправки. Автопродолжение выключено.")
                    .apply()
            }
            pendingFingerprint = null
        }
        stableCheck = check
        handler.postDelayed(check, RESPONSE_STABILITY_MS)
    }

    private fun sendTextMessage(root: AccessibilityNodeInfo, message: String): Boolean {
        val input = findEditable(root) ?: return false
        if (!input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
        }
        if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        val refreshedRoot = findChatGptRoot()
        val sendButton = refreshedRoot?.let(::findSendButton)
        if (sendButton == null) {
            clearComposer(input)
            return false
        }
        val clicked = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!clicked) clearComposer(input)
        return clicked
    }

    private fun clearComposer(input: AccessibilityNodeInfo) {
        val clear = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        runCatching { input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clear) }
    }

    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 24) return
            if (node.isEditable && node.isEnabled && node.isVisibleToUser) {
                val hint = node.hintText?.toString().orEmpty().lowercase()
                val viewId = node.viewIdResourceName.orEmpty().lowercase()
                val className = node.className?.toString().orEmpty().lowercase()
                var score = 0
                if (hint.contains("message") || hint.contains("ask anything") || hint.contains("сообщ")) score += 30
                if (viewId.contains("composer") || viewId.contains("message") || viewId.contains("input")) score += 20
                if (className.contains("edittext")) score += 5
                if (score > bestScore) {
                    best = node
                    bestScore = score
                }
            }
            for (index in 0 until node.childCount) visit(node.getChild(index), depth + 1)
        }
        visit(root, 0)
        return best
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val label = ((node.text?.toString().orEmpty()) + " " +
            (node.contentDescription?.toString().orEmpty())).trim().lowercase()
        val viewId = node.viewIdResourceName.orEmpty().lowercase()
        val match = label in setOf(
            "send", "send message", "send prompt", "отправить", "отправить сообщение"
        ) || viewId.contains("send")
        if (match && node.isVisibleToUser && node.isEnabled) {
            if (node.isClickable) return node
            node.parent?.takeIf { it.isVisibleToUser && it.isEnabled && it.isClickable }?.let { return it }
        }
        for (i in 0 until node.childCount) findSendButton(node.getChild(i))?.let { return it }
        return null
    }

    private fun showStatusNotification(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val intent = android.content.Intent(this, MainActivity::class.java)
        val pending = android.app.PendingIntent.getActivity(
            this, 7410, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val channel = "openflow_monitor"
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(android.app.NotificationChannel(
                channel, "OpenFlow monitor", android.app.NotificationManager.IMPORTANCE_HIGH
            ))
        }
        val builder = if (android.os.Build.VERSION.SDK_INT >= 26)
            android.app.Notification.Builder(this, channel) else android.app.Notification.Builder(this)
        manager.notify(7411, builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("OpenFlow")
            .setContentText(message)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build())
    }

    override fun onInterrupt() {
        stableCheck?.let(handler::removeCallbacks)
        stableCheck = null
        cancelSeekLatest()
        cancelHandoffTimeout()
        if (handoffStage() == HandoffStage.REQUESTING_SUMMARY) transitionHandoff(HandoffEvent.SUMMARY_REQUEST_FAILED)
        state.edit()
            .putBoolean("accessibility_connected", false)
            .putBoolean("automation_enabled", false)
            .putBoolean("auto_continue_enabled", false)
            .putString("diag_service_state", "interrupted")
            .putString("monitor_status", "Служба наблюдения прервана Android. Автоматизация остановлена.")
            .apply()
    }

    override fun onDestroy() {
        runCatching {
            ocrExecutor.execute {
                localOcrEngine?.close()
                localOcrEngine = null
            }
            ocrExecutor.shutdown()
        }
        stableCheck?.let(handler::removeCallbacks)
        stableCheck = null
        cancelSeekLatest()
        cancelHandoffTimeout()
        if (handoffStage() == HandoffStage.REQUESTING_SUMMARY) transitionHandoff(HandoffEvent.SUMMARY_REQUEST_FAILED)
        state.edit()
            .putBoolean("accessibility_connected", false)
            .putString("diag_service_state", "disconnected")
            .putBoolean("automation_enabled", false)
            .putBoolean("auto_continue_enabled", false)
            .apply()
        super.onDestroy()
    }

    companion object {
        const val CHATGPT_PACKAGE = "com.openai.chatgpt"
        private const val RESPONSE_STABILITY_MS = 2200L
        private const val SCREENSHOT_OCR_INTERVAL_MS = 8_000L
        private const val SCROLL_SETTLE_MS = 650L
        private const val MAX_SCROLL_STEPS = 24
        private const val DEFAULT_LONG_CHAT_THRESHOLD = 36
        private const val MAX_AUTO_CONTINUES = 25
        private const val HANDOFF_TIMEOUT_MS = 180_000L
        private const val NEW_CHAT_SETTLE_MS = 1_100L
        private const val NEW_CHAT_COMPOSER_SETTLE_MS = 450L
        private const val MIN_HANDOFF_SUMMARY_CHARS = 240
        private const val MAX_HANDOFF_SUMMARY_CHARS = 12_000
        private const val MAX_HANDOFF_TEXT_BLOCKS = 12
    }
}
