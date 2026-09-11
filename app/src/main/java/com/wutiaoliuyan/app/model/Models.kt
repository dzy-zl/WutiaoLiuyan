package com.wutiaoliuyan.app.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class TaskStatus { INBOX, TODO, DOING, DONE }
enum class Priority { HIGH, MEDIUM, LOW }

data class Task(
    val id: Long = 0,
    val title: String,
    val detail: String = "",
    val date: String = "",
    val time: String = "",
    val priority: Priority = Priority.MEDIUM,
    val category: String = "",
    val tags: String = "",
    val project: String = "",
    val parentTaskTitle: String = "",
    val proposer: String = "",
    val executor: String = "",
    val people: String = "",
    val location: String = "",
    val amount: String = "",
    val recurrence: String = "none",
    val reminderEnabled: Boolean = true,
    val reminderMinutesBefore: Int = 30,
    val status: TaskStatus = TaskStatus.INBOX,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val deletedAt: Long? = null
) {
    fun dueDateTime(): LocalDateTime? = runCatching {
        if (date.isBlank()) null else {
            val d = LocalDate.parse(date)
            val t = if (time.isBlank()) LocalTime.of(20, 0) else LocalTime.parse(time)
            LocalDateTime.of(d, t)
        }
    }.getOrNull()
}

data class SummaryRecord(
    val id: Long = 0,
    val sessionId: Long,
    val shortSummary: String,
    val detailedSummary: String,
    val highlights: String,
    val rawText: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class SourceImage(
    val id: Long = 0,
    val sessionId: Long,
    val path: String,
    val sha256: String,
    val originalName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class AnalysisSession(
    val id: Long = 0,
    val mode: String,
    val instruction: String,
    val pastedText: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class AnalysisResult(
    val shortSummary: String,
    val detailedSummary: String,
    val highlights: List<String>,
    val rawText: String,
    val tasks: List<AnalyzedTask>
)

data class AnalyzedTask(
    val title: String,
    val detail: String = "",
    val date: String = "",
    val time: String = "",
    val priority: Priority = Priority.MEDIUM,
    val category: String = "",
    val tags: String = "",
    val project: String = "",
    val parentTaskTitle: String = "",
    val proposer: String = "",
    val executor: String = "",
    val people: String = "",
    val location: String = "",
    val amount: String = "",
    val recurrence: String = "none",
    val reminderEnabled: Boolean = true,
    val reminderMinutesBefore: Int = 30,
    val sourceIndexes: List<Int> = emptyList(),
    val status: TaskStatus = TaskStatus.INBOX
)
