package com.bitdroid.messages.mms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageCompressor {
    /** Returns (mime, bytes) that fit in [maxBytes]; GIFs are kept if they already fit. */
    fun compress(context: Context, uri: Uri, maxBytes: Int): Pair<String, ByteArray> {
        val cr = context.contentResolver
        val type = cr.getType(uri) ?: "image/jpeg"
        if (type == "image/gif") {
            val raw = cr.openInputStream(uri)?.use { it.readBytes() }
            if (raw != null && raw.size <= maxBytes) return "image/gif" to raw
        }
        var maxDim = 1920
        while (maxDim >= 320) {
            val bmp = decode(context, uri, maxDim)
            var quality = 88
            while (quality >= 40) {
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= maxBytes) {
                    bmp.recycle()
                    return "image/jpeg" to out.toByteArray()
                }
                quality -= 12
            }
            bmp.recycle()
            maxDim = (maxDim * 0.7).toInt()
        }
        val bmp = decode(context, uri, 320)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 35, out)
        return "image/jpeg" to out.toByteArray()
    }

    private fun decode(context: Context, uri: Uri, maxDim: Int): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1f, maxDim.toFloat() / maxOf(w, h))
            if (scale < 1f) decoder.setTargetSize(maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()))
        }
    }
}
