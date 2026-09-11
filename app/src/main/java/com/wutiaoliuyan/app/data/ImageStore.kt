package com.wutiaoliuyan.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ImageStore(private val context: Context) {
    private val sourceDir = File(context.filesDir, "sources").apply { mkdirs() }

    fun importUri(uri: Uri): File {
        val raw = File.createTempFile("incoming_", ".jpg", context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取图片" }
            raw.outputStream().use { input.copyTo(it) }
        }
        return normalizeAndStore(raw)
    }

    fun importCameraFile(raw: File): File = normalizeAndStore(raw)

    private fun normalizeAndStore(raw: File): File {
        val bitmap = BitmapFactory.decodeFile(raw.absolutePath) ?: error("无法解析图片")
        val exif = runCatching { ExifInterface(raw) }.getOrNull()
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            }
        }
        val oriented = if (!matrix.isIdentity) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true) else bitmap
        val scaled = scaleForVision(oriented)
        val target = File(sourceDir, "${System.currentTimeMillis()}_${raw.nameWithoutExtension}.jpg")
        FileOutputStream(target).use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 88, out) }
        if (scaled !== oriented) scaled.recycle()
        if (oriented !== bitmap) oriented.recycle()
        if (!bitmap.isRecycled) bitmap.recycle()
        raw.delete()
        return target
    }

    private fun scaleForVision(bitmap: Bitmap): Bitmap {
        val maxSide = 2200
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun deleteIfExists(path: String) { runCatching { File(path).delete() } }
}
