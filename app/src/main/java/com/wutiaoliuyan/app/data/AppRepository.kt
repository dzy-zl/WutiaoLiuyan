package com.wutiaoliuyan.app.data

import android.content.Context
import com.wutiaoliuyan.app.model.*
import java.io.File

class AppRepository(context: Context) {
    val db = AppDatabase(context)
    val settings = SettingsStore(context)
    val secrets = SecretStore(context)
    val images = ImageStore(context)

    fun saveAnalysis(mode: String, instruction: String, pastedText: String, files: List<File>, result: AnalysisResult): Pair<Long, List<Long>> {
        val sessionId = db.insertSession(AnalysisSession(mode = mode, instruction = instruction, pastedText = pastedText))
        val sourceIds = files.map { file ->
            db.insertSource(SourceImage(sessionId = sessionId, path = file.absolutePath, sha256 = images.sha256(file), originalName = file.name))
        }
        db.insertSummary(SummaryRecord(sessionId = sessionId, shortSummary = result.shortSummary, detailedSummary = result.detailedSummary, highlights = result.highlights.joinToString("\n"), rawText = result.rawText))
        return sessionId to sourceIds
    }
}
