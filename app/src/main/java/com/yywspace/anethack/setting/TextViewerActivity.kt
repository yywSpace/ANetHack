package com.yywspace.anethack.setting

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.yywspace.anethack.R
import com.yywspace.anethack.theme.AppTheme
import java.io.File

/** 在应用内查看文本文件（dump 日志、app 日志、崩溃报告），支持双指缩放 */
class TextViewerActivity : AppCompatActivity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyTheme(this)
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run {
            finish()
            return
        }
        val file = File(path)
        setContentView(R.layout.activity_text_viewer)
        val toolbar = findViewById<Toolbar>(R.id.viewer_toolbar)
        toolbar.title = file.name
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { finish() }
        web = findViewById(R.id.viewer_web)

        web.settings.apply {
            javaScriptEnabled = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        AppTheme.applyStatusBar(this)

        val background = String.format("#%06X", 0xFFFFFF and getColor(R.color.app_background))
        val color = String.format("#%06X", 0xFFFFFF and getColor(R.color.app_text))
        Thread {
            val text = runCatching { file.readText() }.getOrNull() ?: ""
            val html = """
                <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { margin: 12px; background: $background; color: $color; }
                    pre { font-family: monospace; font-size: 14px; white-space: pre; }
                </style></head>
                <body><pre>${escape(text)}</pre></body></html>
            """.trimIndent()
            runOnUiThread { web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) }
        }.start()
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        const val EXTRA_PATH = "path"
    }
}
