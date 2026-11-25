package com.example.filemanager

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal binary AndroidManifest.xml package-name patcher. */
object AxmlPatcher {
    private const val STRING_POOL_TYPE = 0x001C0001
    private const val XML_RESOURCE_MAP_TYPE = 0x01800008
    private const val XML_START_ELEMENT_TYPE = 0x00100102

    fun clonePackage(xml: ByteArray, newPackage: String): ByteArray {
        val out = xml.copyOf()
        val buffer = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)

        var stringPoolOffset = -1
        var stringPoolSize = -1
        var stringCount = -1
        var stringsStart = -1
        var utf8 = false

        var offset = 8
        while (offset + 8 <= out.size) {
            val type = buffer.getInt(offset)
            val chunkSize = buffer.getInt(offset + 4)
            if (chunkSize < 8 || offset + chunkSize > out.size) break

            if (type == STRING_POOL_TYPE) {
                stringPoolOffset = offset
                stringPoolSize = chunkSize
                stringCount = buffer.getInt(offset + 8)
                val flags = buffer.getInt(offset + 16)
                utf8 = (flags and 0x100) == 0
                val stringsStartOffset = buffer.getInt(offset + 20)
                stringsStart = offset + stringsStartOffset
            }
            offset += chunkSize
        }

        if (stringPoolOffset < 0 || stringCount < 0 || stringsStart < 0) {
            throw IllegalArgumentException("Invalid binary AndroidManifest.xml")
        }

        val headerEnd = stringPoolOffset + 28
        val indexOffset = stringPoolOffset + 28

        for (i in 0 until stringCount) {
            val pos = indexOffset + i * 4
            if (pos + 4 > out.size) break
            val stringOffset = buffer.getInt(pos)
            val absolute = stringsStart + stringOffset
            val text = readString(out, absolute, utf8) ?: continue
            if (text.matches(Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$"))) {
                writeString(out, absolute, text, newPackage, utf8)
            }
        }

        return out
    }

    private fun readString(data: ByteArray, offset: Int, utf8: Boolean): String? {
        if (offset >= data.size) return null
        return try {
            if (utf8) {
                var p = offset
                val u16len = readUtf8Length(data, p)
                p += if (data[p].toInt() and 0x80 == 0) 1 else 2
                val byteLen = readUtf8Length(data, p)
                p += if (data[p].toInt() and 0x80 == 0) 1 else 2
                String(data, p, byteLen, Charsets.UTF_8)
            } else {
                var p = offset
                val len = data[p].toInt() and 0xff
                p += if (len >= 0x80) 2 else 1
                String(data, p, len, Charsets.UTF_16LE)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeString(data: ByteArray, offset: Int, old: String, newValue: String, utf8: Boolean) {
        if (utf8) {
            val bytes = newValue.toByteArray(Charsets.UTF_8)
            var p = offset
            p += utf8LengthBytes(bytes.size, data, p)
            p += utf8LengthBytes(bytes.size, data, p)
            bytes.copyInto(data, p)
        } else {
            val chars = newValue.toByteArray(Charsets.UTF_16LE)
            val len = newValue.length
            var p = offset
            if (len >= 0x80) {
                data[p++] = (0x80 or (len shr 8)).toByte()
                data[p++] = (len and 0xff).toByte()
            } else data[p++] = len.toByte()
            chars.copyInto(data, p)
        }
    }

    private fun utf8LengthBytes(length: Int, data: ByteArray, offset: Int): Int {
        if (length < 0x80) {
            data[offset] = length.toByte()
            return 1
        }
        data[offset] = (0x80 or (length ushr 8)).toByte()
        data[offset + 1] = (length and 0xff).toByte()
        return 2
    }

    private fun readUtf8Length(data: ByteArray, offset: Int): Int {
        val first = data[offset].toInt() and 0xff
        return if (first and 0x80 == 0) first
        else ((first and 0x7f) shl 8) or (data[offset + 1].toInt() and 0xff)
    }
}
