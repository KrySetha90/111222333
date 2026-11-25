package com.example.filemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.android.apksig.ApkSigner
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Collections
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class CountingOutputStream(o: OutputStream) : FilterOutputStream(o) {
    var count = 0L
    override fun write(b: Int) { out.write(b); count++ }
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
}

object ApkSign {
    class Key(val priv: PrivateKey, val chain: List<X509Certificate>)

    /** A self-generated RSA-2048 key kept in the app's private storage. */
    fun builtinKey(c: Context): Key {
        val dir = File(c.filesDir, "sign").apply { mkdirs() }
        val kf = File(dir, "builtin.key")
        val cf = File(dir, "builtin.crt")
        if (!kf.exists() || !cf.exists()) {
            val g = KeyPairGenerator.getInstance("RSA")
            g.initialize(2048)
            val kp = g.generateKeyPair()
            cf.writeBytes(CertGen.selfSigned(kp, "File Manager Test Key"))
            kf.writeBytes(kp.private.encoded)
        }
        val priv = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(kf.readBytes()))
        val cert = cf.inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        } as X509Certificate
        return Key(priv, listOf(cert))
    }

    fun customKey(path: String, storePass: String, alias: String, keyPass: String): Key {
        val f = File(path)
        if (!f.isFile) throw Exception("Keystore not found: $path")
        var found: KeyStore? = null
        var err: Exception? = null
        for (type in listOf("PKCS12", "BKS")) {
            try {
                val ks = KeyStore.getInstance(type)
                f.inputStream().use { ks.load(it, storePass.toCharArray()) }
                found = ks
                break
            } catch (e: Exception) {
                err = e
            }
        }
        val ks = found ?: throw Exception("Can't open keystore (Android reads PKCS12 / BKS, not JKS): ${err?.message}")
        val keyAlias = if (alias.isNotBlank()) alias else Collections.list(ks.aliases()).firstOrNull { ks.isKeyEntry(it) }
            ?: throw Exception("No private key in keystore")
        val priv = ks.getKey(keyAlias, (if (keyPass.isBlank()) storePass else keyPass).toCharArray()) as? PrivateKey
            ?: throw Exception("Wrong alias or key password")
        val chain = ks.getCertificateChain(keyAlias)?.map { it as X509Certificate }
            ?: throw Exception("No certificate for alias")
        return Key(priv, chain)
    }

    private fun padExtra(offset: Long, align: Int): ByteArray? {
        val pad = ((align - (offset % align)) % align).toInt()
        if (pad == 0) return null
        val size = if (pad >= 4) pad else pad + align
        val extra = ByteArray(size)
        extra[0] = 0x35
        extra[1] = 0xD9.toByte()
        extra[2] = ((size - 4) and 0xFF).toByte()
        extra[3] = ((size - 4) shr 8).toByte()
        return extra
    }

    /** Copies the APK without old signatures, keeps STORED entries aligned, optionally rewrites the manifest. */
    fun repack(input: File, output: File, patchManifest: ((ByteArray) -> ByteArray)?) {
        ZipFile(input).use { zipInput ->
            val countingOutput = CountingOutputStream(output.outputStream().buffered())
            ZipOutputStream(countingOutput).use { zipOutput ->
                for (entry in zipInput.entries().asSequence()) {
                    val name = entry.name
                    if (name.startsWith("META-INF/") && (name == "META-INF/MANIFEST.MF" || name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) continue
                    val patched = if (name == "AndroidManifest.xml" && patchManifest != null) {
                        patchManifest(zipInput.getInputStream(entry).use { it.readBytes() })
                    } else null
                    val newEntry = ZipEntry(name)
                    newEntry.time = entry.time
                    if (entry.method == ZipEntry.STORED && !entry.isDirectory) {
                        newEntry.method = ZipEntry.STORED
                        if (patched != null) {
                            newEntry.size = patched.size.toLong()
                            newEntry.compressedSize = newEntry.size
                            newEntry.crc = CRC32().also { it.update(patched) }.value
                        } else {
                            newEntry.size = entry.size
                            newEntry.compressedSize = entry.size
                            newEntry.crc = entry.crc
                        }
                        val align = if (name.endsWith(".so")) 4096 else 4
                        newEntry.extra = padExtra(countingOutput.count + 30 + name.toByteArray(Charsets.UTF_8).size, align)
                    }
                    zipOutput.putNextEntry(newEntry)
                    if (patched != null) {
                        zipOutput.write(patched)
                    } else if (!entry.isDirectory) {
                        zipInput.getInputStream(entry).use { it.copyTo(zipOutput) }
                    }
                    zipOutput.closeEntry()
                }
            }
        }
    }

    fun sign(c: Context, input: File, output: File, v1: Boolean, v2: Boolean, v3: Boolean, key: Key,
             patch: ((ByteArray) -> ByteArray)? = null, step: (String) -> Unit = {}) {
        val tmp = File(c.cacheDir, "sign_${System.nanoTime()}.apk")
        try {
            step("Preparing APK…")
            repack(input, tmp, patch)
            step("Signing…")
            val config = ApkSigner.SignerConfig.Builder("CERT", key.priv, key.chain).build()
            ApkSigner.Builder(listOf(config)).setInputApk(tmp).setOutputApk(output)
                .setV1SigningEnabled(v1).setV2SigningEnabled(v2).setV3SigningEnabled(v3)
                .setCreatedBy("File Manager").build().sign()
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            tmp.delete()
        }
    }
}

object ApkTools {
    private fun dp(a: Activity, v: Int) = U.dp(a, v)
    private fun label(a: Activity, text: String) = TextView(a).apply {
        this.text = text
        textSize = 14f
        setTextColor(U.text2())
        setPadding(0, dp(a, 10), 0, dp(a, 2))
    }
    private fun field(a: Activity, hint: String, value: String = "", pass: Boolean = false) = EditText(a).apply {
        this.hint = hint
        setText(value)
        setSingleLine()
        setTextColor(U.text())
        setHintTextColor(U.text2())
        inputType = InputType.TYPE_CLASS_TEXT or (if (pass) InputType.TYPE_TEXT_VARIATION_PASSWORD else 0)
    }
    private fun busy(a: Activity, message: String): Pair<AlertDialog, TextView> {
        val textView = TextView(a).apply {
            text = message
            textSize = 16f
            setTextColor(U.text())
            setPadding(dp(a, 24), dp(a, 20), dp(a, 24), dp(a, 20))
        }
        return Pair(U.builder(a).setTitle("Please wait").setView(textView).setCancelable(false).glass(), textView)
    }
    private fun fail(a: Activity, t: Throwable) {
        U.builder(a).setTitle("Failed").setMessage(t.message ?: t.javaClass.simpleName).setPositiveButton("OK", null).glass()
    }
    private fun runJob(a: Activity, message: String, job: ((String) -> Unit) -> File, done: (File) -> Unit) {
        val (dialog, textView) = busy(a, message)
        Thread {
            try {
                val out = job { status -> a.runOnUiThread { textView.text = status } }
                a.runOnUiThread { dialog.dismiss(); done(out) }
            } catch (t: Throwable) {
                a.runOnUiThread { dialog.dismiss(); fail(a, t) }
            }
        }.start()
    }

    fun signDialog(a: Activity, f: File, done: (File) -> Unit) {
        val preferences = U.prefs(a)
        val column = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(a, 20), dp(a, 8), dp(a, 20), dp(a, 4)) }
        val schemes = listOf("v1 + v2 + v3" to booleanArrayOf(true, true, true), "v1 + v2" to booleanArrayOf(true, true, false),
            "v1 + v3" to booleanArrayOf(true, false, true), "v1 only" to booleanArrayOf(true, false, false))
        column.addView(label(a, "Signature scheme"))
        val schemeGroup = RadioGroup(a)
        schemes.forEachIndexed { i, scheme -> schemeGroup.addView(RadioButton(a).apply { text = scheme.first; id = 100 + i; setTextColor(U.text()) }) }
        schemeGroup.check(100)
        column.addView(schemeGroup)
        column.addView(label(a, "Key"))
        val keyGroup = RadioGroup(a)
        keyGroup.addView(RadioButton(a).apply { text = "Built-in test key (auto-generated)"; id = 200; setTextColor(U.text()) })
        keyGroup.addView(RadioButton(a).apply { text = "Custom keystore (.p12 / .pfx / .bks)"; id = 201; setTextColor(U.text()) })
        keyGroup.check(200)
        column.addView(keyGroup)
        val custom = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val ksPath = field(a, "keystore path", preferences.getString("ks_path", "") ?: "")
        val ksPass = field(a, "keystore password", "", true)
        val ksAlias = field(a, "key alias (blank = first)", preferences.getString("ks_alias", "") ?: "")
        val keyPass = field(a, "key password (blank = same)", "", true)
        for (view in listOf(ksPath, ksPass, ksAlias, keyPass)) custom.addView(view)
        column.addView(custom)
        keyGroup.setOnCheckedChangeListener { _, id -> custom.visibility = if (id == 201) View.VISIBLE else View.GONE }
        column.addView(label(a, "Output file"))
        val outName = field(a, "name", f.nameWithoutExtension + "_signed.apk")
        column.addView(outName)

        U.builder(a).setTitle("Sign ${f.name}").setView(ScrollView(a).apply { addView(column) })
            .setPositiveButton("Sign") { _, _ ->
                val selectedSchemes = schemes[schemeGroup.checkedRadioButtonId - 100].second
                val useCustom = keyGroup.checkedRadioButtonId == 201
                val name = outName.text.toString().trim().let { if (it.endsWith(".apk")) it else "$it.apk" }
                val out = U.uniq(f.parentFile!!, name)
                if (useCustom) {
                    preferences.edit().putString("ks_path", ksPath.text.toString()).putString("ks_alias", ksAlias.text.toString()).apply()
                }
                runJob(a, "Signing…", { step ->
                    val key = if (useCustom) ApkSign.customKey(ksPath.text.toString().trim(), ksPass.text.toString(), ksAlias.text.toString().trim(), keyPass.text.toString())
                    else ApkSign.builtinKey(a)
                    ApkSign.sign(a, f, out, selectedSchemes[0], selectedSchemes[1], selectedSchemes[2], key, null, step)
                    out
                }, done)
            }.setNegativeButton("Cancel", null).glass()
    }

    fun cloneDialog(a: Activity, f: File, done: (File) -> Unit) {
        val oldPkg = a.packageManager.getPackageArchiveInfo(f.absolutePath, 0)?.packageName
        if (oldPkg == null) {
            fail(a, Exception("Can't read this APK"))
            return
        }
        val column = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(a, 20), dp(a, 8), dp(a, 20), dp(a, 4)) }
        column.addView(TextView(a).apply {
            text = "Original package: $oldPkg\n\nThe clone gets a new package name so both apps can be installed together, then it is signed automatically (v1 + v2 + v3, built-in key)."
            textSize = 14f
            setTextColor(U.text2())
        })
        column.addView(label(a, "New package name"))
        val pkg = field(a, "com.example.app2", "$oldPkg.clone")
        column.addView(pkg)
        column.addView(label(a, "Output file"))
        val outName = field(a, "name", f.nameWithoutExtension + "_clone.apk")
        column.addView(outName)
        U.builder(a).setTitle("Clone ${f.name}").setView(ScrollView(a).apply { addView(column) })
            .setPositiveButton("Clone + sign") { _, _ ->
                val newPkg = pkg.text.toString().trim()
                if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$").matches(newPkg)) {
                    fail(a, Exception("Invalid package name"))
                    return@setPositiveButton
                }
                if (newPkg == oldPkg) {
                    fail(a, Exception("Choose a different package name"))
                    return@setPositiveButton
                }
                val name = outName.text.toString().trim().let { if (it.endsWith(".apk")) it else "$it.apk" }
                val out = U.uniq(f.parentFile!!, name)
                runJob(a, "Cloning…", { step ->
                    ApkSign.sign(a, f, out, true, true, true, ApkSign.builtinKey(a), { AxmlPatcher.clonePackage(it, newPkg) }, step)
                    out
                }, done)
            }.setNegativeButton("Cancel", null).glass()
    }
}
