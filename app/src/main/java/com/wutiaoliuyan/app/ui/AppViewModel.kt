package com.wutiaoliuyan.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wutiaoliuyan.app.WutiaoLiuyanApp
import com.wutiaoliuyan.app.ai.AnalysisEngine
import com.wutiaoliuyan.app.ai.DeepSeekClient
import com.wutiaoliuyan.app.export.ExportManager
import com.wutiaoliuyan.app.model.*
import com.wutiaoliuyan.app.notify.ReminderScheduler
import com.wutiaoliuyan.app.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val TAB_HOME = 0
const val TAB_TASKS = 1
const val TAB_SCAN = 2
const val TAB_SUMMARY = 3
const val TAB_SETTINGS = 4

data class ScanState(
    val files: List<File> = emptyList(),
    val text: String = "",
    val instruction: String = "",
    val mode: String = "continuous",
    val running: Boolean = false,
    val progress: String = "",
    val result: AnalysisResult? = null,
    val drafts: List<AnalyzedTask> = emptyList()
)

data class AppUiState(
    val tab: Int = TAB_HOME,
    val tasks: List<Task> = emptyList(),
    val summaries: List<SummaryRecord> = emptyList(),
    val trashTasks: List<Task> = emptyList(),
    val projects: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val todayCount: Int = 0,
    val inboxCount: Int = 0,
    val taskView: String = "all",
    val taskQuery: String = "",
    val taskProject: String = "",
    val summaryQuery: String = "",
    val scan: ScanState = ScanState(),
    val selectedTask: Task? = null,
    val selectedTaskSources: List<SourceImage> = emptyList(),
    val message: String? = null,
    val snackbar: String? = null,
    val lastDeletedId: Long? = null,
    val requestNotificationPermission: Boolean = false,
    val theme: String = "system",
    val model: String = "deepseek-flash",
    val imageRetention: String = "30d",
    val aiPreference: String = ""
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as WutiaoLiuyanApp).repository
    private val analyzer = AnalysisEngine(application)
    private val scheduler = ReminderScheduler(application)
    private val exporter = ExportManager(application, repo)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init { refreshAll() }

    fun setTab(tab: Int) { _state.value = _state.value.copy(tab = tab) }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }
    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
    fun notificationPermissionHandled() { _state.value = _state.value.copy(requestNotificationPermission = false) }

    fun refreshAll() = viewModelScope.launch(Dispatchers.IO) {
        cleanupImages()
        val s = _state.value
        val next = s.copy(
            tasks = repo.db.listTasks(s.taskView, s.taskQuery, s.taskProject),
            summaries = repo.db.listSummaries(s.summaryQuery),
            trashTasks = repo.db.listTrashTasks(),
            projects = repo.db.listProjects(), categories = repo.db.listCategories(),
            todayCount = repo.db.todayCount(), inboxCount = repo.db.inboxCount(),
            theme = repo.settings.theme, model = repo.settings.model, imageRetention = repo.settings.imageRetention, aiPreference = repo.settings.aiPreference
        )
        _state.value = next
        withContext(Dispatchers.Main) { WidgetUpdater.updateAll(getApplication()) }
    }

    fun importUris(uris: List<Uri>) = viewModelScope.launch(Dispatchers.IO) {
        val added = mutableListOf<File>()
        val existingHashes = _state.value.scan.files.associateBy { repo.images.sha256(it) }
        uris.forEach { uri ->
            runCatching { repo.images.importUri(uri) }.onSuccess { file ->
                val sha = repo.images.sha256(file)
                if (existingHashes.containsKey(sha) || added.any { repo.images.sha256(it) == sha }) file.delete() else added += file
            }
        }
        _state.value = _state.value.copy(tab = TAB_SCAN, scan = _state.value.scan.copy(files = _state.value.scan.files + added))
    }

    fun importCamera(file: File) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { repo.images.importCameraFile(file) }
            .onSuccess { normalized -> _state.value = _state.value.copy(tab = TAB_SCAN, scan = _state.value.scan.copy(files = _state.value.scan.files + normalized)) }
            .onFailure { _state.value = _state.value.copy(message = it.message ?: "照片导入失败") }
    }

    fun acceptShared(uris: List<Uri>, text: String) {
        if (text.isNotBlank()) _state.value = _state.value.copy(tab = TAB_SCAN, scan = _state.value.scan.copy(text = text))
        if (uris.isNotEmpty()) importUris(uris) else setTab(TAB_SCAN)
    }

    fun removePendingFile(file: File) {
        val scan = _state.value.scan
        if (scan.result == null) file.delete()
        _state.value = _state.value.copy(scan = scan.copy(files = scan.files - file))
    }
    fun clearPending() {
        _state.value.scan.files.forEach { it.delete() }
        _state.value = _state.value.copy(scan = ScanState())
    }
    fun updateScanText(v: String) { _state.value = _state.value.copy(scan = _state.value.scan.copy(text = v)) }
    fun updateScanInstruction(v: String) { _state.value = _state.value.copy(scan = _state.value.scan.copy(instruction = v)) }
    fun setScanMode(v: String) { _state.value = _state.value.copy(scan = _state.value.scan.copy(mode = v)) }

    fun analyze() {
        val scan = _state.value.scan
        if (scan.files.isEmpty() && scan.text.isBlank()) { _state.value = _state.value.copy(message = "请先添加截图或文字"); return }
        val apiKey = repo.secrets.getApiKey()
        if (apiKey.isBlank()) { _state.value = _state.value.copy(tab = TAB_SETTINGS, message = "请先填写 DeepSeek API Key"); return }
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(scan = scan.copy(running = true, progress = "正在准备内容", result = null, drafts = emptyList()))
            runCatching {
                analyzer.analyze(apiKey, repo.settings.model, scan.mode, scan.files, scan.text, listOf(repo.settings.aiPreference, scan.instruction).filter(String::isNotBlank).joinToString("\n"), repo.db.listCategories()) { p ->
                    _state.value = _state.value.copy(scan = _state.value.scan.copy(progress = p))
                }
            }.onSuccess { result ->
                _state.value = _state.value.copy(scan = _state.value.scan.copy(running = false, progress = "完成", result = result, drafts = result.tasks))
            }.onFailure { e ->
                _state.value = _state.value.copy(scan = _state.value.scan.copy(running = false), message = e.message ?: "识别失败")
            }
        }
    }

    fun updateDraft(index: Int, value: AnalyzedTask) {
        val list = _state.value.scan.drafts.toMutableList(); if (index in list.indices) list[index] = value
        _state.value = _state.value.copy(scan = _state.value.scan.copy(drafts = list))
    }
    fun deleteDraft(index: Int) {
        val list = _state.value.scan.drafts.toMutableList(); if (index in list.indices) list.removeAt(index)
        _state.value = _state.value.copy(scan = _state.value.scan.copy(drafts = list))
    }
    fun addDraft() { _state.value = _state.value.copy(scan = _state.value.scan.copy(drafts = _state.value.scan.drafts + AnalyzedTask(title = ""))) }

    fun saveAnalysis() {
        val scan = _state.value.scan
        val result = scan.result ?: return
        val missing = scan.drafts.indexOfFirst { it.title.isNotBlank() && it.date.isBlank() }
        if (missing >= 0) { _state.value = _state.value.copy(message = "第 ${missing + 1} 条任务缺少日期，请先补充"); return }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val (_, sourceIds) = repo.saveAnalysis(scan.mode, scan.instruction, scan.text, scan.files, result.copy(tasks = scan.drafts))
                var needPermission = false
                scan.drafts.filter { it.title.isNotBlank() }.forEach { d ->
                    val task = Task(
                        title=d.title, detail=d.detail, date=d.date, time=d.time, priority=d.priority, category=d.category,
                        tags=d.tags, project=d.project, parentTaskTitle=d.parentTaskTitle, proposer=d.proposer, executor=d.executor,
                        people=d.people, location=d.location, amount=d.amount, recurrence=d.recurrence, reminderEnabled=d.reminderEnabled,
                        reminderMinutesBefore=d.reminderMinutesBefore, status=d.status
                    )
                    val ids = if (d.sourceIndexes.isEmpty()) emptyList() else d.sourceIndexes.mapNotNull { sourceIds.getOrNull(it) }
                    val id = repo.db.insertTask(task, ids)
                    val saved = task.copy(id = id)
                    scheduler.schedule(saved)
                    if (saved.reminderEnabled) needPermission = true
                }
                if (repo.settings.imageRetention == "after") scan.files.forEach { it.delete() }
                _state.value = _state.value.copy(tab = TAB_HOME, scan = ScanState(), message = "已保存识别结果", requestNotificationPermission = needPermission)
                refreshAll()
            }.onFailure { _state.value = _state.value.copy(message = it.message ?: "保存失败") }
        }
    }

    fun setTaskFilter(view: String) { _state.value = _state.value.copy(taskView = view); refreshAll() }
    fun setTaskQuery(query: String) { _state.value = _state.value.copy(taskQuery = query); refreshAll() }
    fun setTaskProject(project: String) { _state.value = _state.value.copy(taskProject = project); refreshAll() }
    fun setSummaryQuery(query: String) { _state.value = _state.value.copy(summaryQuery = query); refreshAll() }
    fun openTask(task: Task?) {
        if (task == null) { _state.value = _state.value.copy(selectedTask = null, selectedTaskSources = emptyList()); return }
        viewModelScope.launch(Dispatchers.IO) { _state.value = _state.value.copy(tab = TAB_TASKS, selectedTask = task, selectedTaskSources = repo.db.listSourceImagesForTask(task.id)) }
    }
    fun openTaskById(id: Long) = viewModelScope.launch(Dispatchers.IO) { repo.db.getTask(id)?.let { _state.value = _state.value.copy(tab = TAB_TASKS, selectedTask = it, selectedTaskSources = repo.db.listSourceImagesForTask(id)) } }

    fun saveTask(task: Task) = viewModelScope.launch(Dispatchers.IO) {
        if (task.date.isBlank()) { _state.value = _state.value.copy(message = "任务必须填写日期"); return@launch }
        repo.db.updateTask(task); scheduler.cancel(task.id); scheduler.schedule(task); _state.value = _state.value.copy(selectedTask = null, requestNotificationPermission = task.reminderEnabled); refreshAll()
    }
    fun changeStatus(task: Task, status: TaskStatus) = viewModelScope.launch(Dispatchers.IO) { repo.db.markStatus(task.id, status); if(status == TaskStatus.DONE) scheduler.cancel(task.id) else scheduler.schedule(repo.db.getTask(task.id) ?: task); refreshAll() }
    fun deleteTask(task: Task, deleteSources: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        scheduler.cancel(task.id)
        repo.db.softDeleteTask(task.id)
        if (deleteSources) repo.db.removeUnreferencedSourcesForTask(task.id).forEach(repo.images::deleteIfExists)
        _state.value = _state.value.copy(selectedTask = null, selectedTaskSources = emptyList(), snackbar = if(deleteSources) "任务已删除；未被其他任务使用的来源图片也已清理" else "任务已移入回收站", lastDeletedId = task.id)
        refreshAll()
    }
    fun restoreTrashTask(task: Task) = viewModelScope.launch(Dispatchers.IO) { repo.db.restoreTask(task.id); repo.db.getTask(task.id)?.let(scheduler::schedule); refreshAll() }
    fun permanentlyDeleteTrashTask(task: Task) = viewModelScope.launch(Dispatchers.IO) { repo.db.permanentlyDeleteTask(task.id).forEach(repo.images::deleteIfExists); refreshAll() }

    fun undoDelete() = viewModelScope.launch(Dispatchers.IO) {
        val id = _state.value.lastDeletedId ?: return@launch
        repo.db.restoreTask(id)
        repo.db.getTask(id)?.let(scheduler::schedule)
        _state.value = _state.value.copy(lastDeletedId = null, snackbar = null)
        refreshAll()
    }

    fun addProject(name: String) = viewModelScope.launch(Dispatchers.IO) { repo.db.upsertProject(name); refreshAll() }
    fun renameProject(old: String, new: String) = viewModelScope.launch(Dispatchers.IO) { repo.db.renameProject(old, new); refreshAll() }
    fun deleteProject(name: String) = viewModelScope.launch(Dispatchers.IO) { repo.db.deleteProject(name); refreshAll() }
    fun addCategory(name: String) = viewModelScope.launch(Dispatchers.IO) { repo.db.upsertCategory(name); refreshAll() }
    fun deleteCategory(name: String) = viewModelScope.launch(Dispatchers.IO) { repo.db.deleteCategory(name); refreshAll() }

    fun saveApiKey(key: String) { repo.secrets.saveApiKey(key); _state.value = _state.value.copy(message = if(key.isBlank()) "API Key 已清除" else "API Key 已加密保存") }
    fun maskedApiKey(): String = repo.secrets.getApiKey().let { if(it.length < 8) it else "${it.take(3)}••••${it.takeLast(4)}" }
    fun testApiKey(key: String) = viewModelScope.launch(Dispatchers.IO) { _state.value = _state.value.copy(message = runCatching { DeepSeekClient().testConnection(key.ifBlank { repo.secrets.getApiKey() }) }.getOrElse { it.message ?: "连接失败" }) }
    fun setModel(v: String) { repo.settings.model = v; refreshAll() }
    fun setTheme(v: String) { repo.settings.theme = v; refreshAll() }
    fun setRetention(v: String) { repo.settings.imageRetention = v; refreshAll() }
    fun setAiPreference(v: String) { repo.settings.aiPreference = v; refreshAll() }

    suspend fun createExport(kind: String): Uri = withContext(Dispatchers.IO) {
        val file = when(kind) {
            "csv" -> exporter.csv(repo.db.listTasks("all"))
            "md" -> exporter.markdown(repo.db.listTasks("all"))
            else -> exporter.backupZip()
        }
        exporter.uri(file)
    }

    private fun cleanupImages() {
        val retention = repo.settings.imageRetention
        if (retention == "forever") return
        val age = when(retention) { "7d" -> 7L; "30d" -> 30L; else -> return }
        val cutoff = System.currentTimeMillis() - age * 86_400_000L
        repo.db.rawExportTables()["source_images"].orEmpty().forEach { row ->
            val created = (row["created_at"] as? Number)?.toLong() ?: 0L
            val path = row["path"] as? String ?: return@forEach
            if (created < cutoff) repo.images.deleteIfExists(path)
        }
    }
}
