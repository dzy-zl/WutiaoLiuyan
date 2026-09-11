package com.wutiaoliuyan.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.wutiaoliuyan.app.model.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

private val EyeBlue = Color(0xFF6DA7FF)

@Composable
fun WutiaoLiuyanRoot(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when(state.theme) { "dark" -> true; "light" -> false; else -> systemDark }
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.notificationPermissionHandled() }

    LaunchedEffect(state.requestNotificationPermission) {
        if (state.requestNotificationPermission) {
            if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.notificationPermissionHandled()
        }
    }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { msg ->
            val result = snackbarHostState.showSnackbar(msg, actionLabel = "撤销", withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearSnackbar()
        }
    }

    WutiaoTheme(dark) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val tabs = listOf(
                        Triple(TAB_HOME, "首页", Icons.Default.Home), Triple(TAB_TASKS, "任务", Icons.Default.CheckCircle),
                        Triple(TAB_SCAN, "识别", Icons.Default.Visibility), Triple(TAB_SUMMARY, "摘要", Icons.Default.Description),
                        Triple(TAB_SETTINGS, "设置", Icons.Default.Settings)
                    )
                    tabs.forEach { (id, label, icon) ->
                        NavigationBarItem(selected = state.tab == id, onClick = { vm.setTab(id) }, icon = { Icon(icon, label) }, label = { Text(label) })
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when(state.tab) {
                    TAB_HOME -> HomeScreen(state, vm)
                    TAB_TASKS -> TasksScreen(state, vm)
                    TAB_SCAN -> ScanScreen(state, vm)
                    TAB_SUMMARY -> SummaryScreen(state, vm)
                    TAB_SETTINGS -> SettingsScreen(state, vm)
                }
            }
        }
        state.message?.let { msg ->
            AlertDialog(onDismissRequest = vm::clearMessage, confirmButton = { TextButton(onClick = vm::clearMessage) { Text("知道了") } }, title = { Text("五条六眼") }, text = { Text(msg) })
        }
        state.selectedTask?.let { task -> TaskEditorDialog(task, state.selectedTaskSources, state.projects, state.categories, onDismiss = { vm.openTask(null) }, onSave = vm::saveTask, onDelete = vm::deleteTask) }
    }
}

@Composable
private fun WutiaoTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(primary = Color.White, secondary = EyeBlue, background = Color(0xFF070707), surface = Color(0xFF111111), onPrimary = Color.Black)
    else lightColorScheme(primary = Color.Black, secondary = EyeBlue, background = Color(0xFFFAFAFA), surface = Color.White, onPrimary = Color.White)
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun HomeScreen(state: AppUiState, vm: AppViewModel) {
    var quickText by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
            item { ScreenHeader("五条六眼", "把截图里的事情看清楚") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("今日", state.todayCount.toString(), Modifier.weight(1f), false)
                    StatCard("Inbox", state.inboxCount.toString(), Modifier.weight(1f), true)
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("快速整理", fontWeight = FontWeight.Bold)
                        OutlinedTextField(quickText, { quickText = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("粘贴聊天、通知或网页文字…") }, minLines = 3)
                        Button(onClick = { vm.updateScanText(quickText); vm.setTab(TAB_SCAN) }, enabled = quickText.isNotBlank(), modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("交给六眼") }
                    }
                }
            }
            item { Text("最近任务", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            items(state.tasks.take(6), key = { it.id }) { task -> TaskCard(task, onClick = { vm.openTask(task) }, onStatus = { vm.changeStatus(task, it) }) }
            if (state.tasks.isEmpty()) item { EmptyHint("还没有任务。把一张微信截图分享进来试试。") }
        }
        FloatingActionButton(onClick = { vm.setTab(TAB_SCAN) }, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) { Icon(Icons.Default.CenterFocusStrong, "扫描") }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, inverted: Boolean) {
    val container = if (inverted) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface
    val content = if (inverted) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    Card(modifier, colors = CardDefaults.cardColors(containerColor = container), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp)) { Text(label, color = content.copy(alpha = .7f)); Text(value, fontSize = 36.sp, fontWeight = FontWeight.Black, color = content) }
    }
}

@Composable
private fun TasksScreen(state: AppUiState, vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("任务", "收件箱、计划与项目")
        OutlinedTextField(state.taskQuery, vm::setTaskQuery, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("搜索任务、人物、标签…") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val views = listOf("all" to "全部", "inbox" to "Inbox", "today" to "今日", "planned" to "计划", "doing" to "进行中", "done" to "已完成")
            items(views) { (id, label) -> FilterChip(selected = state.taskView == id, onClick = { vm.setTaskFilter(id) }, label = { Text(label) }) }
        }
        if (state.projects.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { AssistChip(onClick = { vm.setTaskProject("") }, label = { Text("全部项目") }, leadingIcon = { if(state.taskProject.isEmpty()) Icon(Icons.Default.Done, null) }) }
            items(state.projects) { p -> AssistChip(onClick = { vm.setTaskProject(p) }, label = { Text(p) }, leadingIcon = { if(state.taskProject == p) Icon(Icons.Default.Done, null) }) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)) {
            items(state.tasks, key = { it.id }) { task -> TaskCard(task, { vm.openTask(task) }, { vm.changeStatus(task, it) }) }
            if (state.tasks.isEmpty()) item { EmptyHint("当前筛选下没有任务") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(task: Task, onClick: () -> Unit, onStatus: (TaskStatus) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val inverted = task.priority == Priority.HIGH && task.status != TaskStatus.DONE
    val bg = if (inverted) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface
    val fg = if (inverted) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).combinedClickable(onClick = onClick, onLongClick = { menu = true }), colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onStatus(if(task.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE) }) { Icon(if(task.status == TaskStatus.DONE) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = fg) }
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Bold, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(task.date, task.time, task.project, task.category).filter(String::isNotBlank).joinToString(" · ").ifBlank { "未设置时间" }, color = fg.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
            }
            Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null, tint = fg) }; DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("移到 Inbox") }, onClick = { menu=false; onStatus(TaskStatus.INBOX) })
                DropdownMenuItem({ Text("设为待办") }, onClick = { menu=false; onStatus(TaskStatus.TODO) })
                DropdownMenuItem({ Text("设为进行中") }, onClick = { menu=false; onStatus(TaskStatus.DOING) })
                DropdownMenuItem({ Text("完成") }, onClick = { menu=false; onStatus(TaskStatus.DONE) })
            } }
        }
    }
}

@Composable
private fun ScanScreen(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { vm.importUris(it) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> cameraFile?.let { if(ok) vm.importCamera(it) else it.delete() }; cameraFile = null }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item { ScreenHeader("识别", "DeepSeek Vision + 本地 OCR 兜底") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.scan.mode == "continuous", { vm.setScanMode("continuous") }, { Text("连续对话") })
                FilterChip(state.scan.mode == "independent", { vm.setScanMode("independent") }, { Text("独立分析") })
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { picker.launch("image/*") }) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("相册") }
                OutlinedButton(onClick = {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File.createTempFile("camera_", ".jpg", dir); cameraFile = file
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    camera.launch(uri)
                }) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("拍照") }
            }
        }
        if (state.scan.files.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.scan.files, key = { it.absolutePath }) { file ->
                    Box(Modifier.size(110.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        remember(file.absolutePath) { BitmapFactory.decodeFile(file.absolutePath) }?.let { bmp -> androidx.compose.foundation.Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize()) }
                        IconButton(onClick = { vm.removePendingFile(file) }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Cancel, "移除", tint = Color.White) }
                    }
                }
            }
        }
        item {
            OutlinedTextField(state.scan.text, vm::updateScanText, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), label = { Text("文字内容（可选）") }, minLines = 3)
            OutlinedTextField(state.scan.instruction, vm::updateScanInstruction, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("本次额外要求（可选）") }, placeholder = { Text("例如：重点找和采访有关的事情") })
        }
        item {
            Text("只有点击“开始识别”后，图片和文字才会发送给 DeepSeek。API Key 仅加密保存在本机。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            Button(onClick = vm::analyze, enabled = !state.scan.running, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp)) { Icon(Icons.Default.Visibility, null); Spacer(Modifier.width(8.dp)); Text(if(state.scan.running) "识别中…" else "开始识别") }
        }
        if (state.scan.running) item { AnalysisProgress(state.scan.progress) }
        state.scan.result?.let { result ->
            item {
                Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("识别摘要", fontWeight = FontWeight.Bold); Text(result.shortSummary.ifBlank { "已完成分析" }); if(result.highlights.isNotEmpty()) Text(result.highlights.joinToString("\n") { "• $it" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text("待确认任务", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); TextButton(onClick = vm::addDraft) { Icon(Icons.Default.Add, null); Text("新增") } }
            }
            items(state.scan.drafts.indices.toList()) { i ->
                val d = state.scan.drafts[i]
                Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(d.title.ifBlank { "未命名任务" }, fontWeight = FontWeight.Bold); Text(listOf(d.date.ifBlank { "缺少日期" }, d.time, d.priority.name, d.category).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if(d.date.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { editingIndex = i }) { Icon(Icons.Default.Edit, "编辑") }; IconButton(onClick = { vm.deleteDraft(i) }) { Icon(Icons.Default.DeleteOutline, "删除") }
                } }
            }
            item { Button(onClick = vm::saveAnalysis, modifier = Modifier.fillMaxWidth().padding(20.dp).height(52.dp)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("确认并保存") } }
        }
    }
    editingIndex?.let { idx -> state.scan.drafts.getOrNull(idx)?.let { DraftEditorDialog(it, state.projects, state.categories, { editingIndex = null }, { vm.updateDraft(idx, it); editingIndex = null }) } }
}

@Composable
private fun AnalysisProgress(text: String) {
    Card(Modifier.fillMaxWidth().padding(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onBackground), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(EyeBlue.copy(alpha=.18f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.RemoveRedEye, null, tint = EyeBlue, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(12.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)); Text(text, color = MaterialTheme.colorScheme.background)
        }
    }
}

@Composable
private fun SummaryScreen(state: AppUiState, vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("摘要", "按识别会话保留上下文")
        OutlinedTextField(state.summaryQuery, vm::setSummaryQuery, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("搜索摘要、重点信息和 OCR 原文") }, modifier = Modifier.fillMaxWidth().padding(horizontal=20.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical=10.dp, horizontal=20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.summaries, key={it.id}) { s ->
                var expanded by remember { mutableStateOf(false) }
                Card(onClick={expanded=!expanded}, shape=RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) {
                    Text(s.shortSummary.ifBlank { "识别摘要" }, fontWeight=FontWeight.Bold)
                    if(expanded) { Spacer(Modifier.height(8.dp)); Text(s.detailedSummary); if(s.highlights.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(s.highlights, color=MaterialTheme.colorScheme.onSurfaceVariant) }; if(s.rawText.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text("OCR / 原文", fontWeight=FontWeight.Bold); Text(s.rawText, style=MaterialTheme.typography.bodySmall) } }
                } }
            }
            if(state.summaries.isEmpty()) item { EmptyHint("还没有保存的摘要") }
        }
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, vm: AppViewModel) {
    var key by remember { mutableStateOf("") }
    var model by remember(state.model) { mutableStateOf(state.model) }
    var pref by remember(state.aiPreference) { mutableStateOf(state.aiPreference) }
    var newProject by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var renameProject by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom=32.dp)) {
        item { ScreenHeader("设置", "本地优先，API Key 不写入 APK") }
        item { Section("DeepSeek") {
            Text("已保存：${vm.maskedApiKey().ifBlank { "未配置" }}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(key, {key=it}, modifier=Modifier.fillMaxWidth(), label={Text("API Key")}, visualTransformation=PasswordVisualTransformation(), singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={vm.saveApiKey(key); key=""}) { Text("保存") }; OutlinedButton(onClick={vm.testApiKey(key)}) { Text("测试连接") } }
            OutlinedTextField(model, {model=it}, modifier=Modifier.fillMaxWidth(), label={Text("模型名称")}, singleLine=true)
            Button(onClick={vm.setModel(model)}) { Text("保存模型") }
        } }
        item { Section("AI 识别偏好") { OutlinedTextField(pref, {pref=it}, modifier=Modifier.fillMaxWidth(), minLines=3, placeholder={Text("例如：更关注采访、拍摄和交付时间")}); Button(onClick={vm.setAiPreference(pref)}) { Text("保存偏好") } } }
        item { Section("外观") { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("system" to "跟随系统", "light" to "浅色", "dark" to "夜间").forEach { (id,label)-> FilterChip(state.theme==id,{vm.setTheme(id)},{Text(label)}) } } } }
        item { Section("来源截图保留") { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("forever" to "永久","7d" to "7天","30d" to "30天","after" to "识别后删除").forEach { (id,label)-> FilterChip(state.imageRetention==id,{vm.setRetention(id)},{Text(label)}) } } } }
        item { Section("项目") {
            Row(verticalAlignment=Alignment.CenterVertically) { OutlinedTextField(newProject,{newProject=it},modifier=Modifier.weight(1f),label={Text("新项目")},singleLine=true); IconButton(onClick={ if(newProject.isNotBlank()) { vm.addProject(newProject); newProject="" } }) { Icon(Icons.Default.Add,null) } }
            state.projects.forEach { p -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(p,Modifier.weight(1f));IconButton(onClick={renameProject=p}){Icon(Icons.Default.Edit,null)};IconButton(onClick={ vm.deleteProject(p) }){Icon(Icons.Default.DeleteOutline,null)}} }
        } }
        item { Section("分类") {
            Row(verticalAlignment=Alignment.CenterVertically) { OutlinedTextField(newCategory,{newCategory=it},modifier=Modifier.weight(1f),label={Text("新分类")},singleLine=true); IconButton(onClick={ if(newCategory.isNotBlank()) { vm.addCategory(newCategory); newCategory="" } }) { Icon(Icons.Default.Add,null) } }
            state.categories.forEach { c -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(c,Modifier.weight(1f));IconButton(onClick={ vm.deleteCategory(c) }){Icon(Icons.Default.DeleteOutline,null)}} }
        } }
        item { Section("回收站 · 30 天") {
            if(state.trashTasks.isEmpty()) Text("回收站为空",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            state.trashTasks.forEach { t -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){ Column(Modifier.weight(1f)){Text(t.title,fontWeight=FontWeight.SemiBold);Text(t.date,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton(onClick={vm.restoreTrashTask(t)}){Text("恢复")};IconButton(onClick={ vm.permanentlyDeleteTrashTask(t) }){Icon(Icons.Default.DeleteForever,null)} } }
        } }
        item { Section("导出与备份") {
            Text("备份包含任务、摘要、设置和仍保留的来源图片；不会包含 API Key。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("md" to "Markdown","csv" to "CSV","zip" to "备份 ZIP").forEach { (kind,label)-> OutlinedButton(onClick={ scope.launch { val uri=vm.createExport(kind); val type=if(kind=="zip") "application/zip" else "text/plain"; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { this.type=type; putExtra(Intent.EXTRA_STREAM,uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },"导出五条六眼")) } }){Text(label)} }
            }
        } }
    }
    renameProject?.let { old -> RenameDialog("重命名项目", old, {renameProject=null}) { vm.renameProject(old,it); renameProject=null } }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=7.dp),shape=RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(title,fontWeight=FontWeight.Bold,fontSize=17.sp);content()} }
}

@Composable
private fun DraftEditorDialog(draft: AnalyzedTask, projects: List<String>, categories: List<String>, onDismiss:()->Unit, onSave:(AnalyzedTask)->Unit) {
    var v by remember(draft) { mutableStateOf(draft) }
    AlertDialog(onDismissRequest=onDismiss, confirmButton={TextButton(onClick={onSave(v)}){Text("保存")}}, dismissButton={TextButton(onClick=onDismiss){Text("取消")}}, title={Text("编辑识别任务")}, text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(v.title,{v=v.copy(title=it)},label={Text("标题")}); OutlinedTextField(v.detail,{v=v.copy(detail=it)},label={Text("详情")},minLines=2)
            DateTimeFields(v.date,v.time,{v=v.copy(date=it)},{v=v.copy(time=it)})
            ChoiceRow("优先级", Priority.entries, v.priority, {v=v.copy(priority=it)}) { it.name }
            QuickTextField("项目",v.project,projects){v=v.copy(project=it)}; QuickTextField("分类",v.category,categories){v=v.copy(category=it)}
            OutlinedTextField(v.tags,{v=v.copy(tags=it)},label={Text("标签")}); OutlinedTextField(v.parentTaskTitle,{v=v.copy(parentTaskTitle=it)},label={Text("父任务 / 子任务归属")}); OutlinedTextField(v.proposer,{v=v.copy(proposer=it)},label={Text("提出人")}); OutlinedTextField(v.executor,{v=v.copy(executor=it)},label={Text("执行人")}); OutlinedTextField(v.people,{v=v.copy(people=it)},label={Text("人物")}); OutlinedTextField(v.location,{v=v.copy(location=it)},label={Text("地点")}); OutlinedTextField(v.amount,{v=v.copy(amount=it)},label={Text("金额")})
            Row(verticalAlignment=Alignment.CenterVertically){Switch(v.reminderEnabled,{v=v.copy(reminderEnabled=it)});Spacer(Modifier.width(8.dp));Text("保存后提醒")}; if(v.reminderEnabled) OutlinedTextField(v.reminderMinutesBefore.toString(),{v=v.copy(reminderMinutesBefore=it.toIntOrNull()?.coerceIn(0,10080) ?: 0)},label={Text("提前分钟")})
            ChoiceRow("状态", TaskStatus.entries, v.status, {v=v.copy(status=it)}) { it.name }
            RecurrenceField(v.recurrence){v=v.copy(recurrence=it)}
        }
    })
}

@Composable
private fun TaskEditorDialog(task: Task, sources: List<SourceImage>, projects: List<String>, categories: List<String>, onDismiss:()->Unit, onSave:(Task)->Unit, onDelete:(Task,Boolean)->Unit) {
    var v by remember(task) { mutableStateOf(task) }
    var deleteSources by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onDismiss, confirmButton={TextButton(onClick={onSave(v)}){Text("保存")}}, dismissButton={Row{TextButton(onClick={onDelete(v,deleteSources)}){Text("删除",color=MaterialTheme.colorScheme.error)};TextButton(onClick=onDismiss){Text("取消")}}}, title={Text("任务详情")}, text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(sources.isNotEmpty()){ Text("来源截图",fontWeight=FontWeight.Bold); Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){ sources.forEach { src -> val bmp=remember(src.path){BitmapFactory.decodeFile(src.path)}; if(bmp!=null) androidx.compose.foundation.Image(bmp.asImageBitmap(),null,Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))) else Text("图片已清理",style=MaterialTheme.typography.bodySmall) } } }
            OutlinedTextField(v.title,{v=v.copy(title=it)},label={Text("标题")}); OutlinedTextField(v.detail,{v=v.copy(detail=it)},label={Text("详情")},minLines=2)
            DateTimeFields(v.date,v.time,{v=v.copy(date=it)},{v=v.copy(time=it)})
            ChoiceRow("优先级", Priority.entries, v.priority, {v=v.copy(priority=it)}) { it.name }
            QuickTextField("项目",v.project,projects){v=v.copy(project=it)}; QuickTextField("分类",v.category,categories){v=v.copy(category=it)}
            OutlinedTextField(v.tags,{v=v.copy(tags=it)},label={Text("标签")}); OutlinedTextField(v.parentTaskTitle,{v=v.copy(parentTaskTitle=it)},label={Text("父任务 / 子任务归属")}); OutlinedTextField(v.proposer,{v=v.copy(proposer=it)},label={Text("提出人")}); OutlinedTextField(v.executor,{v=v.copy(executor=it)},label={Text("执行人")}); OutlinedTextField(v.people,{v=v.copy(people=it)},label={Text("涉及人物")}); OutlinedTextField(v.location,{v=v.copy(location=it)},label={Text("地点")}); OutlinedTextField(v.amount,{v=v.copy(amount=it)},label={Text("金额")})
            Row(verticalAlignment=Alignment.CenterVertically){Switch(v.reminderEnabled,{v=v.copy(reminderEnabled=it)});Spacer(Modifier.width(8.dp));Text("任务提醒")}
            OutlinedTextField(v.reminderMinutesBefore.toString(),{v=v.copy(reminderMinutesBefore=it.toIntOrNull()?.coerceIn(0,10080) ?: 0)},label={Text("提前分钟")})
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(deleteSources,{deleteSources=it});Text("删除任务时，同时清理没有被其他任务使用的来源截图") }
            RecurrenceField(v.recurrence){v=v.copy(recurrence=it)}; ChoiceRow("状态",TaskStatus.entries,v.status,{v=v.copy(status=it)}){it.name}
        }
    })
}

@Composable
private fun DateTimeFields(date:String,time:String,onDate:(String)->Unit,onTime:(String)->Unit) {
    val context=LocalContext.current
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(date,onDate,Modifier.weight(1f),label={Text("日期")},singleLine=true,trailingIcon={IconButton(onClick={val d=runCatching{LocalDate.parse(date)}.getOrDefault(LocalDate.now());DatePickerDialog(context,{_,y,m,day->onDate(LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()}){Icon(Icons.Default.CalendarMonth,null)}})
        OutlinedTextField(time,onTime,Modifier.weight(1f),label={Text("时间")},singleLine=true,trailingIcon={IconButton(onClick={val t=runCatching{LocalTime.parse(time)}.getOrDefault(LocalTime.of(9,0));TimePickerDialog(context,{_,h,min->onTime("%02d:%02d".format(h,min))},t.hour,t.minute,true).show()}){Icon(Icons.Default.Schedule,null)}})
    }
}

@Composable
private fun <T> ChoiceRow(label:String,items:List<T>,selected:T,onSelect:(T)->Unit,text:(T)->String) { Column { Text(label,style=MaterialTheme.typography.labelMedium); Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){items.forEach{item->FilterChip(selected==item,{onSelect(item)},{Text(text(item))})}} } }

@Composable
private fun QuickTextField(label:String,value:String,options:List<String>,onValue:(String)->Unit) { Column(verticalArrangement=Arrangement.spacedBy(4.dp)){OutlinedTextField(value,onValue,label={Text(label)},modifier=Modifier.fillMaxWidth());if(options.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){options.forEach{AssistChip({onValue(it)},{Text(it)})}}} }

@Composable
private fun RecurrenceField(value:String,onValue:(String)->Unit) { val opts=listOf("none" to "不重复","daily" to "每天","weekly" to "每周","biweekly" to "每两周","monthly" to "每月","yearly" to "每年");Column{Text("重复",style=MaterialTheme.typography.labelMedium);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){opts.forEach{(id,label)->FilterChip(value==id,{onValue(id)},{Text(label)})}}} }

@Composable
private fun RenameDialog(title:String,initial:String,onDismiss:()->Unit,onSave:(String)->Unit){var text by remember{mutableStateOf(initial)};AlertDialog(onDismissRequest=onDismiss,confirmButton={TextButton(onClick={ if(text.isNotBlank()) onSave(text) }){Text("保存")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}},title={Text(title)},text={OutlinedTextField(text,{text=it},singleLine=true)})}

@Composable
private fun EmptyHint(text:String){Box(Modifier.fillMaxWidth().padding(40.dp),contentAlignment=Alignment.Center){Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
