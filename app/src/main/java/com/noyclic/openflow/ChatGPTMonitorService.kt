package com.noyclic.openflow

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.noyclic.openflow.core.AutomationSafety
import com.noyclic.openflow.core.GuardDecision
import com.noyclic.openflow.core.ScreenSignal
import com.noyclic.openflow.core.ScreenSnapshot

/**
 * Opt-in, on-device observer for the official ChatGPT Android app.
 * It only inspects the active accessibility tree after the user enables this service in Android Settings.
 */
class ChatGPTMonitorService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val state by lazy { getSharedPreferences("openflow_state", MODE_PRIVATE) }
    private var pendingFingerprint: String? = null
    private var lastAutoSentFingerprint: String? = null
    private var stableCheck: Runnable? = null
    private var seekingLatest = false
    private var scrollAttempts = 0
    private var unchangedScrollChecks = 0
    private var lastScrollFingerprint: String? = null
    private var scrollCheck: Runnable? = null
    private var scrollTracker = com.noyclic.openflow.core.ScrollProgressTracker()

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 350
            packageNames = arrayOf(CHATGPT_PACKAGE)
        }
        state.edit().putBoolean("accessibility_connected", true).apply()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != CHATGPT_PACKAGE) return
        if (!state.getBoolean("automation_enabled", false)) return
        val root = rootInActiveWindow ?: return
        val snapshot = inspect(root)
        val signal = snapshot.signal()
        when (AutomationSafety.decide(signal)) {
            GuardDecision.PAUSE_AUTOMATION -> pauseFor(signal)
            GuardDecision.KEEP_MONITORING -> {
                pendingFingerprint = null
                stableCheck?.let(handler::removeCallbacks)
                state.edit().putString("monitor_status", "Ответ генерируется…").apply()
            }
            GuardDecision.WAIT_FOR_USER -> {
                if (!seekingLatest) beginSeekLatest(snapshot)
            }
        }
    }

    private fun inspect(root: AccessibilityNodeInfo): ScreenSnapshot {
        val texts = ArrayList<String>(96)
        var stop = false
        var send = false
        var messageLikeNodes = 0
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 24 || texts.size >= 180) return
            val value = node.text?.toString()?.trim().orEmpty()
            val description = node.contentDescription?.toString()?.trim().orEmpty()
            if (value.isNotEmpty()) {
                texts += value
                if (value.length >= 40 && node.className?.toString()?.contains("TextView") == true) {
                    messageLikeNodes++
                }
            }
            if (description.isNotEmpty()) texts += description
            val label = (value + " " + description).lowercase()
            if (label.contains("stop generating") || label.contains("остановить генерацию") ||
                label.contains("stop response") || label.contains("остановить ответ")) stop = true
            if (label == "send" || label.contains("send message") || label == "отправить" ||
                label.contains("отправить сообщение")) send = true
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        visit(root, 0)
        return ScreenSnapshot(texts.joinToString("\n"), stop, send, messageLikeNodes)
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
        showStatusNotification(state.getString("monitor_status", "OpenFlow paused") ?: "OpenFlow paused")
    }

    private fun requestHandoff(messageCount: Int) {
        if (state.getBoolean("handoff_required", false)) return
        state.edit()
            .putBoolean("automation_enabled", false)
            .putBoolean("auto_continue_enabled", false)
            .putBoolean("handoff_required", true)
            .putInt("last_visible_message_count", messageCount)
            .putString("monitor_status", "Похоже, чат стал длинным. Подготовь перенос контекста.")
            .apply()
        showStatusNotification("Чат длинный — пора подготовить перенос контекста.")
    }


    /**
     * Move the ChatGPT conversation to its newest visible content before making
     * a completion/handoff decision. Scrolling is bounded and stops when the
     * accessibility tree no longer changes, no scrollable container remains,
     * or the safety limit is reached.
     */
    private fun beginSeekLatest(snapshot: ScreenSnapshot) {
        seekingLatest = true
        scrollAttempts = 0
        unchangedScrollChecks = 0
        scrollTracker = com.noyclic.openflow.core.ScrollProgressTracker(MAX_SCROLL_STEPS)
        lastScrollFingerprint = snapshot.fingerprint
        state.edit().putString("monitor_status", "Проверяю последние сообщения…").apply()
        seekLatestStep()
    }

    private fun seekLatestStep() {
        if (!state.getBoolean("automation_enabled", false)) {
            finishSeekLatest(pause = false)
            return
        }
        val root = rootInActiveWindow
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
        if (!scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            when (scrollTracker.recordScrollRejected()) {
                com.noyclic.openflow.core.ScrollProgressTracker.Result.REACHED_END -> finishSeekLatest(pause = false)
                else -> finishSeekLatest(pause = true)
            }
            return
        }
        scrollAttempts++
        val check = Runnable {
            val currentRoot = rootInActiveWindow
            if (currentRoot == null) {
                finishSeekLatest(pause = true)
                return@Runnable
            }
            val current = inspect(currentRoot)
            val progress = if (current.fingerprint == lastScrollFingerprint) {
                unchangedScrollChecks++
                scrollTracker.recordFingerprintUnchanged()
            } else {
                unchangedScrollChecks = 0
                lastScrollFingerprint = current.fingerprint
                scrollTracker.recordFingerprintChanged()
            }
            when (progress) {
                com.noyclic.openflow.core.ScrollProgressTracker.Result.CONTINUE -> seekLatestStep()
                com.noyclic.openflow.core.ScrollProgressTracker.Result.REACHED_END -> finishSeekLatest(pause = false)
                com.noyclic.openflow.core.ScrollProgressTracker.Result.UNCERTAIN -> finishSeekLatest(pause = true)
            }
        }
        scrollCheck = check
        handler.postDelayed(check, SCROLL_SETTLE_MS)
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            findScrollable(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun finishSeekLatest(pause: Boolean) {
        scrollCheck?.let(handler::removeCallbacks)
        scrollCheck = null
        seekingLatest = false
        if (pause) {
            pauseFor(ScreenSignal.UNKNOWN)
            return
        }
        val root = rootInActiveWindow ?: return
        val latest = inspect(root)
        if (latest.signal() != ScreenSignal.RESPONSE_COMPLETE) {
            when (AutomationSafety.decide(latest.signal())) {
                GuardDecision.PAUSE_AUTOMATION -> pauseFor(latest.signal())
                GuardDecision.KEEP_MONITORING -> state.edit().putString("monitor_status", "Ответ ещё генерируется…").apply()
                GuardDecision.WAIT_FOR_USER -> Unit
            }
            return
        }
        state.edit().putString("monitor_status", "Последние сообщения проверены").apply()
        if (latest.visibleMessageCount >= state.getInt("long_chat_threshold", DEFAULT_LONG_CHAT_THRESHOLD)) {
            requestHandoff(latest.visibleMessageCount)
        } else if (state.getBoolean("auto_continue_enabled", false) &&
            latest.fingerprint != lastAutoSentFingerprint) {
            scheduleContinue(latest.fingerprint)
        }
    }

    private fun scheduleContinue(fingerprint: String) {
        if (pendingFingerprint == fingerprint) return
        pendingFingerprint = fingerprint
        stableCheck?.let(handler::removeCallbacks)
        val check = Runnable {
            val root = rootInActiveWindow ?: return@Runnable
            val current = inspect(root)
            if (!state.getBoolean("automation_enabled", false) ||
                !state.getBoolean("auto_continue_enabled", false) ||
                current.signal() != ScreenSignal.RESPONSE_COMPLETE ||
                current.fingerprint != fingerprint) return@Runnable
            val sent = sendContinueMessage(root)
            if (sent) {
                lastAutoSentFingerprint = fingerprint
                val count = state.getInt("auto_continue_count", 0) + 1
                val keepGoing = count < MAX_AUTO_CONTINUES
                state.edit()
                    .putInt("auto_continue_count", count)
                    .putBoolean("auto_continue_enabled", keepGoing)
                    .putString("monitor_status", if (keepGoing) "Продолжение отправлено ($count/$MAX_AUTO_CONTINUES)" else "Лимит автопродолжений сессии достигнут. Пауза.")
                    .apply()
            } else {
                state.edit().putBoolean("auto_continue_enabled", false)
                    .putString("monitor_status", "Не нашёл поле ввода или кнопку отправки. Пауза для ручного действия.")
                    .apply()
            }
            pendingFingerprint = null
        }
        stableCheck = check
        handler.postDelayed(check, RESPONSE_STABILITY_MS)
    }

    private fun sendContinueMessage(root: AccessibilityNodeInfo): Boolean {
        val prompt = state.getString(
            "continue_prompt",
            "Продолжай работу с того места, где остановился. Не повторяй уже выполненное."
        ) ?: return false
        val input = findEditable(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, prompt)
        }
        if (!input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return false
        if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        val sendButton = findSendButton(root) ?: return false
        return sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) findEditable(node.getChild(i))?.let { return it }
        return null
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val label = ((node.text?.toString().orEmpty()) + " " +
            (node.contentDescription?.toString().orEmpty())).lowercase()
        val match = label == "send" || label.contains("send message") ||
            label == "отправить" || label.contains("отправить сообщение")
        if (match && (node.isClickable || node.isFocusable)) return node
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
        state.edit().putBoolean("accessibility_connected", false)
            .putString("monitor_status", "Служба наблюдения прервана Android").apply()
    }

    override fun onDestroy() {
        stableCheck?.let(handler::removeCallbacks)
        state.edit().putBoolean("accessibility_connected", false).apply()
        super.onDestroy()
    }

    companion object {
        const val CHATGPT_PACKAGE = "com.openai.chatgpt"
        private const val RESPONSE_STABILITY_MS = 2200L
        private const val SCROLL_SETTLE_MS = 420L
        private const val MAX_SCROLL_STEPS = 24
        private const val DEFAULT_LONG_CHAT_THRESHOLD = 36
        private const val MAX_AUTO_CONTINUES = 25
    }
}
