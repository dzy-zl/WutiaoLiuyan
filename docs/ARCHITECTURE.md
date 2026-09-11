# 五条六眼 · 架构

- **UI**：Jetpack Compose + Material 3，单 Activity、五栏状态导航。
- **状态**：`AppViewModel` + `StateFlow`。
- **数据**：`SQLiteOpenHelper`，数据库版本 v4；任务、摘要、会话、来源图片、任务-来源关联、项目、分类均本地保存。
- **AI**：DeepSeek OpenAI-compatible Chat Completions。连续截图每 4 张一批，独立模式逐张处理。
- **OCR**：ML Kit 中文文字识别；DeepSeek 图像请求连续失败后降级。
- **密钥**：Android Keystore AES/GCM；不进入数据库、备份或源码。
- **图片**：导入后复制到应用私有目录，处理 EXIF 方向并控制尺寸；可设置永久/7天/30天/识别后删除。
- **提醒**：AlarmManager 非精确待机提醒；通知支持完成与延期 30 分钟；重启后重建提醒。
- **Widget**：RemoteViews，今日任务与 Inbox+今日任务两种。
- **导出**：Markdown、CSV、本地 ZIP；ZIP 不包含 API Key。
