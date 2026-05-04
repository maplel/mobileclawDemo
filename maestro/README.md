# Maestro UI 自动化测试

本目录包含 [Maestro](https://maestro.mobile.dev/) 自动化测试流程，用于在真机或模拟器上端到端测试 MobileBot App。

## 安装 Maestro

### macOS / Linux

```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
```

### Windows

```powershell
# 需要先安装 JDK 11+
# 方式一：通过 Scoop
scoop install maestro

# 方式二：手动下载
# 从 https://github.com/mobile-dev-inc/maestro/releases 下载最新版本
```

安装后验证：

```bash
maestro --version
```

## 前置条件

1. 启动 Android 模拟器或连接真机（确保 `adb devices` 可见）
2. 构建并安装 Debug 版 App：

```powershell
.\gradlew.bat assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

3. 在 App 的 Settings 页配置好 API Key、Base URL 和 Model（或确保 `local.properties` 中有有效的 build-time API Key）

## 运行测试

### 运行全部测试

```bash
maestro test maestro/flows/
```

### 运行单个测试

```bash
maestro test maestro/flows/01_basic_chat.yaml
```

### 带环境变量运行（Settings 流程需要）

```bash
MAESTRO_API_KEY="your-api-key" maestro test maestro/flows/02_settings.yaml
```

### 录制测试过程

```bash
maestro record maestro/flows/01_basic_chat.yaml
```

## 测试流程说明

| 文件 | 测试内容 | 涉及工具 |
| --- | --- | --- |
| `01_basic_chat.yaml` | 基础聊天：输入 "你好"，等待回复 | 无 |
| `02_settings.yaml` | 设置页：填写 API Key，选择 Gemini 预设，保存 | 无 |
| `03_open_url.yaml` | 工具触发：请求打开 URL | `open_url` |
| `04_smart_contact.yaml` | 多步技能：给联系人发短信 | `search_contacts` + `send_sms` |
| `05_location.yaml` | 位置读取 | `get_current_location` |
| `06_clipboard.yaml` | 剪贴板复制 | `copy_to_clipboard` |

## AI 断言

测试流程使用 Maestro 的 `assertWithAI` 功能进行模糊断言。由于 LLM 的响应是非确定性的，我们不检查精确文本，而是检查语义条件。

`assertWithAI` 需要 Maestro Cloud 账号或本地配置 AI 断言后端。如果团队没有配置 AI 断言，可以将 `assertWithAI` 替换为简单的 `assertVisible` 检查：

```yaml
# 替代方案：用 assertVisible 替代 assertWithAI
- assertVisible: "Bot:"
```

## testTag 对照表

ChatScreen 和 SettingsScreen 中已添加以下 testTag，供 Maestro 通过 `id` 定位元素：

| testTag | 位置 | 元素 |
| --- | --- | --- |
| `chat_input` | ChatScreen | 消息输入框 |
| `send_button` | ChatScreen | 发送按钮 |
| `settings_button` | ChatScreen | 设置按钮 |
| `message_list` | ChatScreen | 消息列表 |
| `settings_api_key` | SettingsScreen | API Key 输入框 |
| `settings_base_url` | SettingsScreen | Base URL 输入框 |
| `settings_model` | SettingsScreen | Model ID 输入框 |
| `settings_save` | SettingsScreen | 保存按钮 |

## 故障排除

- **App 未安装**：确保 `adb install` 成功，`adb shell pm list packages | grep mobilebot` 能看到包名。
- **元素找不到**：运行 `maestro hierarchy` 查看当前页面的元素树。
- **超时**：LLM 响应可能较慢，可以在 `extendedWaitUntil` 中增大 `timeout` 值。
- **AI 断言失败**：检查 Maestro Cloud 配置，或降级为 `assertVisible`。
