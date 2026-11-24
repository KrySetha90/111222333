package com.example.filemanager

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
    fun rounded(color: Int, r: Float = 36f) = GradientDrawable().apply { setColor(color); cornerRadius = r }
    fun btn(c: Context, text: String, color: Int = PURPLE, tc: Int = Color.BLACK, click: () -> Unit) = Button(c).apply {
        this.text = text; isAllCaps = false; setTextColor(tc); background = rounded(color)
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
