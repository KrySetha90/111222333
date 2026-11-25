@file:Suppress("DEPRECATION")

package com.example.filemanager

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** In-app APK installer (PackageInstaller sessions), also handles .apks / .xapk / .apkm bundles. */
class ApkInstallActivity : Activity() {
    private val ACTION = "com.example.filemanager.INSTALL_RESULT"
    private lateinit var src: File
    private lateinit var info: TextView
    private lateinit var status: TextView
    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var installBtn: Button
    private lateinit var removeBtn: Button
    private var apks: List<File> = emptyList()
    private var pkg: String? = null
    private var ready = false
    private var installed = false
    private var opened = false
    private fun dp(v: Int) = U.dp(this, v)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else i.getParcelableExtra(Intent.EXTRA_INTENT)
                    status.text = "Waiting for confirmation…"
                    confirm?.let { startActivity(it) }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    status.text = "✅ Installed successfully"; installed = true; opened = true
                    installBtn.isEnabled = true; installBtn.text = "Open"
                    installBtn.setOnClickListener { pkg?.let { p -> packageManager.getLaunchIntentForPackage(p)?.let { startActivity(it) } } }
                }
                else -> {
                    val msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"
                    status.text = "❌ $msg" + if (msg.contains("INCOMPATIBLE") || msg.contains("signatures") || msg.contains("CONFLICT", true))
                        "\n\nThe installed copy is signed with a different key. Uninstall it first, or sign this APK with the same key." else ""
                    installBtn.isEnabled = true
                }
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        src = File(intent.getStringExtra("path") ?: return finish())
        U.styleWindow(this, Color.WHITE)
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(14)) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = U.rounded(Color.WHITE, dp(26).toFloat()); setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        icon = ImageView(this)
        head.addView(icon, LinearLayout.LayoutParams(dp(64), dp(64)))
        title = TextView(this).apply { textSize = 20f; setTextColor(U.text()); setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(dp(14), 0, 0, 0); text = src.name }
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(head)
        info = TextView(this).apply { textSize = 14f; setTextColor(U.text2()); setPadding(0, dp(14), 0, 0); text = "Reading APK…" }
        card.addView(info)
        col.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        status = TextView(this).apply { textSize = 15f; setTextColor(U.text()); setPadding(dp(8), dp(12), dp(8), dp(4)) }
        col.addView(status)
        val row = LinearLayout(this).apply { gravity = Gravity.END }
        removeBtn = U.btn(this, "Uninstall existing", Color.WHITE) { pkg?.let { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$it"))) } }.apply { visibility = android.view.View.GONE }
        installBtn = U.btn(this, "Install") { install() }.apply { isEnabled = false }
        row.addView(removeBtn); row.addView(U.btn(this, "Cancel", Color.WHITE) { finish() }); row.addView(installBtn)
        col.addView(row)
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(col) })
        load()
    }

    private fun load() = Thread {
        try {
            val files = if (U.ext(src.name) == "apk") listOf(src) else {
                val d = File(cacheDir, "install/${src.nameWithoutExtension}"); d.deleteRecursively(); d.mkdirs()
                val out = ArrayList<File>()
                ZipFile(src).use { zf ->
                    for ((i, e) in zf.entries().asSequence().withIndex()) if (!e.isDirectory && e.name.endsWith(".apk", true)) {
                        val t = File(d, "${i}_${e.name.substringAfterLast('/')}")
                        zf.getInputStream(e).use { ins -> t.outputStream().use { ins.copyTo(it) } }; out.add(t)
                    }
                }
                if (out.isEmpty()) throw Exception("No .apk inside this archive")
                out
            }
            val base = files.firstOrNull { it.name.endsWith("base.apk") } ?: files.maxByOrNull { it.length() }!!
            val flags = PackageManager.GET_PERMISSIONS or (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
            val pi = packageManager.getPackageArchiveInfo(base.absolutePath, flags) ?: throw Exception("Not a valid APK (can't parse)")
            val ai = pi.applicationInfo!!; ai.sourceDir = base.absolutePath; ai.publicSourceDir = base.absolutePath
            val label = ai.loadLabel(packageManager).toString(); val ic = ai.loadIcon(packageManager)
            val sigs = if (Build.VERSION.SDK_INT >= 28) pi.signingInfo?.apkContentsSigners else pi.signatures
            val sha = sigs?.firstOrNull()?.let { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.chunked(32).joinToString("\n") } ?: "unsigned"
            val old = try { packageManager.getPackageInfo(pi.packageName, 0) } catch (e: Exception) { null }
            val perms = pi.requestedPermissions?.map { it.substringAfterLast('.') } ?: emptyList()
            val oldCode = old?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
            val newCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            val text = buildString {
                append("Package: ${pi.packageName}\nVersion: ${pi.versionName} ($newCode)\nMin SDK: ${ai.minSdkVersion}   Target SDK: ${ai.targetSdkVersion}\n")
                append("Size: ${U.size(files.sumOf { it.length() })}${if (files.size > 1) "  (${files.size} APK files)" else ""}\n")
                if (old != null) append("Installed: ${old.versionName} ($oldCode)  →  ${if (newCode > oldCode!!) "update" else if (newCode == oldCode) "reinstall" else "DOWNGRADE (will likely fail)"}\n")
                append("\nSignature SHA-256:\n$sha\n")
                append("\nPermissions (${perms.size}): ${perms.take(14).joinToString(", ")}${if (perms.size > 14) " …" else ""}")
            }
            apks = files; pkg = pi.packageName; installed = old != null
            runOnUiThread {
                title.text = label; icon.setImageDrawable(ic); info.text = text
                ready = true; refreshButtons()
            }
        } catch (e: Exception) { runOnUiThread { info.text = "Can't read this file."; status.text = "❌ ${e.message}" } }
    }.start()

    private fun refreshButtons() {
        if (!ready) return
        removeBtn.visibility = if (installed) android.view.View.VISIBLE else android.view.View.GONE
        installBtn.isEnabled = true
        installBtn.text = if (installed) "Update" else "Install"
        status.text = if (!packageManager.canRequestPackageInstalls()) "To install from this app, allow \"Install unknown apps\" (you'll be asked when you tap Install)." else ""
    }

    override fun onResume() {
        super.onResume()
        if (ready && !opened) {
            installed = try { packageManager.getPackageInfo(pkg!!, 0); true } catch (e: Exception) { false }
            refreshButtons()
        }
    }

    private fun install() {
        if (!packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))); return
        }
        installBtn.isEnabled = false; status.text = "Installing…"
        Thread {
            try {
                val pi = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                pkg?.let { params.setAppPackageName(it) }
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                val id = pi.createSession(params)
                pi.openSession(id).use { s ->
                    for ((i, f) in apks.withIndex()) {
                        f.inputStream().use { ins -> s.openWrite("${i}_${f.name}", 0, f.length()).use { o -> ins.copyTo(o); s.fsync(o) } }
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    s.commit(PendingIntent.getBroadcast(this, id, Intent(ACTION).setPackage(packageName), flags).intentSender)
                }
            } catch (e: Exception) { runOnUiThread { status.text = "❌ ${e.message}"; installBtn.isEnabled = true } }
        }.start()
    }

    override fun onDestroy() { try { unregisterReceiver(receiver) } catch (_: Exception) {}; super.onDestroy() }
}
