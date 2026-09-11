package com.wutiaoliuyan.app.export

import android.content.Context
import androidx.core.content.FileProvider
import com.wutiaoliuyan.app.data.AppRepository
import com.wutiaoliuyan.app.model.Task
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportManager(private val context: Context, private val repo: AppRepository) {
    private val dir = File(context.cacheDir, "exports").apply { mkdirs() }

    fun markdown(tasks: List<Task>): File = File(dir, "WutiaoLiuyan-tasks.md").apply {
        writeText(buildString {
            appendLine("# 五条六眼 · 任务导出")
            tasks.forEach { t ->
                appendLine("- [${if (t.status.name == "DONE") "x" else " "}] **${t.title}**")
                appendLine("  - 日期：${t.date.ifBlank { "未设置" }} ${t.time}")
                if (t.detail.isNotBlank()) appendLine("  - 详情：${t.detail}")
                if (t.project.isNotBlank()) appendLine("  - 项目：${t.project}")
                if (t.category.isNotBlank()) appendLine("  - 分类：${t.category}")
            }
        })
    }

    fun csv(tasks: List<Task>): File = File(dir, "WutiaoLiuyan-tasks.csv").apply {
        val rows = mutableListOf("id,title,detail,date,time,priority,category,project,status")
        fun q(v: Any?) = "\"${v.toString().replace("\"", "\"\"")}\""
        tasks.forEach { t -> rows += listOf(t.id,t.title,t.detail,t.date,t.time,t.priority.name,t.category,t.project,t.status.name).joinToString(",", transform=::q) }
        writeText(rows.joinToString("\n"))
    }

    fun backupZip(): File {
        val out = File(dir, "WutiaoLiuyan-backup-${System.currentTimeMillis()}.zip")
        val tables = repo.db.rawExportTables()
        val json = JSONObject()
        tables.forEach { (table, rows) ->
            val arr = JSONArray()
            rows.forEach { row -> arr.put(JSONObject(row)) }
            json.put(table, arr)
        }
        json.put("settings", JSONObject(repo.settings.publicSnapshot()))
        ZipOutputStream(out.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json")); zip.write(json.toString(2).toByteArray()); zip.closeEntry()
            val paths = tables["source_images"].orEmpty().mapNotNull { it["path"] as? String }.distinct()
            paths.forEachIndexed { index, path ->
                val file = File(path)
                if (file.exists()) {
                    zip.putNextEntry(ZipEntry("images/${index}_${file.name}")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        }
        return out
    }

    fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
