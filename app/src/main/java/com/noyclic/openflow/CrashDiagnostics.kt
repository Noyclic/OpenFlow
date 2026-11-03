package com.noyclic.openflow

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures fatal crashes in the main app process and opens a tiny report screen
 * in a separate process, so the report remains visible after the original process dies.
 */
class OpenFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // The crash viewer runs in :crash. Never install the handler there,
        // otherwise a crash in the viewer could reopen the viewer forever.
        if (currentProcessName() != packageName) return

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val report = createCrashReport(thread, error)

            // Persist first: the viewer is a separate process and reads this file.
            runCatching {
                File(filesDir, CRASH_FILE).writeText(report, Charsets.UTF_8)
            }

            // Start the viewer in its own process before terminating this one.
            runCatching {
                startActivity(
                    Intent(this, CrashReportActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
            }

            // Give ActivityManager a moment to launch :crash. The original
            // process is then terminated so Android does not keep a broken UI.
            try {
                Thread.sleep(900)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            Process.killProcess(Process.myPid())
            kotlin.system.exitProcess(10)
        }
    }

    private fun currentProcessName(): String {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName()
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }
            ?.processName ?: packageName
    }

    private fun createCrashReport(thread: Thread, error: Throwable): String {
        val trace = StringWriter().also { writer ->
            error.printStackTrace(PrintWriter(writer))
        }.toString()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        return buildString {
            appendLine("OpenFlow crash report")
            appendLine("Time: $date")
            appendLine("App: $packageName")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine("Exception:")
            append(trace)
        }
    }

    companion object {
        const val CRASH_FILE = "openflow_last_crash.txt"
    }
}

/** Minimal crash screen with a one-tap copy action. Runs separately from the app UI. */
class CrashReportActivity : Activity() {
    private var report: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        report = runCatching {
            File(filesDir, OpenFlowApplication.CRASH_FILE).readText(Charsets.UTF_8)
        }.getOrElse {
            "OpenFlow crashed, but the detailed report could not be read.\n\n${it.javaClass.name}: ${it.message}"
        }
        showReport()
    }

    private fun showReport() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 20)
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        val title = TextView(this).apply {
            text = "OpenFlow остановился"
            textSize = 22f
            setTextColor(Color.rgb(25, 35, 50))
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        val description = TextView(this).apply {
            text = "Я сохранил отчёт об ошибке. Нажми кнопку ниже и отправь скопированный текст сюда."
            textSize = 14f
            setTextColor(Color.rgb(65, 75, 90))
            setPadding(0, 12, 0, 16)
        }
        root.addView(description, LinearLayout.LayoutParams(-1, -2))

        val copy = Button(this).apply {
            text = "Копировать отчёт"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OpenFlow crash report", report))
                Toast.makeText(this@CrashReportActivity, "Отчёт скопирован. Вставь его в чат.", Toast.LENGTH_LONG).show()
            }
        }
        root.addView(copy, LinearLayout.LayoutParams(-1, -2))

        val detailsTitle = TextView(this).apply {
            text = "Подробности ошибки"
            textSize = 16f
            setTextColor(Color.rgb(25, 35, 50))
            setPadding(0, 18, 0, 8)
        }
        root.addView(detailsTitle, LinearLayout.LayoutParams(-1, -2))

        val details = TextView(this).apply {
            text = report
            textSize = 12f
            setTextColor(Color.rgb(35, 45, 60))
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val scroll = ScrollView(this)
        scroll.addView(details, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val close = Button(this).apply {
            text = "Закрыть"
            setOnClickListener { finish() }
        }
        root.addView(close, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }
}
