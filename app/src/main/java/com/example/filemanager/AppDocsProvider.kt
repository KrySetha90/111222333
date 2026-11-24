package com.example.filemanager

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/** Exposes the "E:/app folder" to every other app through the system file picker. */
class AppDocsProvider : DocumentsProvider() {
    private val rootId = "E"
    private fun base(): File = U.eRoot(context!!).canonicalFile
    private fun fileFor(id: String): File {
        if (id == rootId) return base()
        val f = File(base(), id.removePrefix("$rootId:")).canonicalFile
        if (!f.path.startsWith(base().path)) throw FileNotFoundException("outside root")
        return f
    }
    private fun idFor(f: File): String {
        val c = f.canonicalFile
        return if (c.path == base().path) rootId else "$rootId:" + c.relativeTo(base()).path
    }
    private val rootCols = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_TITLE, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_ICON, Root.COLUMN_AVAILABLE_BYTES)
    private val docCols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val c = MatrixCursor(projection ?: rootCols)
        c.newRow().add(Root.COLUMN_ROOT_ID, rootId)
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
            .add(Root.COLUMN_TITLE, "File Manager (E: app folder)")
            .add(Root.COLUMN_DOCUMENT_ID, rootId)
            .add(Root.COLUMN_ICON, android.R.drawable.ic_menu_save)
            .add(Root.COLUMN_AVAILABLE_BYTES, base().freeSpace)
        return c
    }
    private fun addRow(c: MatrixCursor, f: File) {
        var flags = Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        flags = flags or if (f.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE
        c.newRow().add(Document.COLUMN_DOCUMENT_ID, idFor(f))
            .add(Document.COLUMN_DISPLAY_NAME, if (f.canonicalPath == base().path) "E: app folder" else f.name)
            .add(Document.COLUMN_MIME_TYPE, if (f.isDirectory) Document.MIME_TYPE_DIR else U.mime(f.name))
            .add(Document.COLUMN_SIZE, f.length())
            .add(Document.COLUMN_LAST_MODIFIED, f.lastModified())
            .add(Document.COLUMN_FLAGS, flags)
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val c = MatrixCursor(projection ?: docCols); addRow(c, fileFor(documentId)); return c
    }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val c = MatrixCursor(projection ?: docCols)
        fileFor(parentDocumentId).listFiles()?.forEach { addRow(c, it) }
        return c
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileFor(documentId), ParcelFileDescriptor.parseMode(mode))
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val f = U.uniq(fileFor(parentDocumentId), displayName)
        if (mimeType == Document.MIME_TYPE_DIR) f.mkdirs() else f.createNewFile()
        return idFor(f)
    }
    override fun deleteDocument(documentId: String) { fileFor(documentId).deleteRecursively() }
    override fun renameDocument(documentId: String, displayName: String): String? {
        val f = fileFor(documentId); val t = File(f.parentFile, displayName)
        return if (f.renameTo(t)) idFor(t) else null
    }
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        fileFor(documentId).path.startsWith(fileFor(parentDocumentId).path)
}
