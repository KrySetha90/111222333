package com.example.filemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*

class SettingsActivity : Activity() {
    private fun dp(v: Int) = U.dp(this, v)
    private fun p() = U.prefs(this)
    override fun onCreate(b: Bundle?) { super.onCreate(b); render() }
    override fun onResume() { super.onResume(); render() }

    private fun card(title: String, sub: String?, click: (() -> Unit)?): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = U.rounded(Color.BLACK, dp(18).toFloat()); setPadding(dp(18), dp(14), dp(18), dp(14))
        addView(TextView(context).apply { text = title; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE) })
        if (sub != null) addView(TextView(context).apply { text = sub; textSize = 15f; setTextColor(0xFFBBBBBB.toInt()) })
        if (click != null) setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(10)) }
    }

    private fun render() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(16)) }
        col.addView(U.btn(this, "◀ back") { finish() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(10)) })
        val names = mapOf("all" to "All files", "text" to "Text & code", "images" to "Images", "archives" to "Archives", "web" to "Web files", "folders" to "Folders only")
        col.addView(card("FILE TYPE", "Show: ${names[p().getString("filter", "all")]}") { pickFilter(names) })
        col.addView(card("CUSTOM · background color", String.format("#%06X", U.bg(this) and 0xFFFFFF)) { pickColor() })
        col.addView(card("CUSTOM · text size", "${U.textSp(this)} sp") { pickSize() })
        col.addView(card("CUSTOM · sort by", mapOf("date" to "Date modified", "name" to "Name", "size" to "Size")[U.sort(this)]) { pickSort() })
        col.addView(card("ROOT ACCESS", "Shizuku / SUI: not connected yet (placeholder)") {
            Toast.makeText(this, "Shizuku / SUI support isn't wired up yet", Toast.LENGTH_SHORT).show()
        })
        col.addView(card("REPORT THE PROBLEM", "Send a note with device info") {
            val t = "Problem:\n\n\nDevice: ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nApp: 1.0"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Report"))
        })
        col.addView(card("permission", if (U.hasAccess(this)) "All-files access: granted" else "All-files access: NOT granted. Tap to grant") { U.askAccess(this) })
        setContentView(ScrollView(this).apply { setBackgroundColor(0xFFCDB4E8.toInt()); fitsSystemWindows = true; addView(col) })
    }

    private fun pickFilter(names: Map<String, String>) {
        val keys = names.keys.toList()
        AlertDialog.Builder(this).setTitle("File type").setItems(names.values.toTypedArray()) { _, w -> p().edit().putString("filter", keys[w]).apply(); render() }.show()
    }
    private fun pickSort() {
        val keys = arrayOf("date", "name", "size")
        AlertDialog.Builder(this).setTitle("Sort by").setItems(arrayOf("Date modified", "Name", "Size")) { _, w -> p().edit().putString("sort", keys[w]).apply(); render() }.show()
    }
    private fun pickSize() {
        val sb = SeekBar(this).apply { max = 16; progress = U.textSp(context) - 12 }
        val lab = TextView(this).apply { text = "${U.textSp(context)} sp"; textSize = 16f }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) { lab.text = "${v + 12} sp" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(8)); addView(lab); addView(sb) }
        AlertDialog.Builder(this).setTitle("Text size").setView(box)
            .setPositiveButton("Apply") { _, _ -> p().edit().putInt("textSp", sb.progress + 12).apply(); render() }
            .setNegativeButton("Cancel", null).show()
    }
    private fun pickColor() {
        val colors = intArrayOf(val colors = intArrayOf(
    0xFFFF4545.toInt(),
    0xFFF4B34A.toInt(),
    0xFFF9E86B.toInt(),
    0xFF22DD22.toInt(),
    0xFF18E0E0.toInt(),
    0xFF4050F5.toInt(),
    0xFFDD44DD.toInt(),
    0xFFFFFFFF.toInt(),
    0xFFBDBDBD.toInt(),
    0xFF888888.toInt(),
    0xFF444444.toInt(),
    0xFF000000.toInt(),
    0xFF5BB5D8.toInt(),
    0xFF99C99F.toInt(),
    0xFF00FF00.toInt(),
    0xFF525C6C.toInt(),
    0xFF9CC8E8.toInt(),
    0xFFBB86FC.toInt(),
    0xFF0AC82A.toInt(),
    0xFF3D4D6B.toInt(),
    0xFFBFB8D6.toInt(),
    0xFFE0D4F5.toInt()
    )}
        var sel = U.bg(this)
        val lab = TextView(this).apply { textSize = 18f; gravity = android.view.Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), dp(10)) }
        fun upd() { lab.text = String.format("#%06X (100%%)", sel and 0xFFFFFF); lab.setBackgroundColor(sel); lab.setTextColor(U.fg(sel)) }
        upd()
        val grid = GridLayout(this).apply { columnCount = 6 }
        for (c in colors) grid.addView(View(this).apply {
            background = U.rounded(c, dp(8).toFloat())
            layoutParams = GridLayout.LayoutParams().apply { width = dp(44); height = dp(44); setMargins(dp(4), dp(4), dp(4), dp(4)) }
            setOnClickListener { sel = c; upd() }
        })
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(8)); addView(lab); addView(grid) }
        AlertDialog.Builder(this).setTitle("Background color").setView(box)
            .setPositiveButton("Apply") { _, _ -> p().edit().putInt("bg", sel).apply(); render() }
            .setNegativeButton("Cancel", null).show()
    }
}
