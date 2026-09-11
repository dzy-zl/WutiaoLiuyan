# APK 构建状态

## 当前会话环境

本工程已完成源码级预检，但当前容器不能产出 Android APK，原因是构建工具链缺失且网络被禁用：

- 已有：OpenJDK 17
- 缺失：Android SDK / `android.jar`
- 缺失：`aapt2` / `d8` / `apksigner` / `zipalign`
- 缺失：本地 Gradle 安装和 Maven/Google 依赖缓存
- 网络：`services.gradle.org`、`dl.google.com` 均无法 DNS 解析
- GitHub 连接：当前没有可访问仓库，因此还不能从本会话启动 GitHub Actions

因此没有生成或伪造 APK 文件。

## 已完成验证与修正

执行：

```bash
python tools/preflight.py
```

当前容器缺少 `kotlinc`，其余离线结构检查通过；Kotlin 编译与 APK 构建仍必须由 Android 工具链验证。

已修正：

- Compose 编辑弹窗缺少 `verticalScroll` 导入的问题。
- GitHub Actions 改为安装 API 37 + Build Tools 36.0.0，并支持 Pull Request 构建。
- DeepSeek 默认模型更新为 `deepseek-flash`，加入 JSON 最大输出长度和空结果错误提示。
- Windows 一键构建会检测并尝试安装所需 Android SDK 组件。

## 在 Windows + Android Studio 上构建

推荐 PowerShell：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\build-apk.ps1
```

或者：

```bat
build-apk.bat
```

成功后输出：

```text
dist\WutiaoLiuyan-debug.apk
```

## GitHub Actions

工程已包含：

```text
.github/workflows/build-apk.yml
```

将工程推送到 GitHub 后，可在 Actions 中手动运行 `Build Android APK`，生成的 Artifact 名称为：

```text
WutiaoLiuyan-debug-apk
```
