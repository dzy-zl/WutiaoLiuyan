# 隐私设计

1. App 不要求账号，任务、摘要、截图和设置默认只保存在设备本地。
2. Android 系统云备份关闭；HTTP 明文流量关闭。
3. DeepSeek API Key 使用 Android Keystore AES/GCM 加密，仅在请求时解密使用。
4. 用户点击“开始识别”前，不会把待处理图片/文字发送到 DeepSeek。
5. 使用 DeepSeek Vision 时，所选图片与相关文字会发送至 DeepSeek API；这是 AI 识别必须发生的网络传输。
6. 本地备份不会包含 API Key。
7. 来源图片可设置永久、7 天、30 天或识别后删除；删除任务时还可选择清理未被其他活动任务引用的来源图片。
