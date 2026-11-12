package com.bitdroid.messages.mms

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Serves PDU files to the platform MmsService (com.android.phone) for
 * sendMultimediaMessage (read) and downloadMultimediaMessage (write).
 */
class MmsFileProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw IllegalStateException()
        val name = uri.lastPathSegment ?: throw IllegalArgumentException("no file")
        if (name.contains('/') || name.contains("..")) throw IllegalArgumentException("bad name")
        val file = File(dir(ctx), name)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String = "application/vnd.wap.mms-message"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    companion object {
        private val PHONE_PACKAGES = listOf("com.android.phone")

        fun dir(context: Context): File = File(context.cacheDir, "mms").apply { mkdirs() }

        fun file(context: Context, name: String) = File(dir(context), name)

        fun uri(context: Context, name: String, write: Boolean): Uri {
            val uri = Uri.parse("content://${context.packageName}.mmsfiles/$name")
            val flags = if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            else Intent.FLAG_GRANT_READ_URI_PERMISSION
            for (pkg in PHONE_PACKAGES) {
                try {
                    context.grantUriPermission(pkg, uri, flags)
                } catch (_: Exception) {
                }
            }
            return uri
        }
    }
}
