package com.noyclic.openflow

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private enum class Page { HOME, SETTINGS, HANDOFF, ACTIVITY }
    private var currentPage = Page.HOME
    private val prefs by lazy { getSharedPreferences("openflow_state", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = color("#0B1220")
        window.navigationBarColor = color("#0B1220")
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) render()
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(color("#0B1220"))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }
        scroll.addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(dp(20), bars.top + dp(12), dp(20), bars.bottom + dp(18))
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(dp(20), insets.systemWindowInsetTop + dp(12), dp(20), insets.systemWindowInsetBottom + dp(18))
                insets
            }
        }
        when (currentPage) {
            Page.HOME -> home(content)
            Page.SETTINGS -> settings(content)
            Page.HANDOFF -> handoff(content)
            Page.ACTIVITY -> activityPage(content)
        }
        setContentView(scroll)
    }

    private fun home(parent: LinearLayout) {
        val brand = row(parent)
        val mark = text(brand, "↗", 22f, "#09251F", true)
        mark.gravity = Gravity.CENTER
        mark.background = rounded("#69E4C5", "#69E4C5", 13)
        mark.layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        gapWidth(brand, 10)
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text(brandText, "OpenFlow", 19f, "#F2F7FF", true)
        text(brandText, "AI WORKFLOW COMPANION", 9f, "#8397AF", true).letterSpacing = 0.11f
        brand.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        val settingsIcon = text(brand, "⚙", 20f, "#C3D2E3", false)
        settingsIcon.gravity = Gravity.CENTER
        settingsIcon.background = rounded("#172437", "#29394E", 14)
        settingsIcon.layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        settingsIcon.setOnClickListener { go(Page.SETTINGS) }

        gap(parent, 30)
        eyebrow(parent, "PERSONAL AI WORKSPACE")
        gap(parent, 10)
        text(parent, "Keep your AI\nworkflow flowing.", 34f, "#F2F7FF", true)
        gap(parent, 10)
        text(parent, "A calm control center for long-running conversations, careful automation and clean handoffs.", 15f, "#9CADC2", false)

        gap(parent, 24)
        val hero = card(parent, 22, "#1A3850", "#18263B")
        eyebrow(hero, "OPENFLOW COMPANION")
        gap(hero, 12)
        text(hero, "Stay in the flow.\nStay in control.", 26f, "#F5FAFF", true)
        gap(hero, 8)
        text(hero, "Keep repetitive controls close while every important action stays visible and interruptible.", 14f, "#C0D1E2", false)
        gap(hero, 18)
        val heroFooter = row(hero)
        chip(heroFooter, "LOCAL-FIRST", "#69E4C5", "#143C42")
        gapWidth(heroFooter, 8)
        chip(heroFooter, "USER CONTROL", "#9BC5FF", "#203B58")

        gap(parent, 18)
        val stats = row(parent)
        statCard(stats, "FLOATING PANEL", if (isRunning()) "ACTIVE" else "OFF", if (isRunning()) "#69E4C5" else "#B3C3D5")
        gapWidth(stats, 10)
        statCard(stats, "SAFETY GUARD", "READY", "#69E4C5")
        gapWidth(stats, 10)
        statCard(stats, "DATA", "ON DEVICE", "#9BC5FF")

        gap(parent, 28)
        sectionTitle(parent, "Quick controls", "Your most-used actions, one tap away.")
        gap(parent, 12)
        val controls = card(parent, 18)
        text(controls, "Floating assistant", 18f, "#F1F6FE", true)
        gap(controls, 5)
        text(controls, "A movable panel above other apps. Pause or close it whenever you need.", 13f, "#95A7BD", false)
        gap(controls, 16)
        val running = isRunning()
        actionButton(controls, if (running) "Stop floating assistant" else "Launch floating assistant",
            if (running) "#402B35" else "#65DFC1", if (running) "#FFB7C2" else "#09251F") {
            if (running) stopOverlay() else launchOverlay()
        }

        gap(parent, 12)
        val handoffCard = card(parent, 18)
        val handoffRow = row(handoffCard)
        val handoffText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text(handoffText, "Context handoff", 17f, "#F1F6FE", true)
        gap(handoffText, 5)
        text(handoffText, "Prepare a tidy brief before moving to a fresh chat.", 13f, "#95A7BD", false)
        handoffRow.addView(handoffText, LinearLayout.LayoutParams(0, -2, 1f))
        text(handoffRow, "↗", 24f, "#69E4C5", true).gravity = Gravity.CENTER
        gap(handoffCard, 14)
        outlineButton(handoffCard, "Open handoff workspace") { go(Page.HANDOFF) }

        gap(parent, 28)
        sectionTitle(parent, "Safety by design", "OpenFlow pauses instead of guessing.")
        gap(parent, 12)
        safetyRow(parent, "Usage-limit guard", "A reported limit must pause automation.", "ON")
        safetyRow(parent, "Unclear screen state", "Unknown states are treated as unsafe.", "ON")
        safetyRow(parent, "Automatic message sending", "Not enabled in this prototype.", "OFF")

        gap(parent, 24)
        val note = card(parent, 15, "#111C2A", "#111C2A")
        text(note, "PROTOTYPE STATUS", 11f, "#69E4C5", true)
        gap(note, 6)
        text(note, "The overlay and local handoff tools are available. Screen recognition, automatic chat creation and reasoning-mode control are not implemented yet.", 12f, "#9CADC2", false)
        gap(parent, 24)
        navigation(parent, Page.HOME)
    }

    private fun settings(parent: LinearLayout) {
        pageHeader(parent, "Settings", "Permissions and behavior")
        sectionTitle(parent, "Android permissions", "OpenFlow asks only for what the floating companion needs.")
        gap(parent, 12)
        val overlayGranted = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
        settingRow(parent, "Display over other apps", "Required to show the floating panel.", if (overlayGranted) "GRANTED" else "NEEDS ACCESS", overlayGranted)
        if (!overlayGranted) outlineButton(parent, "Grant overlay permission") {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                .onFailure { toast("Allow display over other apps in Android Settings.") }
        }
        gap(parent, 10)
        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        settingRow(parent, "Foreground notification", "Shows when the companion is active.", if (notificationGranted) "GRANTED" else "OPTIONAL", notificationGranted)
        if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) {
            outlineButton(parent, "Allow notifications") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
        }

        gap(parent, 24)
        sectionTitle(parent, "ChatGPT automation", "Enable this only when you want OpenFlow to monitor your ChatGPT session.")
        gap(parent, 12)
        val monitorCard = card(parent, 18, "#152338", "#2C4861")
        val accessibilityEnabled = isChatGptAccessibilityEnabled()
        settingRow(monitorCard, "ChatGPT screen monitor", "Reads visible accessibility labels locally. Android permission required.", if (accessibilityEnabled) "CONNECTED" else "NOT ENABLED", accessibilityEnabled)
        gap(monitorCard, 10)
        outlineButton(monitorCard, if (accessibilityEnabled) "Accessibility settings" else "Enable screen monitor") {
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .onFailure { toast("Open Android Settings → Accessibility → Downloaded apps.") }
        }
        gap(monitorCard, 14)
        val automationEnabled = prefs.getBoolean("automation_enabled", false)
        actionButton(monitorCard, if (automationEnabled) "Pause automation" else "Start monitoring",
            if (automationEnabled) "#493442" else "#65DFC1",
            if (automationEnabled) "#FFC0C8" else "#09251F") {
            if (!isChatGptAccessibilityEnabled()) {
                toast("First enable OpenFlow ChatGPT monitor in Android Accessibility settings.")
                runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            } else {
                prefs.edit()
                    .putBoolean("automation_enabled", !automationEnabled)
                    .putBoolean("auto_continue_enabled", false)
                    .putString("monitor_status", if (automationEnabled) "Пауза вручную" else "Наблюдение включено")
                    .putInt("auto_continue_count", 0)
                    .apply()
                toast(if (automationEnabled) "Automation paused." else "Monitoring started. Auto-continue is still off.")
                render()
            }
        }
        gap(monitorCard, 8)
        val autoContinue = prefs.getBoolean("auto_continue_enabled", false)
        outlineButton(monitorCard, if (autoContinue) "Disable auto-continue" else "Enable auto-continue") {
            if (!prefs.getBoolean("automation_enabled", false)) {
                toast("Start monitoring first.")
            } else {
                prefs.edit().putBoolean("auto_continue_enabled", !autoContinue).apply()
                toast(if (autoContinue) "Auto-continue disabled." else "Auto-continue enabled.")
                render()
            }
        }
        gap(monitorCard, 8)
        text(monitorCard, "Auto-continue sends your saved continuation prompt only after a stable completed response. A limit, error, CAPTCHA, restriction or uncertain screen stops it. Long-chat detection is an estimate and will pause for a handoff review.", 12f, "#9CADC2", false)
        gap(monitorCard, 8)
        text(monitorCard, "STATUS: ${prefs.getString("monitor_status", "Not started")}", 11f, "#69E4C5", true)

        gap(parent, 26)
        sectionTitle(parent, "Automation behavior", "The current build is intentionally conservative.")
        gap(parent, 12)
        val behavior = card(parent, 18)
        settingRow(behavior, "Manual control", "You decide when the overlay starts and stops.", "ON", true)
        divider(behavior)
        settingRow(behavior, "Limit protection", "A detected limit must pause automation.", "ON", true)
        divider(behavior)
        settingRow(behavior, "Automatic message sending", "Not available in this prototype.", "OFF", false)
        gap(parent, 22)
        val note = card(parent, 16, "#18263B", "#18263B")
        text(note, "WHY THESE PERMISSIONS?", 11f, "#9BC5FF", true)
        gap(note, 7)
        text(note, "Overlay permission is used only for the visible floating panel. The app does not currently read ChatGPT screens, capture screenshots or upload conversation content.", 13f, "#B6C6D8", false)
        gap(parent, 24)
        actionButton(parent, "Back to overview", "#65DFC1", "#09251F") { go(Page.HOME) }
        gap(parent, 20)
        navigation(parent, Page.SETTINGS)
    }

    private fun handoff(parent: LinearLayout) {
        pageHeader(parent, "Context handoff", "Carry the important parts forward")
        val intro = card(parent, 18, "#1A3850", "#18263B")
        text(intro, "A fresh chat.\nThe same progress.", 24f, "#F3F8FF", true)
        gap(intro, 8)
        text(intro, "OpenFlow cannot read another app's conversation yet. Ask ChatGPT for a handoff summary, paste it here, then copy it into your new chat.", 13f, "#B9CADD", false)
        gap(parent, 20)
        sectionTitle(parent, "Step 1 · Ask for a summary", "Copy this request into your current ChatGPT conversation.")
        gap(parent, 10)
        val request = "Prepare a continuation handoff for this project. Include the goal, implemented work, important files and architecture, test results, known problems, decisions and constraints, and the single best next step. Be precise and do not claim unfinished work is complete."
        val requestCard = card(parent, 16)
        text(requestCard, request, 13f, "#C0CEDF", false)
        gap(requestCard, 12)
        outlineButton(requestCard, "Copy summary request") { copyText("OpenFlow handoff request", request) }

        gap(parent, 22)
        sectionTitle(parent, "Step 2 · Paste the summary", "Review it before carrying it into another chat.")
        gap(parent, 10)
        val editor = EditText(this).apply {
            setTextColor(color("#ECF4FF"))
            setHintTextColor(color("#71839A"))
            textSize = 14f
            hint = "Paste the handoff summary here…"
            minLines = 8
            maxLines = 16
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded("#111C2A", "#2B4057", 14)
            isVerticalScrollBarEnabled = true
        }
        parent.addView(editor, LinearLayout.LayoutParams(-1, -2))
        gap(parent, 12)
        actionButton(parent, "Copy handoff for new chat", "#65DFC1", "#09251F") {
            val value = editor.text?.toString()?.trim().orEmpty()
            if (value.isBlank()) toast("Paste a handoff summary first.")
            else copyText("OpenFlow project handoff",
                "Continue from this project handoff. Verify repository state before editing and preserve the listed constraints.\n\n$value")
        }
        gap(parent, 9)
        text(parent, "This screen does not upload or save your text. Clipboard copying happens only when you tap a button.", 12f, "#778BA3", false)
        gap(parent, 24)
        navigation(parent, Page.HANDOFF)
    }

    private fun activityPage(parent: LinearLayout) {
        pageHeader(parent, "Activity", "A clear record of what OpenFlow does")
        val empty = card(parent, 22)
        text(empty, "◷", 34f, "#69E4C5", true)
        gap(empty, 10)
        text(empty, "No background automation yet.", 18f, "#F2F7FF", true)
        gap(empty, 7)
        text(empty, "This prototype does not send messages or monitor other apps. When the floating companion is active, Android shows an ongoing notification.", 13f, "#9CADC2", false)
        gap(parent, 14)
        settingRow(parent, "Floating companion", "Current service state", if (isRunning()) "ACTIVE" else "STOPPED", isRunning())
        gap(parent, 24)
        navigation(parent, Page.ACTIVITY)
    }

    private fun pageHeader(parent: LinearLayout, title: String, subtitle: String) {
        val top = row(parent)
        val back = text(top, "‹", 30f, "#69E4C5", false)
        back.gravity = Gravity.CENTER
        back.layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        back.setOnClickListener { go(Page.HOME) }
        gapWidth(top, 8)
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text(titles, title, 21f, "#F2F7FF", true)
        text(titles, subtitle, 12f, "#91A2B8", false)
        top.addView(titles)
        gap(parent, 22)
    }

    private fun navigation(parent: LinearLayout, selected: Page) {
        val nav = row(parent).apply {
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded("#111C2A", "#26384E", 18)
        }
        navItem(nav, "⌂", "Home", Page.HOME, selected)
        navItem(nav, "↗", "Handoff", Page.HANDOFF, selected)
        navItem(nav, "◷", "Activity", Page.ACTIVITY, selected)
        navItem(nav, "⚙", "Settings", Page.SETTINGS, selected)
    }

    private fun navItem(parent: LinearLayout, icon: String, label: String, page: Page, selected: Page) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(7), dp(2), dp(7))
            if (page == selected) background = rounded("#203A48", "#203A48", 13)
            setOnClickListener { go(page) }
        }
        text(item, icon, 17f, if (page == selected) "#69E4C5" else "#8497AE", true).gravity = Gravity.CENTER
        text(item, label, 9f, if (page == selected) "#DDFBF3" else "#8497AE", true).gravity = Gravity.CENTER
        parent.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun statCard(parent: LinearLayout, title: String, value: String, tint: String) {
        val item = card(parent, 12, "#131E2D", "#25364B")
        text(item, title, 8.5f, "#8397AF", true)
        gap(item, 7)
        text(item, value, 11f, tint, true)
        item.layoutParams = LinearLayout.LayoutParams(0, dp(76), 1f)
    }

    private fun safetyRow(parent: LinearLayout, title: String, description: String, status: String) {
        val item = row(parent).apply { setPadding(dp(2), dp(13), dp(2), dp(13)) }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text(labels, title, 14f, "#E8F0FA", true)
        gap(labels, 4)
        text(labels, description, 11.5f, "#8498AF", false)
        item.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        chip(item, status, if (status == "ON") "#69E4C5" else "#9EADC1",
            if (status == "ON") "#163B39" else "#202D3D")
        parent.addView(item)
        divider(parent)
    }

    private fun settingRow(parent: LinearLayout, title: String, description: String, status: String, good: Boolean) {
        val item = row(parent).apply { setPadding(dp(2), dp(12), dp(2), dp(12)) }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text(labels, title, 14f, "#EAF2FC", true)
        gap(labels, 4)
        text(labels, description, 11.5f, "#8FA2B9", false)
        item.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        chip(item, status, if (good) "#69E4C5" else "#F0C27A", if (good) "#153B38" else "#3D3020")
        parent.addView(item)
    }

    private fun sectionTitle(parent: LinearLayout, title: String, subtitle: String) {
        text(parent, title, 19f, "#F0F5FD", true)
        gap(parent, 4)
        text(parent, subtitle, 12f, "#879BB3", false)
    }

    private fun eyebrow(parent: LinearLayout, value: String) {
        text(parent, value, 10f, "#69E4C5", true).letterSpacing = 0.16f
    }

    private fun chip(parent: LinearLayout, value: String, foreground: String, backgroundColor: String) {
        val label = text(parent, value, 9f, foreground, true)
        label.gravity = Gravity.CENTER
        label.letterSpacing = 0.06f
        label.setPadding(dp(10), dp(7), dp(10), dp(7))
        label.background = rounded(backgroundColor, backgroundColor, 20)
    }

    private fun card(parent: LinearLayout, padding: Int, fill: String = "#131E2D", stroke: String = "#26374B"): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
            background = rounded(fill, stroke, 20)
        }
        parent.addView(box, LinearLayout.LayoutParams(-1, -2))
        return box
    }

    private fun text(parent: LinearLayout, value: String, size: Float, tint: String, bold: Boolean): TextView {
        val view = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color(tint))
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = true
        }
        parent.addView(view, LinearLayout.LayoutParams(-2, -2))
        return view
    }

    private fun actionButton(parent: LinearLayout, label: String, fill: String, foreground: String, action: () -> Unit) {
        val button = text(parent, label, 14f, foreground, true)
        button.gravity = Gravity.CENTER
        button.setPadding(dp(16), dp(14), dp(16), dp(14))
        button.background = rounded(fill, fill, 14)
        button.setOnClickListener { action() }
        button.layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun outlineButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = text(parent, label, 13f, "#BDEFE3", true)
        button.gravity = Gravity.CENTER
        button.setPadding(dp(14), dp(12), dp(14), dp(12))
        button.background = rounded("#172638", "#36526B", 13)
        button.setOnClickListener { action() }
        button.layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun row(parent: LinearLayout): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun gap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun gapWidth(parent: LinearLayout, width: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(dp(width), 1))
    }

    private fun divider(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(color("#1D2A3B")) },
            LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun rounded(fill: String, stroke: String, radius: Int) = GradientDrawable().apply {
        setColor(color(fill))
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), color(stroke))
    }

    private fun color(value: String): Int = Color.parseColor(value)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun isRunning() = prefs.getBoolean("overlay_running", false)

    private fun isChatGptAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val expected = android.content.ComponentName(this, ChatGPTMonitorService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun go(page: Page) {
        currentPage = page
        render()
    }

    private fun launchOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            toast("Allow display over other apps, then tap Launch again.")
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            toast("Allow notifications, then tap Launch again.")
            return
        }
        val intent = Intent(this, OpenFlowOverlayService::class.java).setAction(OpenFlowOverlayService.ACTION_START)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            toast("Floating companion started.")
        } catch (_: Exception) {
            toast("Could not start the companion. Check Android permissions.")
        }
        render()
    }

    private fun stopOverlay() {
        startService(Intent(this, OpenFlowOverlayService::class.java).setAction(OpenFlowOverlayService.ACTION_STOP))
        toast("Floating companion stopped.")
        render()
    }

    private fun copyText(label: String, value: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        toast("Copied to clipboard.")
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 4102
    }
}
