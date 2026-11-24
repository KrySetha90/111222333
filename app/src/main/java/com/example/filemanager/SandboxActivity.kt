package com.example.filemanager

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import java.io.File

/** Runs a .html file or a .zip website inside a locked-down WebView and reports per-file status. */
class SandboxActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var header: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var src: File
    private var base: File? = null
    private var entry: File? = null
    private var selected: File? = null
    private var files: List<File> = emptyList()
    private val status = HashMap<String, String>()
    private var progress = 0

override fun onProgressChanged(v: WebView, p: Int) {
    progress = p
    render()
}
    private var editing = false
    private fun dp(v: Int) = U.dp(this, v)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        src = File(intent.getStringExtra("path") ?: return finish())
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true; setBackgroundColor(Color.WHITE); setPadding(dp(10), dp(4), dp(10), dp(8)) }
        root.addView(TextView(this).apply { text = "website tester / app viewer for test"; textSize = 18f; setTextColor(Color.BLACK) })

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                    mark(r.url.lastPathSegment ?: "", "error: ${e.description}")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(v: WebView, p: Int) { progress = p; render() }
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                        mark(m.sourceId().substringAfterLast('/'), "JS error line ${m.lineNumber()}: ${m.message()}")
                    return true
                }
            }
        }
        val box = FrameLayout(this).apply { background = U.rounded(0xFF506C9E.toInt(), dp(18).toFloat()); clipToOutline = true; setPadding(dp(4), dp(4), dp(4), dp(4)) }
        box.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(0, dp(6), 0, dp(6)) })

        header = TextView(this).apply { textSize = 15f; setTextColor(0xFF777777.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD) }
        root.addView(header)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(listBox) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)))
        root.addView(U.btn(this, "edit") {
            val f = selected ?: entry
            if (f != null) { editing = true; startActivity(Intent(this, EditorActivity::class.java).putExtra("path", f.absolutePath)) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.END })
        setContentView(root)
        render()

        Thread {
            try {
                if (U.ext(src.name) == "zip") {
                    val d = File(cacheDir, "sandbox/${src.nameWithoutExtension}"); d.deleteRecursively(); U.unzip(src, d); base = d
                    files = d.walkTopDown().filter { it.isFile }.take(60).toList()
                    entry = files.filter { it.name.equals("index.html", true) }.minByOrNull { it.absolutePath.length }
                        ?: files.firstOrNull { U.ext(it.name) in setOf("html", "htm") }
                } else { base = src.parentFile; entry = src; files = listOf(src) }
                selected = entry
                for (f in files) {
                    status[f.absolutePath] = try {
                        val buf = ByteArray(8192); f.inputStream().use { while (it.read(buf) >= 0) { } }
                        if (U.ext(f.name) == "py") "read ok, can't run here (no Python server in the app)" else "okay 100%"
                    } catch (e: Exception) { "error: ${e.message}" }
                }
            } catch (e: Exception) { runOnUiThread { Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show() } }
            runOnUiThread {
                render()
                val e = entry
                if (e != null) web.loadUrl(Uri.fromFile(e).toString()) else Toast.makeText(this, "No .html file found to run", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun mark(name: String, s: String) = runOnUiThread {
        files.firstOrNull { it.name == name }?.let { status[it.absolutePath] = s }; render()
    }

    private fun render() {
        header.text = "file details runner : ${src.name}  :$progress%"
        listBox.removeAllViews()
        for (f in files) {
            val rel = base?.let { f.relativeTo(it).path } ?: f.name
            listBox.addView(TextView(this).apply {
                text = "- $rel   : ${status[f.absolutePath] ?: "checking…"}"; textSize = 14f
                setTextColor(0xFF777777.toInt()); setPadding(dp(4), dp(4), dp(4), dp(4))
                if (f == selected) setBackgroundColor(0xFFE0D4F5.toInt())
                setOnClickListener { selected = f; render() }
            })
        }
    }

    override fun onResume() { super.onResume(); if (editing) { editing = false; web.reload() } }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
