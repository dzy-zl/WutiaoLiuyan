package com.wutiaoliuyan.app.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine(private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }

    suspend fun recognize(file: File): String {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
    }

    suspend fun recognizeAll(files: List<File>, onProgress: (Int, Int) -> Unit = { _, _ -> }): String {
        val out = StringBuilder()
        files.forEachIndexed { index, file ->
            onProgress(index + 1, files.size)
            val text = runCatching { recognize(file) }.getOrDefault("")
            if (text.isNotBlank()) out.appendLine("[截图 ${index + 1}]").appendLine(text).appendLine()
        }
        return out.toString().trim()
    }
}
