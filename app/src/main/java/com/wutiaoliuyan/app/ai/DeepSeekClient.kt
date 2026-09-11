package com.wutiaoliuyan.app.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.wutiaoliuyan.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class DeepSeekException(val statusCode: Int, message: String) : Exception(message)

class DeepSeekClient {
    private val endpoint = "https://api.deepseek.com/chat/completions"

    fun testConnection(apiKey: String): String {
        if (apiKey.isBlank()) throw DeepSeekException(401, "尚未填写 API Key")
        val conn = (URL("https://api.deepseek.com/user/balance").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
        }
        return conn.useResponse { code, body ->
            if (code !in 200..299) throw asException(code, body)
            val obj = JSONObject(body)
            if (obj.optBoolean("is_available", true)) "连接成功" else "连接成功，但账户余额当前不可用"
        }
    }

    fun analyze(
        apiKey: String,
        model: String,
        files: List<File>,
        text: String,
        instruction: String,
        categories: List<String>,
        previousContext: String = ""
    ): AnalysisResult {
        if (apiKey.isBlank()) throw DeepSeekException(401, "请先在设置中填写 DeepSeek API Key")
        val prompt = buildPrompt(text, instruction, categories, previousContext, files.size)
        val userContent = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
        files.forEachIndexed { index, file ->
            val data = encodeImage(file)
            userContent.put(JSONObject()
                .put("type", "text")
                .put("text", "来源截图索引：$index"))
            userContent.put(JSONObject()
                .put("type", "image_url")
                .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$data")))
        }
        val payload = JSONObject()
            .put("model", model)
            .put("temperature", 0.1)
            .put("max_tokens", 8192)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt()))
                .put(JSONObject().put("role", "user").put("content", userContent)))

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 120000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        return conn.useResponse { code, body ->
            if (code !in 200..299) throw asException(code, body)
            val response = JSONObject(body)
            val content = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
            if (content.isBlank()) throw DeepSeekException(502, "DeepSeek 返回了空结果，请重新识别")
            parseResult(content)
        }
    }

    private fun buildPrompt(text: String, instruction: String, categories: List<String>, previous: String, imageCount: Int): String = buildString {
        appendLine("当前日期：${LocalDate.now()}。请分析用户提供的内容。")
        appendLine("目标：提取与用户本人有关的待办事项，同时生成简短摘要、详细摘要和重点信息。宁可多抓可能任务，不要漏掉明确行动项。")
        appendLine("若同一事项被修改时间或重复提到，只保留一条并采用最新信息；需要时在 detail 中说明变更。")
        appendLine("若只描述他人安排但没有要求用户行动，只写入摘要，不生成用户任务。")
        appendLine("相对日期（明天、下周三等）必须换算为 YYYY-MM-DD；没有明确日期则 date 返回空字符串，由用户确认时补充。")
        appendLine("现有分类只有：${if (categories.isEmpty()) "（暂无）" else categories.joinToString("、")}。只能从这些分类中选择；没有合适分类时留空，不得自行创建分类。")
        appendLine("priority 只能是 HIGH/MEDIUM/LOW；status 默认 INBOX。recurrence 只能是 none/daily/weekly/biweekly/monthly/yearly。")
        appendLine("每条任务 sourceIndexes 返回它真正来源的截图索引（0 到 ${maxOf(0, imageCount - 1)}）；纯文字来源可返回空数组。")
        if (previous.isNotBlank()) appendLine("连续对话的前文上下文：$previous")
        if (text.isNotBlank()) appendLine("用户粘贴/分享的文字：\n$text")
        if (instruction.isNotBlank()) appendLine("用户本次额外要求：$instruction")
        appendLine("必须输出合法 JSON 对象，不要 Markdown。结构：")
        appendLine("{\"shortSummary\":\"\",\"detailedSummary\":\"\",\"highlights\":[\"\"],\"rawText\":\"\",\"tasks\":[{\"title\":\"\",\"detail\":\"\",\"date\":\"YYYY-MM-DD或空\",\"time\":\"HH:mm或空\",\"priority\":\"MEDIUM\",\"category\":\"\",\"tags\":\"\",\"project\":\"\",\"parentTaskTitle\":\"\",\"proposer\":\"\",\"executor\":\"\",\"people\":\"\",\"location\":\"\",\"amount\":\"\",\"recurrence\":\"none\",\"sourceIndexes\":[0],\"status\":\"INBOX\"}]}")
    }

    private fun systemPrompt() = "你是‘五条六眼’的截图信息整理引擎。你需要忠实理解截图内容、聊天上下文和用户指令，输出结构化 JSON。不要杜撰日期、金额、人物或任务。"

    private fun parseResult(raw: String): AnalysisResult {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = JSONObject(cleaned)
        val tasksJson = obj.optJSONArray("tasks") ?: JSONArray()
        val tasks = buildList {
            for (i in 0 until tasksJson.length()) {
                val t = tasksJson.optJSONObject(i) ?: continue
                val priority = runCatching { Priority.valueOf(t.optString("priority", "MEDIUM").uppercase()) }.getOrDefault(Priority.MEDIUM)
                val status = runCatching { TaskStatus.valueOf(t.optString("status", "INBOX").uppercase()) }.getOrDefault(TaskStatus.INBOX)
                val indexes = t.optJSONArray("sourceIndexes")?.let { arr -> (0 until arr.length()).mapNotNull { idx -> arr.optInt(idx, -1).takeIf { it >= 0 } } } ?: emptyList()
                add(AnalyzedTask(
                    title=t.optString("title").trim(), detail=t.optString("detail").trim(), date=validDate(t.optString("date")), time=validTime(t.optString("time")),
                    priority=priority, category=t.optString("category").trim(), tags=t.optString("tags").trim(), project=t.optString("project").trim(),
                    parentTaskTitle=t.optString("parentTaskTitle").trim(), proposer=t.optString("proposer").trim(), executor=t.optString("executor").trim(), people=t.optString("people").trim(),
                    location=t.optString("location").trim(), amount=t.optString("amount").trim(), recurrence=t.optString("recurrence", "none").lowercase(), sourceIndexes=indexes, status=status
                ))
            }
        }.filter { it.title.isNotBlank() }
        val highlights = obj.optJSONArray("highlights")?.let { arr -> (0 until arr.length()).map { arr.optString(it) }.filter(String::isNotBlank) } ?: emptyList()
        return AnalysisResult(obj.optString("shortSummary"), obj.optString("detailedSummary"), highlights, obj.optString("rawText"), tasks)
    }

    private fun validDate(value: String): String = runCatching { if (value.isBlank()) "" else java.time.LocalDate.parse(value).toString() }.getOrDefault("")
    private fun validTime(value: String): String = runCatching { if (value.isBlank()) "" else java.time.LocalTime.parse(value).withSecond(0).withNano(0).toString() }.getOrDefault("")

    private fun encodeImage(file: File): String {
        var bytes = file.readBytes()
        if (bytes.size > 1_400_000) {
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("无法解析图片")
            var current = original
            var quality = 82
            var rounds = 0
            while (bytes.size > 1_400_000 && rounds < 5) {
                val out = ByteArrayOutputStream()
                current.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                if (bytes.size <= 1_400_000) break
                val scaled = Bitmap.createScaledBitmap(current, (current.width * 0.82).toInt().coerceAtLeast(480), (current.height * 0.82).toInt().coerceAtLeast(480), true)
                if (current !== original) current.recycle()
                current = scaled
                quality = (quality - 8).coerceAtLeast(52)
                rounds++
            }
            if (current !== original) current.recycle()
            original.recycle()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun asException(code: Int, body: String): DeepSeekException {
        val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        val human = when (code) {
            401 -> "API Key 无效，请检查设置"
            402 -> "DeepSeek 账户余额不足"
            403 -> "当前 API Key 无权访问该模型或接口"
            429 -> "请求过于频繁，请稍后重试"
            else -> if (detail.isNotBlank()) "DeepSeek 请求失败：$detail" else "DeepSeek 请求失败（HTTP $code）"
        }
        return DeepSeekException(code, human)
    }

    private inline fun <T> HttpURLConnection.useResponse(block: (Int, String) -> T): T = try {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        block(code, body)
    } finally { disconnect() }
}
