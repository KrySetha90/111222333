package com.example.filemanager

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.io.File

class EditorActivity : Activity() {
    private lateinit var file: File
    private lateinit var edit: EditText
    private var dirty = false
    private fun dp(v: Int) = U.dp(this, v)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        file = File(intent.getStringExtra("path") ?: return finish())
        if (file.length() > 2 * 1024 * 1024) { Toast.makeText(this, "File too large to edit (2 MB max)", Toast.LENGTH_LONG).show(); finish(); return }
        val text = try { file.readText() } catch (e: Exception) { Toast.makeText(this, "Can't read file", Toast.LENGTH_LONG).show(); finish(); return }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true; setBackgroundColor(Color.WHITE) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = U.rounded(0xFFBB8CFF.toInt(), dp(16).toFloat()); setPadding(dp(8), dp(6), dp(8), dp(6)) }
        top.addView(U.btn(this, "◀") { onBackPressed() })
        val q = EditText(this).apply { hint = "search"; inputType = InputType.TYPE_CLASS_TEXT; setSingleLine(); imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ -> find(text.toString()); true } }
        top.addView(q, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(U.btn(this, "↓ next") { find(q.text.toString()) })
        top.addView(U.btn(this, "Save", 0xFF4FFAF2.toInt()) { save() })
        root.addView(top)

        root.addView(TextView(this).apply { this.text = file.name; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.BLACK); setPadding(dp(16), dp(10), dp(16), dp(4)) })
        edit = EditText(this).apply {
            setText(text); gravity = Gravity.TOP or Gravity.START; typeface = Typeface.MONOSPACE; setTextColor(Color.BLACK)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = U.rounded(0xFFBFB8D6.toInt(), dp(14).toFloat()); setPadding(dp(14), dp(12), dp(14), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { dirty = true }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        dirty = false
        root.addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(dp(8), dp(4), dp(8), dp(8)) })
        setContentView(root)
    }

    private fun find(q: String) {
        if (q.isEmpty()) return
        val t = edit.text.toString()
        var i = t.indexOf(q, edit.selectionEnd.coerceAtLeast(0), true)
        if (i < 0) i = t.indexOf(q, 0, true)
        if (i < 0) Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show() else { edit.requestFocus(); edit.setSelection(i, i + q.length) }
    }
    private fun save(): Boolean = try {
        file.writeText(edit.text.toString()); dirty = false
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(); true
    } catch (e: Exception) { Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show(); false }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!dirty) { super.onBackPressed(); return }
        AlertDialog.Builder(this).setTitle("Unsaved changes")
            .setPositiveButton("Save") { _, _ -> if (save()) finish() }
            .setNegativeButton("Discard") { _, _ -> dirty = false; finish() }
            .setNeutralButton("Cancel", null).show()
    }
}
