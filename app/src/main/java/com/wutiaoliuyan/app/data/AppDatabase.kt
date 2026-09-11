package com.wutiaoliuyan.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.wutiaoliuyan.app.model.*
import java.time.LocalDate

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "wutiao_liuyan.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE sessions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                mode TEXT NOT NULL,
                instruction TEXT NOT NULL DEFAULT '',
                pasted_text TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE summaries(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                short_summary TEXT NOT NULL,
                detailed_summary TEXT NOT NULL,
                highlights TEXT NOT NULL,
                raw_text TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE tasks(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                detail TEXT NOT NULL DEFAULT '',
                due_date TEXT NOT NULL DEFAULT '',
                due_time TEXT NOT NULL DEFAULT '',
                priority TEXT NOT NULL DEFAULT 'MEDIUM',
                category TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                project TEXT NOT NULL DEFAULT '',
                parent_task_title TEXT NOT NULL DEFAULT '',
                proposer TEXT NOT NULL DEFAULT '',
                executor TEXT NOT NULL DEFAULT '',
                people TEXT NOT NULL DEFAULT '',
                location TEXT NOT NULL DEFAULT '',
                amount TEXT NOT NULL DEFAULT '',
                recurrence TEXT NOT NULL DEFAULT 'none',
                reminder_enabled INTEGER NOT NULL DEFAULT 1,
                reminder_minutes INTEGER NOT NULL DEFAULT 30,
                status TEXT NOT NULL DEFAULT 'INBOX',
                created_at INTEGER NOT NULL,
                completed_at INTEGER,
                deleted_at INTEGER
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE source_images(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                path TEXT NOT NULL,
                sha256 TEXT NOT NULL,
                original_name TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                UNIQUE(sha256, session_id)
            )
        """.trimIndent())
        db.execSQL("CREATE TABLE task_sources(task_id INTEGER NOT NULL, source_id INTEGER NOT NULL, PRIMARY KEY(task_id, source_id))")
        db.execSQL("CREATE TABLE projects(name TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE categories(name TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_tasks_status ON tasks(status)")
        db.execSQL("CREATE INDEX idx_tasks_date ON tasks(due_date)")
        db.execSQL("CREATE INDEX idx_tasks_deleted ON tasks(deleted_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            safeExec(db, "ALTER TABLE tasks ADD COLUMN parent_task_title TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            safeExec(db, "CREATE TABLE IF NOT EXISTS projects(name TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
            safeExec(db, "CREATE TABLE IF NOT EXISTS categories(name TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
            safeExec(db, "INSERT OR IGNORE INTO projects(name, created_at) SELECT DISTINCT project, strftime('%s','now')*1000 FROM tasks WHERE project <> ''")
        }
        if (oldVersion < 4) {
            safeExec(db, "ALTER TABLE tasks ADD COLUMN reminder_enabled INTEGER NOT NULL DEFAULT 1")
        }
    }

    private fun safeExec(db: SQLiteDatabase, sql: String) { runCatching { db.execSQL(sql) } }

    fun insertSession(session: AnalysisSession): Long = writableDatabase.insertOrThrow("sessions", null, ContentValues().apply {
        put("mode", session.mode); put("instruction", session.instruction); put("pasted_text", session.pastedText); put("created_at", session.createdAt)
    })

    fun insertSummary(summary: SummaryRecord): Long = writableDatabase.insertOrThrow("summaries", null, ContentValues().apply {
        put("session_id", summary.sessionId); put("short_summary", summary.shortSummary); put("detailed_summary", summary.detailedSummary)
        put("highlights", summary.highlights); put("raw_text", summary.rawText); put("created_at", summary.createdAt)
    })

    fun insertSource(source: SourceImage): Long = writableDatabase.insertWithOnConflict("source_images", null, ContentValues().apply {
        put("session_id", source.sessionId); put("path", source.path); put("sha256", source.sha256); put("original_name", source.originalName); put("created_at", source.createdAt)
    }, SQLiteDatabase.CONFLICT_IGNORE).let { id ->
        if (id != -1L) id else readableDatabase.rawQuery("SELECT id FROM source_images WHERE session_id=? AND sha256=?", arrayOf(source.sessionId.toString(), source.sha256)).use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
    }

    fun insertTask(task: Task, sourceIds: List<Long> = emptyList()): Long {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val id = db.insertOrThrow("tasks", null, taskValues(task))
            sourceIds.distinct().filter { it > 0 }.forEach { sid ->
                db.insertWithOnConflict("task_sources", null, ContentValues().apply { put("task_id", id); put("source_id", sid) }, SQLiteDatabase.CONFLICT_IGNORE)
            }
            if (task.project.isNotBlank()) upsertProject(task.project)
            if (task.category.isNotBlank()) upsertCategory(task.category)
            db.setTransactionSuccessful(); id
        } finally { db.endTransaction() }
    }

    fun updateTask(task: Task) {
        writableDatabase.update("tasks", taskValues(task), "id=?", arrayOf(task.id.toString()))
        if (task.project.isNotBlank()) upsertProject(task.project)
        if (task.category.isNotBlank()) upsertCategory(task.category)
    }

    private fun taskValues(task: Task) = ContentValues().apply {
        put("title", task.title); put("detail", task.detail); put("due_date", task.date); put("due_time", task.time)
        put("priority", task.priority.name); put("category", task.category); put("tags", task.tags); put("project", task.project)
        put("parent_task_title", task.parentTaskTitle); put("proposer", task.proposer); put("executor", task.executor); put("people", task.people)
        put("location", task.location); put("amount", task.amount); put("recurrence", task.recurrence); put("reminder_enabled", if (task.reminderEnabled) 1 else 0)
        put("reminder_minutes", task.reminderMinutesBefore); put("status", task.status.name); put("created_at", task.createdAt)
        if (task.completedAt == null) putNull("completed_at") else put("completed_at", task.completedAt)
        if (task.deletedAt == null) putNull("deleted_at") else put("deleted_at", task.deletedAt)
    }

    fun getTask(id: Long): Task? = readableDatabase.rawQuery("SELECT * FROM tasks WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToFirst()) c.toTask() else null }

    fun listTasks(view: String = "all", query: String = "", project: String = ""): List<Task> {
        val clauses = mutableListOf("deleted_at IS NULL")
        val args = mutableListOf<String>()
        when (view) {
            "inbox" -> clauses += "status='INBOX'"
            "doing" -> clauses += "status='DOING'"
            "done" -> clauses += "status='DONE'"
            "today" -> { clauses += "due_date=?"; args += LocalDate.now().toString(); clauses += "status<>'DONE'" }
            "planned" -> { clauses += "due_date>?"; args += LocalDate.now().toString(); clauses += "status<>'DONE'" }
            else -> Unit
        }
        if (query.isNotBlank()) {
            clauses += "(title LIKE ? OR detail LIKE ? OR tags LIKE ? OR people LIKE ? OR category LIKE ? OR id IN (SELECT ts.task_id FROM task_sources ts JOIN source_images si ON si.id=ts.source_id JOIN summaries sm ON sm.session_id=si.session_id WHERE sm.raw_text LIKE ? OR sm.detailed_summary LIKE ?))"
            repeat(7) { args += "%$query%" }
        }
        if (project.isNotBlank()) { clauses += "project=?"; args += project }
        val sql = "SELECT * FROM tasks WHERE ${clauses.joinToString(" AND ")} ORDER BY CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, due_date='', due_date, due_time, created_at DESC"
        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { c -> buildList { while (c.moveToNext()) add(c.toTask()) } }
    }

    fun todayCount(): Int = scalarInt("SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND status<>'DONE' AND due_date=?", arrayOf(LocalDate.now().toString()))
    fun inboxCount(): Int = scalarInt("SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND status='INBOX'", emptyArray())

    fun markStatus(id: Long, status: TaskStatus): Task? {
        val task = getTask(id) ?: return null
        if (task.status == status) return task
        val completedAt = if (status == TaskStatus.DONE) System.currentTimeMillis() else null
        updateTask(task.copy(status = status, completedAt = completedAt))
        if (status == TaskStatus.DONE) createNextRecurring(task)
        return getTask(id)
    }

    private fun createNextRecurring(task: Task) {
        if (task.recurrence == "none" || task.date.isBlank()) return
        val date = runCatching { LocalDate.parse(task.date) }.getOrNull() ?: return
        val next = when (task.recurrence.lowercase()) {
            "daily", "每天" -> date.plusDays(1)
            "weekly", "每周" -> date.plusWeeks(1)
            "biweekly", "每两周" -> date.plusWeeks(2)
            "monthly", "每月" -> date.plusMonths(1)
            "yearly", "每年" -> date.plusYears(1)
            else -> return
        }
        val exists = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND title=? AND due_date=? AND recurrence=?",
            arrayOf(task.title, next.toString(), task.recurrence)
        ).use { c -> c.moveToFirst(); c.getInt(0) > 0 }
        if (!exists) insertTask(task.copy(id = 0, date = next.toString(), status = TaskStatus.TODO, completedAt = null, createdAt = System.currentTimeMillis()))
    }

    fun listTrashTasks(): List<Task> = readableDatabase.rawQuery("SELECT * FROM tasks WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC", emptyArray()).use { c -> buildList { while(c.moveToNext()) add(c.toTask()) } }
    fun permanentlyDeleteTask(id: Long): List<String> {
        val paths = removeUnreferencedSourcesForTask(id)
        writableDatabase.delete("task_sources", "task_id=?", arrayOf(id.toString()))
        writableDatabase.delete("tasks", "id=?", arrayOf(id.toString()))
        return paths
    }

    fun softDeleteTask(id: Long) { writableDatabase.execSQL("UPDATE tasks SET deleted_at=? WHERE id=?", arrayOf(System.currentTimeMillis(), id)) }
    fun restoreTask(id: Long) { writableDatabase.execSQL("UPDATE tasks SET deleted_at=NULL WHERE id=?", arrayOf(id)) }
    fun emptyOldTrash(days: Int = 30) { writableDatabase.execSQL("DELETE FROM tasks WHERE deleted_at IS NOT NULL AND deleted_at < ?", arrayOf(System.currentTimeMillis() - days * 86400000L)) }

    fun listSummaries(query: String = ""): List<SummaryRecord> {
        val sql = if (query.isBlank()) "SELECT * FROM summaries ORDER BY created_at DESC" else "SELECT * FROM summaries WHERE short_summary LIKE ? OR detailed_summary LIKE ? OR highlights LIKE ? OR raw_text LIKE ? ORDER BY created_at DESC"
        val args = if (query.isBlank()) emptyArray() else arrayOf("%$query%", "%$query%", "%$query%", "%$query%")
        return readableDatabase.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(c.toSummary()) } }
    }

    fun removeUnreferencedSourcesForTask(taskId: Long): List<String> {
        val sources = listSourceImagesForTask(taskId)
        val db = writableDatabase
        val removed = mutableListOf<String>()
        db.beginTransaction()
        try {
            sources.forEach { source ->
                val otherActive = db.rawQuery("SELECT COUNT(*) FROM task_sources ts JOIN tasks t ON t.id=ts.task_id WHERE ts.source_id=? AND ts.task_id<>? AND t.deleted_at IS NULL", arrayOf(source.id.toString(), taskId.toString())).use { c -> c.moveToFirst(); c.getInt(0) }
                if (otherActive == 0) {
                    db.delete("task_sources", "source_id=?", arrayOf(source.id.toString()))
                    db.delete("source_images", "id=?", arrayOf(source.id.toString()))
                    removed += source.path
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return removed
    }

    fun listSourceImagesForTask(taskId: Long): List<SourceImage> = readableDatabase.rawQuery(
        "SELECT s.* FROM source_images s JOIN task_sources ts ON ts.source_id=s.id WHERE ts.task_id=? ORDER BY s.id", arrayOf(taskId.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(c.toSource()) } }

    fun allPendingReminderTasks(): List<Task> = readableDatabase.rawQuery("SELECT * FROM tasks WHERE deleted_at IS NULL AND status<>'DONE' AND reminder_enabled=1 AND due_date<>''", emptyArray()).use { c -> buildList { while (c.moveToNext()) add(c.toTask()) } }

    fun upsertProject(name: String) { if (name.isNotBlank()) writableDatabase.insertWithOnConflict("projects", null, ContentValues().apply { put("name", name.trim()); put("created_at", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_IGNORE) }
    fun renameProject(old: String, new: String) { if (new.isBlank()) return; val db=writableDatabase; db.beginTransaction(); try { upsertProject(new); db.execSQL("UPDATE tasks SET project=? WHERE project=?", arrayOf(new.trim(), old)); db.delete("projects", "name=?", arrayOf(old)); db.setTransactionSuccessful() } finally { db.endTransaction() } }
    fun deleteProject(name: String) { val db=writableDatabase; db.beginTransaction(); try { db.execSQL("UPDATE tasks SET project='' WHERE project=?", arrayOf(name)); db.delete("projects", "name=?", arrayOf(name)); db.setTransactionSuccessful() } finally { db.endTransaction() } }
    fun listProjects(): List<String> = stringList("SELECT name FROM projects ORDER BY name")

    fun upsertCategory(name: String) { if (name.isNotBlank()) writableDatabase.insertWithOnConflict("categories", null, ContentValues().apply { put("name", name.trim()); put("created_at", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_IGNORE) }
    fun deleteCategory(name: String) { writableDatabase.delete("categories", "name=?", arrayOf(name)) }
    fun listCategories(): List<String> = stringList("SELECT name FROM categories ORDER BY name")

    fun sourceBySha(sessionId: Long, sha: String): SourceImage? = readableDatabase.rawQuery("SELECT * FROM source_images WHERE session_id=? AND sha256=?", arrayOf(sessionId.toString(), sha)).use { c -> if(c.moveToFirst()) c.toSource() else null }

    fun rawExportTables(): Map<String, List<Map<String, Any?>>> = listOf("sessions","summaries","tasks","source_images","task_sources","projects","categories").associateWith { table ->
        readableDatabase.rawQuery("SELECT * FROM $table", null).use { c ->
            buildList {
                while (c.moveToNext()) add((0 until c.columnCount).associate { i -> c.getColumnName(i) to if (c.isNull(i)) null else when(c.getType(i)) { Cursor.FIELD_TYPE_INTEGER -> c.getLong(i); Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i); else -> c.getString(i) } })
            }
        }
    }

    private fun scalarInt(sql: String, args: Array<String>): Int = readableDatabase.rawQuery(sql, args).use { c -> c.moveToFirst(); c.getInt(0) }
    private fun stringList(sql: String): List<String> = readableDatabase.rawQuery(sql, null).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }

    private fun Cursor.toTask() = Task(
        id = getLong(getColumnIndexOrThrow("id")), title = getString(getColumnIndexOrThrow("title")), detail = getString(getColumnIndexOrThrow("detail")),
        date = getString(getColumnIndexOrThrow("due_date")), time = getString(getColumnIndexOrThrow("due_time")),
        priority = runCatching { Priority.valueOf(getString(getColumnIndexOrThrow("priority"))) }.getOrDefault(Priority.MEDIUM), category = getString(getColumnIndexOrThrow("category")),
        tags = getString(getColumnIndexOrThrow("tags")), project = getString(getColumnIndexOrThrow("project")), parentTaskTitle = getString(getColumnIndexOrThrow("parent_task_title")),
        proposer = getString(getColumnIndexOrThrow("proposer")), executor = getString(getColumnIndexOrThrow("executor")), people = getString(getColumnIndexOrThrow("people")),
        location = getString(getColumnIndexOrThrow("location")), amount = getString(getColumnIndexOrThrow("amount")), recurrence = getString(getColumnIndexOrThrow("recurrence")),
        reminderEnabled = getInt(getColumnIndexOrThrow("reminder_enabled")) != 0, reminderMinutesBefore = getInt(getColumnIndexOrThrow("reminder_minutes")),
        status = runCatching { TaskStatus.valueOf(getString(getColumnIndexOrThrow("status"))) }.getOrDefault(TaskStatus.INBOX), createdAt = getLong(getColumnIndexOrThrow("created_at")),
        completedAt = getColumnIndexOrThrow("completed_at").let { if (isNull(it)) null else getLong(it) }, deletedAt = getColumnIndexOrThrow("deleted_at").let { if (isNull(it)) null else getLong(it) }
    )

    private fun Cursor.toSummary() = SummaryRecord(
        id=getLong(getColumnIndexOrThrow("id")), sessionId=getLong(getColumnIndexOrThrow("session_id")), shortSummary=getString(getColumnIndexOrThrow("short_summary")),
        detailedSummary=getString(getColumnIndexOrThrow("detailed_summary")), highlights=getString(getColumnIndexOrThrow("highlights")), rawText=getString(getColumnIndexOrThrow("raw_text")), createdAt=getLong(getColumnIndexOrThrow("created_at"))
    )

    private fun Cursor.toSource() = SourceImage(
        id=getLong(getColumnIndexOrThrow("id")), sessionId=getLong(getColumnIndexOrThrow("session_id")), path=getString(getColumnIndexOrThrow("path")), sha256=getString(getColumnIndexOrThrow("sha256")), originalName=getString(getColumnIndexOrThrow("original_name")), createdAt=getLong(getColumnIndexOrThrow("created_at"))
    )
}
