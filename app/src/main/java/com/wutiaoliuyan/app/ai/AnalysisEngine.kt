package com.wutiaoliuyan.app.ai

import android.content.Context
import com.wutiaoliuyan.app.model.AnalysisResult
import com.wutiaoliuyan.app.model.AnalyzedTask
import java.io.File

class AnalysisEngine(context: Context) {
    private val client = DeepSeekClient()
    private val ocr = OcrEngine(context)

    suspend fun analyze(
        apiKey: String,
        model: String,
        mode: String,
        files: List<File>,
        text: String,
        instruction: String,
        categories: List<String>,
        onProgress: (String) -> Unit
    ): AnalysisResult {
        return if (mode == "independent") analyzeIndependent(apiKey, model, files, text, instruction, categories, onProgress)
        else analyzeContinuous(apiKey, model, files, text, instruction, categories, onProgress)
    }

    private suspend fun analyzeIndependent(apiKey: String, model: String, files: List<File>, text: String, instruction: String, categories: List<String>, onProgress: (String) -> Unit): AnalysisResult {
        val results = mutableListOf<AnalysisResult>()
        if (text.isNotBlank()) {
            onProgress("正在分析文字")
            results += callWithFallback(apiKey, model, emptyList(), text, instruction, categories, onProgress)
        }
        files.forEachIndexed { index, file ->
            onProgress("正在分析第 ${index + 1} / ${files.size} 张")
            val result = callWithFallback(apiKey, model, listOf(file), "", instruction, categories, onProgress)
            results += result.copy(tasks = result.tasks.map { it.copy(sourceIndexes = listOf(index)) })
        }
        return merge(results)
    }

    private suspend fun analyzeContinuous(apiKey: String, model: String, files: List<File>, text: String, instruction: String, categories: List<String>, onProgress: (String) -> Unit): AnalysisResult {
        if (files.isEmpty()) return callWithFallback(apiKey, model, emptyList(), text, instruction, categories, onProgress)
        val chunks = files.chunked(4)
        val results = mutableListOf<AnalysisResult>()
        var context = ""
        var baseIndex = 0
        chunks.forEachIndexed { batch, chunk ->
            onProgress("理解连续内容：第 ${baseIndex + 1}–${baseIndex + chunk.size} / ${files.size} 张")
            val batchText = if (batch == 0) text else ""
            val result = callWithFallback(apiKey, model, chunk, batchText, instruction, categories, onProgress, context)
            val remapped = result.tasks.map { t -> t.copy(sourceIndexes = t.sourceIndexes.map { it + baseIndex }.filter { it in files.indices }) }
            results += result.copy(tasks = remapped)
            context = "上一批摘要：${result.shortSummary}\n上一批任务：${result.tasks.joinToString("；") { it.title }}"
            baseIndex += chunk.size
        }
        return merge(results)
    }

    private suspend fun callWithFallback(apiKey: String, model: String, files: List<File>, text: String, instruction: String, categories: List<String>, onProgress: (String) -> Unit, previous: String = ""): AnalysisResult {
        var last: Throwable? = null
        repeat(2) { attempt ->
            try {
                if (attempt == 1) onProgress("DeepSeek 暂时失败，正在重试")
                return client.analyze(apiKey, model, files, text, instruction, categories, previous)
            } catch (e: DeepSeekException) {
                if (e.statusCode in setOf(401, 402, 403)) throw e
                last = e
            } catch (e: Throwable) { last = e }
        }
        if (files.isEmpty()) throw last ?: IllegalStateException("分析失败")
        onProgress("正在使用本地 OCR 兜底")
        val ocrText = ocr.recognizeAll(files) { done, total -> onProgress("本地 OCR：$done / $total") }
        if (ocrText.isBlank()) throw last ?: IllegalStateException("DeepSeek 与 OCR 均未取得结果")
        return try {
            onProgress("尝试用 OCR 文字重新整理")
            client.analyze(apiKey, model, emptyList(), listOf(text, ocrText).filter(String::isNotBlank).joinToString("\n\n"), instruction, categories, previous)
                .copy(rawText = ocrText)
        } catch (_: Throwable) {
            AnalysisResult("已通过本地 OCR 提取文字", "DeepSeek 暂时不可用，已保留截图文字。你可以稍后重新分析。", emptyList(), ocrText, emptyList())
        }
    }

    private fun merge(results: List<AnalysisResult>): AnalysisResult {
        val tasks = LinkedHashMap<String, AnalyzedTask>()
        results.flatMap { it.tasks }.forEach { task ->
            val key = "${task.title.trim().lowercase()}|${task.date}|${task.time}"
            val old = tasks[key]
            tasks[key] = if (old == null) task else old.copy(
                detail = listOf(old.detail, task.detail).filter(String::isNotBlank).distinct().joinToString("；"),
                sourceIndexes = (old.sourceIndexes + task.sourceIndexes).distinct().sorted()
            )
        }
        return AnalysisResult(
            shortSummary = results.map { it.shortSummary }.filter(String::isNotBlank).joinToString(" / "),
            detailedSummary = results.map { it.detailedSummary }.filter(String::isNotBlank).joinToString("\n\n"),
            highlights = results.flatMap { it.highlights }.distinct(),
            rawText = results.map { it.rawText }.filter(String::isNotBlank).joinToString("\n\n"),
            tasks = tasks.values.toList()
        )
    }
}
