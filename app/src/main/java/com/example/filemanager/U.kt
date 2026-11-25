package com.example.filemanager

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.app.AlertDialog
import android.view.View
import android.view.WindowManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Button
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object U {
    val PURPLE = 0xFFBB86FC.toInt()
    var ctx: Context? = null

    // ---- Liquid Glass theme ----
    fun isGlass(): Boolean = ctx?.let { prefs(it).getBoolean("glass", true) } ?: false
    fun glassStyle(): String = ctx?.let { prefs(it).getString("glassStyle", "aurora") } ?: "aurora"
    fun dark(): Boolean = isGlass() && GlassBackground.isDark(glassStyle())
    /** Main text colour for the current theme. */
    fun text(): Int = if (isGlass()) (if (dark()) Color.WHITE else 0xFF14142B.toInt()) else Color.BLACK
    fun text2(): Int = if (isGlass()) (if (dark()) 0xFFC9C9E0.toInt() else 0xFF4A4A68.toInt()) else 0xFF777777.toInt()
    fun pageBg(fallback: Int): Drawable = if (isGlass()) GlassBackground(glassStyle()) else ColorDrawable(fallback)
    fun styleWindow(a: Activity, fallback: Int) {
        a.window.setBackgroundDrawable(pageBg(fallback))
        if (isGlass()) {
            a.window.statusBarColor = Color.TRANSPARENT
            a.window.navigationBarColor = Color.TRANSPARENT
            var f = a.window.decorView.systemUiVisibility
            val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            f = if (dark()) f and light.inv() else f or light
            a.window.decorView.systemUiVisibility = f
        }
    }
    fun builder(c: Context): AlertDialog.Builder = AlertDialog.Builder(c,
        if (dark()) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)
    private val TEXT = setOf("txt", "xml", "html", "htm", "js", "css", "json", "md", "py", "log", "csv", "java", "kt", "ini", "conf", "yml", "yaml", "sh", "php", "svg")

    fun prefs(c: Context) = c.getSharedPreferences("fm", Context.MODE_PRIVATE)
    fun bg(c: Context) = prefs(c).getInt("bg", Color.WHITE)
    fun textSp(c: Context) = prefs(c).getInt("textSp", 16)
    fun sort(c: Context) = prefs(c).getString("sort", "date") ?: "date"
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    fun fg(color: Int): Int {
        val l = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return if (l > 0.5) Color.BLACK else Color.WHITE
    }
    fun flat(color: Int, r: Float = 36f) = GradientDrawable().apply { setColor(color); cornerRadius = r }
    fun rounded(color: Int, r: Float = 36f): Drawable = if (isGlass()) GlassDrawable(color, r, dark()) else flat(color, r)
    fun btn(c: Context, text: String, color: Int = PURPLE, tc: Int = 0, click: () -> Unit) = Button(c).apply {
        this.text = text; isAllCaps = false; setTextColor(if (tc == 0) (if (dark()) Color.WHITE else Color.BLACK) else tc); background = rounded(color)
        minHeight = 0; minimumHeight = dp(c, 44); setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6))
        setOnClickListener { click() }
    }
    fun size(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", b / 1024.0)
        b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
        else -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
    }
    fun date(t: Long): String = SimpleDateFormat("MM/dd/yy h:mm a", Locale.US).format(Date(t))
    fun eRoot(c: Context) = File(c.filesDir, "E").apply { mkdirs() }
    fun cRoot(): File = Environment.getExternalStorageDirectory()
    fun ext(n: String) = n.substringAfterLast('.', "").lowercase()
    fun isText(n: String) = ext(n) in TEXT
    fun mime(n: String): String = when (ext(n)) {
        "html", "htm" -> "text/html"
        "xml" -> "text/xml"
        in TEXT -> "text/plain"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext(n)) ?: "*/*"
    }
    fun uniq(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val b = if (dot > 0) name.substring(0, dot) else name
        val e = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (f.exists()) { f = File(dir, "$b ($i)$e"); i++ }
        return f
    }
    fun copy(src: File, dst: File) {
        if (src.isDirectory) { dst.mkdirs(); src.listFiles()?.forEach { copy(it, File(dst, it.name)) } }
        else src.copyTo(dst, overwrite = false)
    }
    fun zip(src: File, out: File) {
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            fun add(f: File, path: String) {
                if (f.isDirectory) {
                    val kids = f.listFiles() ?: return
                    if (kids.isEmpty()) { z.putNextEntry(ZipEntry("$path/")); z.closeEntry() }
                    kids.forEach { add(it, "$path/${it.name}") }
                } else {
                    z.putNextEntry(ZipEntry(path)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
                }
            }
            add(src, src.name)
        }
    }
    fun unzip(zip: File, dest: File) {
        dest.mkdirs()
        val base = dest.canonicalPath + File.separator
        ZipFile(zip).use { zf ->
            for (e in zf.entries().asSequence()) {
                val t = File(dest, e.name)
                if (!t.canonicalPath.startsWith(base)) continue // zip-slip guard
                if (e.isDirectory) t.mkdirs() else {
                    t.parentFile?.mkdirs()
                    zf.getInputStream(e).use { i -> t.outputStream().use { o -> i.copyTo(o) } }
                }
            }
        }
    }
    fun hasAccess(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    fun askAccess(a: Activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                a.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${a.packageName}")))
            } catch (e: Exception) {
                a.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else a.requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
    }
}

/** Show a dialog with a frosted glass window (and background blur on Android 12+). */
fun AlertDialog.Builder.glass(): AlertDialog {
    val d = create()
    if (U.isGlass()) {
        val dk = U.dark()
        d.window?.setBackgroundDrawable(GlassDrawable(if (dk) 0xFF14142B.toInt() else Color.WHITE, 60f, dk, true))
    }
    d.show()
    if (U.isGlass() && Build.VERSION.SDK_INT >= 31) {
        try {
            d.window?.let { w ->
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = w.attributes; lp.blurBehindRadius = 40; w.attributes = lp
            }
        } catch (_: Throwable) {}
    }
    return d
}
