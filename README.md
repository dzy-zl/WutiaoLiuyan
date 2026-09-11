# 五条六眼 · Android APK 项目

“**五条六眼**”是一款 Android 原生截图信息整理工具：把微信聊天截图、通知截图、网页文字或拍照内容交给 DeepSeek Vision，自动生成摘要、重点信息和结构化待办，再由用户确认后保存到本机。

## 技术基线

- Kotlin + Jetpack Compose + Material 3
- 最低 Android 10（API 29）
- `compileSdk/targetSdk 37`
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- JDK 17
- Compose BOM 2026.08.00

## 已落盘的主要能力

- 黑 / 白 / 灰主视觉，少量蓝色“六眼”点缀；跟随系统、浅色、夜间模式。
- 首页展示今日任务与 Inbox 数量，并支持快速粘贴文字。
- 相册多选、拍照、Android `ACTION_SEND` / `ACTION_SEND_MULTIPLE` 分享入口。
- 连续对话 / 独立分析两种模式。
- DeepSeek Vision 结构化输出：简短摘要、详细摘要、重点信息、OCR/原文、任务数组。
- 多图连续模式每 4 张一批，并携带前一批上下文。
- DeepSeek 自动重试 1 次；失败后用 ML Kit 中文 OCR 兜底，再尝试用 OCR 文本重新整理。
- API Key 使用 Android Keystore AES/GCM 加密保存；源码、数据库和备份均不写入 Key。
- 图片导入后复制到 App 私有目录，做 EXIF 方向校正与尺寸控制。
- 识别确认页：每条任务均可编辑标题、详情、日期/时间、优先级、项目、分类、标签、父任务、提出人、执行人、人物、地点、金额、重复规则、提醒、状态。
- 缺少日期的任务不可直接保存。
- 任务视图：全部 / Inbox / 今日 / 计划 / 进行中 / 已完成。
- 项目管理：新建、重命名、删除（删除项目只解除任务归属）。
- 分类管理：用户自行创建，AI 只从现有分类中选择。
- 搜索覆盖任务字段，以及关联会话的 OCR / 摘要文字。
- 任务提醒：支持单条开关与提前分钟；只有日期没有时间时默认前一天 20:00。
- 通知快捷操作：完成 / 延期 30 分钟；设备重启后恢复提醒。
- 重复任务：每天 / 每周 / 每两周 / 每月 / 每年，完成后生成下一周期。
- 来源截图可在任务详情查看。
- 图片保留策略：永久 / 7 天 / 30 天 / 识别后删除。
- 删除任务进入 30 天回收站；支持立即撤销、回收站恢复、永久删除。
- 删除任务时可选择同时清理“没有被其他活动任务引用”的来源截图。
- 摘要历史与全文搜索。
- Markdown、CSV 导出；ZIP 本地备份（任务、摘要、项目/分类、设置、仍存在的图片；不含 API Key）。
- 两个桌面 Widget：今日任务、Inbox + 今日任务。

## 页面结构

底部五栏：**首页 / 任务 / 识别 / 摘要 / 设置**。

首次启动直接进入首页。只有第一次真正执行 AI 识别且尚未配置 API Key 时，才会引导到设置页。

## DeepSeek 配置

进入「设置」：

1. 输入自己的 DeepSeek API Key。
2. 点击“保存”。
3. 点击“测试连接”。
4. 默认模型为 `deepseek-flash`；高级使用者可直接修改模型字符串。

只有用户主动点击“开始识别”后，所选图片/文字才会发送到 DeepSeek API。

## 构建

### Android Studio

1. 用 Android Studio 打开整个 `WutiaoLiuyan` 文件夹。
2. Gradle JDK 选择 **17**。
3. SDK Manager 安装 Android SDK 37 与兼容 Build Tools。
4. 等待 Gradle Sync。
5. 选择 `Build > Build APK(s)`。

### Windows 一键构建

双击：

```text
build-apk.bat
```

或者命令行：

```text
gradlew.bat assembleDebug
```

成功后 APK 原始位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

`build-apk.bat` 会另外复制到：

```text
dist/WutiaoLiuyan-debug.apk
```

### macOS / Linux

```bash
./tools/build-apk.sh
```

## Gradle Wrapper 说明

由于本次生成环境无法访问 `services.gradle.org`，无法在线取得官方 Gradle Wrapper JAR。项目中因此包含一个**Java 17 兼容的轻量 bootstrap wrapper**：

- `gradlew` / `gradlew.bat` 调用 `gradle/wrapper/gradle-wrapper.jar`
- bootstrap 读取 `gradle-wrapper.properties`
- 在用户电脑首次运行时自动下载官方 Gradle 9.6.0 分发包并执行

如果 Android Studio 后续提示生成/替换为官方 Gradle Wrapper，可以直接接受。

## 离线预检

项目自带：

```bash
python tools/preflight.py
```

当前生成环境中的预检结果：**11 passed, 0 failed**。

检查范围包括：

- 关键工程文件
- XML 解析
- Manifest 安全项
- 硬编码 API Key 扫描
- XML 资源引用
- Manifest 组件映射
- Kotlin 解析级语法
- SQLite v4 建库 SQL
- 纯 Kotlin 模型编译
- Gradle bootstrap JAR
- 关键功能标记

> 预检不能替代 Android Gradle Plugin 的真实编译、Android Lint 和真机安装测试。

## 当前环境无法完成的验证

当前容器没有 Android SDK，且 DNS 无法访问 Gradle / Maven 仓库，因此**无法在这里真实执行 `assembleDebug` 并产出经过编译验证的 APK**。

源码已经落盘并通过离线预检，但第一次在联网的 Android Studio 环境打开时，仍建议按 `docs/TEST_PLAN.md` 完成一次真实编译和真机验收。

## 目录

```text
app/src/main/java/com/wutiaoliuyan/app/
├─ ai/       DeepSeek、OCR、分析编排
├─ data/     SQLite、设置、Keystore、图片存储
├─ export/   Markdown / CSV / ZIP
├─ model/    数据模型
├─ notify/   AlarmManager、通知动作、重启恢复
├─ ui/       Compose 页面与 ViewModel
└─ widget/   桌面 Widget

docs/
├─ ARCHITECTURE.md
├─ PRIVACY.md
└─ TEST_PLAN.md
```
