package com.example.filemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {
    private var drive = "C"
    private var cur: File = U.cRoot()
    private var items: List<File> = emptyList()
    private var clip: File? = null
    private var clipMove = false
    private val undoStack = mutableListOf<Pair<String, () -> Unit>>()
    private var asked = false
    private var fg = Color.BLACK
    private lateinit var listView: ListView
    private lateinit var pathView: TextView
    private lateinit var adapter: BaseAdapter

    private fun dp(v: Int) = U.dp(this, v)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onCreate(b: Bundle?) { super.onCreate(b); U.eRoot(this) }

    override fun onResume() {
        super.onResume()
        buildUi()
        if (!U.hasAccess(this) && !asked) {
            asked = true
            U.builder(this).setTitle("Allow file access")
                .setMessage("To browse files and create folders on device storage, grant \"All files access\".")
                .setPositiveButton("Grant") { _, _ -> U.askAccess(this) }
                .setNegativeButton("Later", null).glass()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val root = if (drive == "E") U.eRoot(this) else U.cRoot()
        if (cur.canonicalPath != root.canonicalPath) up() else super.onBackPressed()
    }

    // ---------- UI ----------
    private fun lp1() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }

    private fun buildUi() {
        val bg = U.bg(this); fg = U.text(); U.styleWindow(this, bg)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; fitsSystemWindows = true
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        val tabs = LinearLayout(this)
        for ((label, d) in listOf("PE:/ file folder" to "C", "E:/app folder" to "E")) {
            tabs.addView(U.btn(this, label, if (drive == d) 0xFFA66BF5.toInt() else U.PURPLE) { switchDrive(d) }, lp1())
        }
        root.addView(tabs)

        val bar = LinearLayout(this).apply { background = U.rounded(0xFFBFB8D6.toInt(), dp(16).toFloat()); setPadding(dp(8), dp(6), dp(8), dp(6)) }
        val tools = listOf<Pair<String, () -> Unit>>(
            "search" to { searchDialog() },
            "settings" to { startActivity(Intent(this, SettingsActivity::class.java)) },
            "pin file" to { pinDialog() },
            "⬆ up" to { up() }
        )
        for ((label, act) in tools) bar.addView(U.btn(this, label, Color.WHITE) { act() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(4), 0, dp(4), 0) })
        root.addView(HorizontalScrollView(this).apply { addView(bar) })

        pathView = TextView(this).apply {
            setTextColor(U.text()); textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = U.rounded(0xFFBFB8D6.toInt(), dp(14).toFloat()); setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(pathView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(6), 0, dp(6)) })

        adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(p: Int) = items[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
                val f = items[p]; val sp = U.textSp(this@MainActivity)
                val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
                box.addView(TextView(this@MainActivity).apply { text = (if (f.isDirectory) "📁 " else "📄 ") + f.name; textSize = sp.toFloat(); setTextColor(fg) })
                val sub = if (f.isDirectory) "${f.list()?.size ?: 0} items" else U.size(f.length())
                box.addView(TextView(this@MainActivity).apply {
                    text = "$sub · ${U.date(f.lastModified())}"; textSize = (sp - 4).coerceAtLeast(9).toFloat()
                    setTextColor(Color.argb(160, Color.red(fg), Color.green(fg), Color.blue(fg)))
                })
                return box
            }
        }
        listView = ListView(this).apply {
            divider = null; adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, p, _ -> open(items[p]) }
            setOnItemLongClickListener { _, _, p, _ -> menu(items[p]); true }
        }
        val listLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(0, dp(4), 0, dp(8)) }
        if (U.isGlass()) {
            val card = FrameLayout(this).apply {
                background = U.rounded(Color.WHITE, dp(26).toFloat()); clipToOutline = true; setPadding(dp(4), dp(6), dp(4), dp(6))
                addView(listView)
            }
            root.addView(card, listLp)
        } else root.addView(listView, listLp)

        val bottom = LinearLayout(this).apply { background = U.rounded(0xFF86A2CC.toInt(), dp(22).toFloat()); setPadding(dp(10), dp(8), dp(10), dp(8)); gravity = Gravity.CENTER_VERTICAL }
        bottom.addView(U.btn(this, "Paste") { paste() }, lp1())
        bottom.addView(U.btn(this, "+", Color.WHITE) { plusMenu() }, lp1())
        bottom.addView(U.btn(this, "undo") { undo() }, lp1())
        root.addView(bottom)

        setContentView(root)
        refresh()
    }

    private fun refresh() {
        val flt = U.prefs(this).getString("filter", "all")
        val img = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        val arc = setOf("zip", "rar", "7z", "tar", "gz")
        val web = setOf("html", "htm", "css", "js")
        val all = (cur.listFiles()?.toList() ?: emptyList()).filter {
            when (flt) {
                "text" -> it.isDirectory || U.isText(it.name)
                "images" -> it.isDirectory || U.ext(it.name) in img
                "archives" -> it.isDirectory || U.ext(it.name) in arc
                "web" -> it.isDirectory || U.ext(it.name) in web
                "folders" -> it.isDirectory
                else -> true
            }
        }
        val cmp: Comparator<File> = when (U.sort(this)) {
            "name" -> compareBy<File> { it.name.lowercase() }
            "size" -> compareByDescending<File> { if (it.isDirectory) 0L else it.length() }
            else -> compareByDescending<File> { it.lastModified() }
        }
        items = all.sortedWith(compareByDescending<File> { it.isDirectory }.then(cmp))
        pathView.text = (if (drive == "E") "E:" else "") + cur.absolutePath
        adapter.notifyDataSetChanged()
    }

    private fun switchDrive(d: String) { drive = d; cur = if (d == "E") U.eRoot(this) else U.cRoot(); buildUi() }
    private fun navigate(dir: File) {
        drive = if (dir.absolutePath.startsWith(U.eRoot(this).absolutePath)) "E" else "C"; cur = dir; buildUi()
    }
    private fun up() {
        val root = if (drive == "E") U.eRoot(this) else U.cRoot()
        if (cur.canonicalPath != root.canonicalPath) cur.parentFile?.let { cur = it; refresh() }
    }

    // ---------- opening ----------
    private fun open(f: File) {
        when {
            f.isDirectory -> { cur = f; refresh() }
            U.ext(f.name) in APK_EXT -> installApk(f)
            U.ext(f.name) == "zip" -> U.builder(this).setTitle(f.name)
                .setItems(arrayOf("Extract here", "Run in sandbox", "Open with…")) { _, w ->
                    when (w) { 0 -> extract(f); 1 -> sandbox(f); else -> viewExternal(f) }
                }.glass()
            U.isText(f.name) -> edit(f)
            else -> viewExternal(f)
        }
    }
    private fun uri(f: File) = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
    private fun viewExternal(f: File) {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri(f), U.mime(f.name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open with"))
        } catch (e: Exception) { toast("No app can open this file") }
    }
    private fun chrome(f: File) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(f), U.mime(f.name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).setPackage("com.android.chrome"))
        } catch (e: Exception) { toast("Chrome couldn't open this file") }
    }
    private fun share(f: File) {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(U.mime(f.name)).putExtra(Intent.EXTRA_STREAM, uri(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
        } catch (e: Exception) { toast("Can't share this") }
    }
    private val APK_EXT = setOf("apk", "apks", "xapk", "apkm")
    private fun installApk(f: File) = startActivity(Intent(this, ApkInstallActivity::class.java).putExtra("path", f.absolutePath))
    private fun afterApk(out: File) {
        refresh()
        U.builder(this).setTitle("Done").setMessage("Saved: ${out.name}")
            .setPositiveButton("Install") { _, _ -> installApk(out) }.setNegativeButton("OK", null).glass()
    }
    private fun edit(f: File) = startActivity(Intent(this, EditorActivity::class.java).putExtra("path", f.absolutePath))
    private fun sandbox(f: File) = startActivity(Intent(this, SandboxActivity::class.java).putExtra("path", f.absolutePath))

    // ---------- long-press menu ----------
    private fun menu(f: File) {
        val acts = mutableListOf<Pair<String, () -> Unit>>()
        fun add(label: String, a: () -> Unit) { acts.add(Pair(label, a)) }
        if (f.isFile && U.ext(f.name) in APK_EXT) add("Install") { installApk(f) }
        if (f.isFile && U.ext(f.name) == "apk") {
            add("Sign APK…") { ApkTools.signDialog(this, f) { afterApk(it) } }
            add("Clone APK (auto sign)…") { ApkTools.cloneDialog(this, f) { afterApk(it) } }
        }
        if (f.isFile && U.ext(f.name) in setOf("p12", "pfx", "bks", "keystore")) add("Use as signing key") {
            U.prefs(this).edit().putString("ks_path", f.absolutePath).apply(); toast("Saved. Choose \"Custom keystore\" when signing")
        }
        if (f.isFile && U.ext(f.name) in setOf("html", "htm", "zip")) add("Sandbox") { sandbox(f) }
        add("Properties") { props(f) }
        if (f.isFile) { add("Share") { share(f) }; add("Open in Chrome") { chrome(f) }; add("Edit") { edit(f) } }
        add("Rename") { rename(f) }
        add("Cut / Move") { clip = f; clipMove = true; toast("Cut. Open a folder and tap Paste") }
        add("Copy") { clip = f; clipMove = false; toast("Copied. Open a folder and tap Paste") }
        if (f.isFile && U.ext(f.name) == "zip") add("Extract here") { extract(f) }
        add("Zip") { zip(f) }
        add("Pin") { pin(f) }
        add("Delete") { delete(f) }
        U.builder(this).setTitle(f.name).setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.glass()
    }

    private fun ask(title: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(this).apply { setText(initial); inputType = InputType.TYPE_CLASS_TEXT; setSelection(0, initial.substringBeforeLast('.', initial).length) }
        U.builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> val s = et.text.toString().trim(); if (s.isNotEmpty() && !s.contains('/')) ok(s) else toast("Invalid name") }
            .setNegativeButton("Cancel", null).glass()
    }

    private fun push(label: String, a: () -> Unit) = runOnUiThread { undoStack.add(Pair(label, a)) }
    private fun work(msg: String, job: () -> Unit) {
        toast(msg)
        Thread {
            val err = try { job(); null } catch (e: Exception) { e.message ?: "failed" }
            runOnUiThread { toast(if (err != null) "Failed: $err" else "Done"); refresh() }
        }.start()
    }

    private fun rename(f: File) = ask("Rename (e.g. .txt → .html)", f.name) { n ->
        val t = File(f.parentFile, n)
        if (t.exists()) toast("Name already exists") else if (f.renameTo(t)) { push("rename") { t.renameTo(f) }; refresh() } else toast("Rename failed")
    }
    private fun delete(f: File) = U.builder(this).setTitle("Delete ${f.name}?").setMessage("This can't be undone.")
        .setPositiveButton("Delete") { _, _ -> work("Deleting…") { if (!f.deleteRecursively()) throw Exception("not allowed") } }
        .setNegativeButton("Cancel", null).glass()
    private fun paste() {
        val s = clip ?: return toast("Nothing to paste")
        if (!s.exists()) { clip = null; return toast("Source no longer exists") }
        if (s.isDirectory && (cur.canonicalPath + "/").startsWith(s.canonicalPath + "/")) return toast("Can't paste a folder into itself")
        val dst = U.uniq(cur, s.name); val mv = clipMove
        work(if (mv) "Moving…" else "Copying…") {
            if (mv) {
                if (!s.renameTo(dst)) { U.copy(s, dst); s.deleteRecursively() }
                push("move") { if (!dst.renameTo(s)) { U.copy(dst, s); dst.deleteRecursively() } }
                clip = null
            } else { U.copy(s, dst); push("copy") { dst.deleteRecursively() } }
        }
    }
    private fun undo() {
        if (undoStack.isEmpty()) return toast("Nothing to undo")
        val (label, act) = undoStack.removeAt(undoStack.lastIndex)
        work("Undo $label…", act)
    }
    private fun zip(f: File) { val out = U.uniq(f.parentFile!!, f.name + ".zip"); work("Zipping…") { U.zip(f, out); push("zip") { out.delete() } } }
    private fun extract(f: File) { val d = U.uniq(f.parentFile!!, f.nameWithoutExtension); work("Extracting…") { U.unzip(f, d); push("extract") { d.deleteRecursively() } } }

    // ---------- create ----------
    private fun plusMenu() = U.builder(this).setTitle("Create in ${cur.name}")
        .setItems(arrayOf("New folder", "New file (.txt .xml .html …)", "New empty .zip")) { _, w ->
            when (w) {
                0 -> ask("Folder name", "New folder") { create(File(cur, it), true) }
                1 -> ask("File name with extension", "new.txt") { create(File(cur, it), false) }
                else -> ask("Zip name", "new.zip") { n -> create(File(cur, if (n.endsWith(".zip")) n else "$n.zip"), false, zip = true) }
            }
        }.glass()
    private fun create(f: File, dir: Boolean, zip: Boolean = false) {
        if (f.exists()) return toast("Already exists")
        val ok = try {
            if (dir) f.mkdirs() else if (zip) { f.writeBytes(byteArrayOf(0x50, 0x4B, 5, 6) + ByteArray(18)); true } else f.createNewFile()
        } catch (e: Exception) { false }
        if (ok) { push("create") { f.deleteRecursively() }; refresh() } else toast("Can't create here. Is All-files access granted?")
    }

    // ---------- properties / search / pins ----------
    private fun props(f: File) {
        Thread {
            val size = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
            runOnUiThread {
                U.builder(this).setTitle("Properties").setMessage(
                    "Name: ${f.name}\nPath: ${f.absolutePath}\nType: ${if (f.isDirectory) "Folder" else U.mime(f.name)}\n" +
                        "Size: ${U.size(size)}\nModified: ${U.date(f.lastModified())}\nReadable: ${f.canRead()}  Writable: ${f.canWrite()}"
                ).setPositiveButton("OK", null).glass()
            }
        }.start()
    }
    private fun searchDialog() {
        val et = EditText(this).apply { hint = "file or folder name"; inputType = InputType.TYPE_CLASS_TEXT }
        U.builder(this).setTitle("Search in ${cur.name}").setView(et)
            .setPositiveButton("Search") { _, _ ->
                val q = et.text.toString().trim(); if (q.isEmpty()) return@setPositiveButton
                toast("Searching…")
                Thread {
                    val hits = cur.walkTopDown().filter { it != cur && it.name.contains(q, true) }.take(300).toList()
                    runOnUiThread {
                        if (hits.isEmpty()) toast("No results") else
                            U.builder(this).setTitle("${hits.size} result(s)")
                                .setItems(hits.map { it.relativeTo(cur).path }.toTypedArray()) { _, w ->
                                    val h = hits[w]; if (h.isDirectory) { cur = h; refresh() } else { cur = h.parentFile!!; refresh() }
                                }.glass()
                    }
                }.start()
            }.setNegativeButton("Cancel", null).glass()
    }
    private fun pin(f: File) {
        val s = U.prefs(this).getStringSet("pins", emptySet())!!.toMutableSet(); s.add(f.absolutePath)
        U.prefs(this).edit().putStringSet("pins", s).apply(); toast("Pinned")
    }
    private fun pinDialog() {
        val pins = U.prefs(this).getStringSet("pins", emptySet())!!.toList().sorted()
        var dlg: AlertDialog? = null
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        box.addView(U.btn(this, "📌 Pin this folder") { pin(cur); dlg?.dismiss(); pinDialog() })
        box.addView(U.btn(this, "Storage root (go)", Color.WHITE) { dlg?.dismiss(); navigate(U.cRoot()) })
        box.addView(U.btn(this, "Download (go)", Color.WHITE) { dlg?.dismiss(); navigate(File(U.cRoot(), "Download")) })
        for (p in pins) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = U.rounded(Color.WHITE, dp(14).toFloat()); setPadding(dp(10), dp(6), dp(10), dp(6)) }
            val label = TextView(this).apply { text = File(p).name + "\n" + p; textSize = 12f; setTextColor(U.text()) }
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(U.btn(this, "go", 0xFFBDBDBD.toInt()) {
                dlg?.dismiss(); val f = File(p)
                if (!f.exists()) toast("Not found") else if (f.isDirectory) navigate(f) else { navigate(f.parentFile!!); open(f) }
            })
            row.addView(U.btn(this, "✕", Color.WHITE) {
                val s = U.prefs(this).getStringSet("pins", emptySet())!!.toMutableSet(); s.remove(p)
                U.prefs(this).edit().putStringSet("pins", s).apply(); dlg?.dismiss(); pinDialog()
            })
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(6), 0, 0) })
        }
        dlg = U.builder(this).setTitle("Pinned").setView(ScrollView(this).apply { addView(box) }).setNegativeButton("Close", null).glass()
    }
}
