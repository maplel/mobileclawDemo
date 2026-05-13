# MobileBot 测试团队交接指南

本文档面向测试团队，重点解决 4 件事：拿到版本后先做什么、如何在虚拟/真实 Bridge 间切换测试、每两天一轮完整回归怎么执行、以及如何记录问题与建议解决方案。

如果你要准备真实 Android / 第三方接口，请同步阅读 `REAL_ANDROID_INTERFACE_GUIDE.md`。本文档主目标是帮助测试团队快速开展测试，不要求先理解全部内部实现细节。

---

## 目录

- [使用说明](#使用说明)
- [测试节奏与交付要求](#测试节奏与交付要求)
- [问题记录模板](#问题记录模板)
- [虚拟与真实 Bridge 快速指南](#虚拟与真实-bridge-快速指南)
- [1. 测试环境准备](#1-测试环境准备)
- [2. 现有自动化测试](#2-现有自动化测试)
- [3. 运行自动化测试](#3-运行自动化测试)
- [4. 手工测试矩阵 -- 基础功能](#4-手工测试矩阵----基础功能)
- [5. 手工测试矩阵 -- 通用场景框架](#5-手工测试矩阵----通用场景框架)
- [5.6 计划模式 (Plan Mode)](#56-计划模式-plan-mode)
- [6. 工具逐项验证方法 -- 原有工具](#6-工具逐项验证方法----原有工具)
- [7. 工具逐项验证方法 -- 新增通用工具](#7-工具逐项验证方法----新增通用工具)
- [8. 技能体系验证](#8-技能体系验证)
- [9. 场景端到端验证](#9-场景端到端验证)
- [10. Android 系统接入点验证](#10-android-系统接入点验证)
- [11. 多模型供应商兼容回归](#11-多模型供应商兼容回归)
- [12. 回归检查清单模板](#12-回归检查清单模板)
- [13. 编写新测试的指南（开发附录）](#13-编写新测试的指南开发附录)
- [14. CI/CD 配置与使用（开发附录）](#14-cicd-配置与使用开发附录)
- [15. JVM 集成测试（方案 C，开发/自动化附录）](#15-jvm-集成测试方案-c开发自动化附录)
- [16. Maestro UI 自动化（方案 B，可选自动化）](#16-maestro-ui-自动化方案-b可选自动化)
- [附录 A：测试相关文件索引](#附录-a测试相关文件索引)
- [17. 虚拟 Bridge 系统详解（附录）](#17-虚拟-bridge-系统详解附录)

---

## 使用说明

### 推荐阅读顺序

1. 第一次接手本项目时，先看 `测试节奏与交付要求`、`问题记录模板`、`虚拟与真实 Bridge 快速指南`、第 1 节。
2. 开始执行回归前，重点看第 4-12 节；这些章节直接对应测试顺序和验收标准。
3. 只有在需要跑自动化、排查日志或补充测试方案时，再看第 13-17 节和附录。

### 每个版本必须产出的交付物

| 交付物 | 必填内容 | 责任人 |
| --- | --- | --- |
| 版本测试记录 | 版本号 / 日期 / 测试人 / 设备 / API Level / 模型供应商 / Bridge 配置 | 测试团队 |
| 回归结论 | 通过 / 有条件通过 / 不通过；阻塞项数量；未覆盖项与原因 | 测试团队 |
| 问题清单 | 现象、复现步骤、影响范围、严重级别、建议解决方案、是否需要开发协助 | 测试团队 |
| 复测结果 | 修复后版本号、复测结论、是否关闭问题 | 测试团队 |

### 完整测试的定义

- 一次完整测试不等于只做冒烟；必须至少覆盖自动化基础检查、基础聊天、原有工具、新增通用工具、关键技能/场景、Android 集成点、问题复测和问题记录。
- 开发团队每两天更新一个新版本，测试团队也必须以两天为一个完整回归周期，针对每个版本单独形成测试记录。
- 如果某项因环境、权限、账号或接口未准备好而无法执行，必须明确标记 `Skip`，并写清楚原因、阻塞人和下一步动作，不能留空。

---

## 测试节奏与交付要求

### 两天一轮的固定节奏

| 时间段 | 必做事项 | 输出 |
| --- | --- | --- |
| 第 1 天上午 | 接收新版本、记录版本信息、确认设备和 Bridge 配置、安装并启动 App、完成基础冒烟 | 版本接收记录、冒烟结论 |
| 第 1 天下 午 | 执行原有工具和 Android 集成点测试，记录问题和建议解决方向 | 工具回归记录、首轮问题清单 |
| 第 2 天上午 | 执行新增通用工具、技能体系、关键 E2E 场景、多模型兼容回归 | 完整回归记录、补充问题清单 |
| 第 2 天下 午 | 汇总问题、补充日志和截图、输出版本测试结论；如开发提供修复包则优先复测阻塞项 | 测试结论、问题闭环状态、复测记录 |

### 测试团队与开发团队的协同要求

- 开发团队每两天提供一个新版本，测试团队按版本维度建立独立测试记录，避免把多个版本的问题混在一起。
- 测试团队在发现问题时，除了记录现象，还要补充建议解决方向，例如“疑似权限判断缺失”“建议回退到短信草稿模式”“建议补充 `VirtualMockData` 对应字段”。
- 如果某个场景依赖调试入口或内部事件触发，测试团队应在问题单中标记 `需要开发协助触发`，不要因为无法自行触发而默认判定通过。

### 建议的版本命名与记录方式

| 字段 | 建议填写方式 |
| --- | --- |
| 版本标识 | `vX.Y.Z` 或 `日期 + commit short SHA` |
| 构建来源 | 开发提供 APK / Android Studio 本地构建 / CI 构建 |
| 测试环境 | 模拟器 / 真机；API Level；机型 |
| Bridge 配置 | 默认配置 / 指定某些 bridge 切到 real |
| 模型供应商 | Gemini / DashScope / 智谱 / MiniMax |

---

## 问题记录模板

### 每条问题至少要记录的字段

| 字段 | 说明 |
| --- | --- |
| 问题标题 | 用一句话描述现象，例如“无短信权限时 `send_sms` 直接失败，没有回退到草稿模式” |
| 版本信息 | 版本号、构建日期、commit 或 APK 文件名 |
| 环境信息 | 设备 / 模拟器、API Level、模型供应商、Bridge 配置 |
| 复现步骤 | 输入话术、点击步骤、前置数据、权限状态 |
| 期望结果 | 文档或业务上期望出现的行为 |
| 实际结果 | 当前观察到的现象 |
| 影响范围 | 单工具 / 多工具 / E2E 场景 / 阻塞发布 |
| 严重级别 | P0 / P1 / P2 / P3 |
| 证据 | 截图、录屏、Logcat 关键字、返回文案 |
| 建议解决方案 | 测试团队观察到的可能原因或建议修复方向 |
| 当前状态 | 新建 / 待开发确认 / 已修复待复测 / 已关闭 |

### 严重级别建议

| 级别 | 定义 | 处理建议 |
| --- | --- | --- |
| P0 | 崩溃、数据损坏、关键场景完全不可用、阻塞版本验收 | 立即同步开发，优先复现和修复 |
| P1 | 主路径错误、关键工具或 E2E 场景失败，但有部分绕行方案 | 当轮版本必须明确处理计划 |
| P2 | 次要功能异常、文案/UI/降级策略不符合预期 | 进入版本问题清单并跟踪 |
| P3 | 优化项、建议项、非阻塞体验问题 | 归档到建议池，后续评估 |

### 建议使用的 Markdown 模板

```markdown
## 问题标题

- 版本信息：
- 测试时间：
- 测试人：
- 环境信息：
- Bridge 配置：
- 模型供应商：
- 严重级别：

### 复现步骤
1.
2.
3.

### 期望结果

### 实际结果

### 影响范围

### 证据
- 截图：
- 录屏：
- Logcat 关键字：

### 建议解决方案
- 建议 1：
- 建议 2：

### 当前状态
```

### 每个版本的汇总模板

```markdown
## 版本测试汇总

- 版本号：
- 构建来源：
- 测试时间范围：
- 测试人：
- 设备 / 模拟器：
- Bridge 配置：
- 模型供应商：

### 结论
- [ ] 通过
- [ ] 有条件通过
- [ ] 不通过

### 阻塞项
- P0：
- P1：

### 本轮新增问题

### 本轮已关闭问题

### 本轮建议解决方案

### Skip 项与原因
```

---

## 虚拟与真实 Bridge 快速指南

许多工具依赖真实设备能力或第三方 API。为了让模拟器也能稳定回归，项目提供了按 bridge 独立切换的虚拟模式。

### 当前默认配置概览

| Bridge | 默认模式 | 主要影响工具 / 能力 | 推荐测试环境 |
| --- | --- | --- | --- |
| `telephony` | virtual | `send_sms`、`dial_number` | 模拟器冒烟；真机做真实回归 |
| `contacts` | virtual | `search_contacts` | 模拟器冒烟；真机做真实回归 |
| `location` | virtual | `get_current_location` | 模拟器冒烟；真机做真实回归 |
| `notifications` | virtual | `list_notifications` | 模拟器冒烟；真机验证通知监听 |
| `files` | virtual | `read_sandbox_file` | 模拟器即可 |
| `services` | virtual | `call_service` | 模拟器即可；真实第三方接口见 `REAL_ANDROID_INTERFACE_GUIDE.md` |
| `accessibility` | virtual | 辅助功能相关 | 当前不作为真实接入完成项 |
| `media` | real | `open_camera` | 模拟器或真机 |
| `browser` | real | `open_url` | 模拟器或真机 |
| `maps` | real | `open_map` | 模拟器或真机 |
| `clipboard` | real | `copy_to_clipboard` | 模拟器或真机 |
| `share` | real | `share_text` | 模拟器或真机 |
| `system` | real | `open_settings`、`set_alarm` 等 | 模拟器或真机 |
| `appState` | real | 设备状态读取 | 模拟器或真机 |

### 当前第三方服务的优先级

- 第一优先级（当前主回归路径）：`geico`、`tesla_fleet`、`aaa_roadside`、`ctrip`、`opentable`、`marriott`
- 第二优先级（面向未来“衣食住行、吃喝拉撒”的扩展池）：`visa_checker`、`hotel_search`

### 如何切换为真实模式

1. 打开 `app/src/main/assets/virtual_bridge_config.json`
2. 将目标 bridge 的值从 `"virtual"` 改成 `"real"`
3. 重新构建并重新安装 APK
4. 启动 App 后在 Logcat 搜索 `VirtualBridgeManager`，确认模式已生效
5. 按对应测试项重新执行回归，并在测试记录中注明本轮使用的是 virtual 还是 real

### 修改配置后的重新构建

```powershell
.\gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 使用虚拟模式时你会看到什么

- 联系人、位置、短信、通知、文件、第三方服务都会返回稳定的模拟结果，便于测试流程和 UI。
- 启动时会自动注入一组 `UserProfileStore` 测试数据，便于验证保险、会员、车辆、紧急联系人、行程等场景。
- `call_service` 在虚拟模式下会返回带 `_virtual=true` 的模拟数据，便于区分真实接口结果。

### 重要注意事项

- `virtual_bridge_config.json` 里的 `defaultMode` 当前不会自动作用到所有 bridge；请显式修改对应 bridge 的键值，不要只改 `defaultMode`。
- `services` 切到 `real` 后，当前代码路径会进入 `HttpServiceGateway`，但真实 HTTP 打通还没有完成；如果要准备真实第三方接口，请先看 `REAL_ANDROID_INTERFACE_GUIDE.md`。
- `accessibility` 在真实栈里仍然是 `StubAccessibilityBridge`，当前不要把它当作真实接入完成项。
- 配置文件打包在 APK 的 assets 中，修改后必须重新构建并重新安装才能生效。

---

## 1. 测试环境准备

### 1.1 基础要求

| 项目 | 版本 |
| --- | --- |
| JDK | 17（推荐使用 Android Studio 内置的 JBR 17） |
| Gradle | 8.9（通过 Wrapper 自动管理） |
| AGP | 8.7.2 |
| Kotlin | 2.0.21 |
| Android `compileSdk` / `targetSdk` | 35 |
| Android `minSdk` | 26 |

### 1.2 必要文件

确保仓库根目录有 `local.properties`，最小内容：

```properties
sdk.dir=C\:\\Users\\<你的用户名>\\AppData\\Local\\Android\\Sdk
```

如果要运行涉及模型调用的端到端测试或手工测试，还要配好至少一个 API Key：

```properties
DASHSCOPE_API_KEY=sk-xxx
GEMINI_API_KEY=xxx
ZHIPU_API_KEY=xxx
MINIMAX_API_KEY=xxx
```

### 1.3 测试设备

- **模拟器**：API 26 ~ 35 均可，推荐使用 API 34 或 35 的 x86_64 镜像。
- **真机**：建议用 Android 10+ 真机，以便测试联系人、短信、位置、通知监听等需要真实硬件或系统权限的场景。
- **权限提前授予**：部分工具用例需要提前在系统设置中授权（联系人、位置、短信、通知访问），具体见下方各场景说明。

### 1.4 网络要求

自动化 JVM 单元测试不需要网络。手工测试和端到端测试需要：
- 能访问至少一个模型 API 端点（Gemini / DashScope / 智谱 / MiniMax）。
- 如果团队处于受限网络，项目已在 `settings.gradle.kts` 中配置了阿里云镜像，`gradle.properties` 中也增大了 TLS 超时。

### 1.5 测试数据准备（通用场景框架）

测试通用场景框架的手工场景前，需要通过代码或调试界面预设 `UserProfileStore` 数据：

| 分类 | 示例 Key | 示例 Value |
| --- | --- | --- |
| `insurance` | `geico_policy` | `POL-12345, comprehensive, $500 deductible` |
| `membership` | `AAA` | `Gold, member_id=AAA-9876` |
| `preferences` | `dining` | `no shellfish, prefer steakhouses` |
| `emergency_contacts` | `primary` | `Sarah, 206-555-1234` |
| `vehicles` | `primary` | `Tesla Model Y, WA plate ABC-1234` |
| `health` | `allergies` | `penicillin` |
| `trip_plans` | `current` | `14-day Yellowstone road trip, Day 3 of 14` |

---

## 2. 现有自动化测试

当前仓库包含 **11 个 JVM 单元测试文件**，分布在 3 个模块。

### core:domain（9 个）

| 测试文件 | 测试数 | 验证目标 |
| --- | --- | --- |
| `PlanJsonParserTest` | — | Planner 返回的 JSON 计划解析 |
| `LlmJsonPlannerPayloadTest` | — | Planner 向 LLM 发送的请求体构造 |
| `InlineToolCallParserTest` | — | 内联工具调用字符串解析 |
| `BundledSkillJsonParserTest` | 3 | Bundled JSON 技能文件解析与校验 |
| `SmartContactActionSkillTest` | — | `smart_contact_action` 技能的触发词匹配 |
| `SkillSelectorBrowserSkillTest` | — | 浏览器技能选择逻辑 |
| **`EnhancedSkillParserTest`** | 8 | 新增字段解析（category/composesSkills/requiredServices/contextConditions）、原子技能与场景技能共注册、dining 技能跨场景复用 |
| **`CallServiceToolTest`** | 5 | `call_service` 工具的参数校验、服务授权、未知服务/动作拒绝 |
| **`SubtaskExecutorTest`** | 2 | 共享事实的发布/读取/覆盖逻辑 |

### core:bridge（1 个）

| 测试文件 | 测试数 | 验证目标 |
| --- | --- | --- |
| **`StubServiceGatewayTest`** | 5 | 服务注册/列举/调用/未知服务拒绝/多服务共存 |

### core:data（1 个）

| 测试文件 | 测试数 | 验证目标 |
| --- | --- | --- |
| `UserSettingsResolutionTest` | — | API Key 回退策略、Base URL 和模型 ID 解析 |

### 测试依赖说明

| 模块 | 特殊测试依赖 | 用途 |
| --- | --- | --- |
| `core:domain` | `org.json:json:20231013` | 提供 JVM 环境下可用的 `org.json.JSONObject`（替代 Android SDK stub） |
| `core:bridge` | `kotlinx-coroutines-test` | 支持 `runBlocking` 协程测试 |

---

## 3. 运行自动化测试

### 3.1 全量 JVM 单元测试

```powershell
.\gradlew.bat test
```

### 3.2 按模块运行

```powershell
.\gradlew.bat :core:domain:testDebugUnitTest
.\gradlew.bat :core:bridge:testDebugUnitTest
.\gradlew.bat :core:data:testDebugUnitTest
```

### 3.3 按测试类运行

```powershell
# 通用场景框架相关
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.mobilebot.domain.skill.EnhancedSkillParserTest"
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.mobilebot.domain.tools.CallServiceToolTest"
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.mobilebot.domain.subtask.SubtaskExecutorTest"
.\gradlew.bat :core:bridge:testDebugUnitTest --tests "com.mobilebot.bridge.impl.StubServiceGatewayTest"

# 原有测试
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.mobilebot.domain.skill.BundledSkillJsonParserTest"
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.mobilebot.domain.planner.PlanJsonParserTest"
```

### 3.4 查看测试报告

```
core/domain/build/reports/tests/testDebugUnitTest/index.html
core/bridge/build/reports/tests/testDebugUnitTest/index.html
core/data/build/reports/tests/testDebugUnitTest/index.html
```

### 3.5 内存不足时的处理

如果 Gradle daemon 因内存不足崩溃（常见于 Windows），使用：

```powershell
$env:GRADLE_OPTS="-Xmx1g -XX:+UseSerialGC"
.\gradlew.bat :core:domain:testDebugUnitTest --no-daemon
```

---

## 4. 手工测试矩阵 -- 基础功能

本节用于回答“这次回归需要覆盖哪些基础能力”。每一行只定义一个代表性场景，适合做冒烟和回归勾选；具体前置条件、输入话术和验收点，请看第 6 节。

### 4.1 基础聊天

| 项目 | 说明 |
| --- | --- |
| **前置条件** | Settings 页配好 API Key、Base URL、Model |
| **步骤** | 1. 打开 App → 进入聊天页<br>2. 输入"你好"并发送 |
| **预期** | 模型返回回复，UI 正确渲染消息气泡 |
| **异常预期** | API Key 为空时应有错误提示，不应崩溃 |

### 4.2 原有工具冒烟覆盖矩阵

| 场景 | 代表输入 | 预期工具 | 冒烟通过标准 | 详细验收 |
| --- | --- | --- | --- | --- |
| 打开网页 | "打开百度" | `open_url` | 能正确进入浏览器打开目标网址 | 见 `6.1` |
| 地图导航 | "导航到北京天安门" | `open_map` | 能唤起地图并带上目标地点 | 见 `6.2` |
| 联系人搜索 | "找联系人三张" | `search_contacts` | 能返回联系人匹配结果或空结果提示 | 见 `6.3` |
| 拨号 | "给 13800138000 打电话" | `dial_number` | 能打开拨号盘且预填号码 | 见 `6.4` |
| 发短信 | "给李四发短信说明天开会" | `send_sms` | 能进入短信发送链路，并满足确认/降级策略 | 见 `6.5` |
| 读取位置 | "我现在在哪里" | `get_current_location` | 能返回位置结果或权限提示 | 见 `6.6` |
| 拍照 | "打开相机" | `open_camera` | 能启动相机应用 | 见 `6.7` |
| 复制到剪贴板 | "把以下内容复制到剪贴板：xxx" | `copy_to_clipboard` | 内容可被后续粘贴验证 | 见 `6.8` |
| 分享文本 | "分享这段话给微信" | `share_text` | 能弹出系统分享面板 | 见 `6.9` |
| 读取文件 | "读取工作区的 incoming.txt" | `read_sandbox_file` | 能读到 sandbox/shared 文件内容 | 见 `6.10` |
| 通知列表 | "最近有什么通知" | `list_notifications` | 能返回通知列表或权限提示 | 见 `6.11` |

### 4.3 多步技能场景

| 场景 | 输入示例 | 涉及技能/工具 | 预期 |
| --- | --- | --- | --- |
| 智能联系人操作 | "给张三发短信说周末见" | `smart_contact_action` → `search_contacts` → `send_sms` | Agent 先搜索联系人，展示或确认目标联系人；发送前明确征求确认，已授权时请求系统发送，未授权时打开短信草稿 |
| 智能拨号 | "打电话给李四" | `smart_contact_action` → `search_contacts` → `dial_number` | Agent 先搜索联系人，找到后打开拨号盘 |

### 4.4 权限边界场景

| 场景 | 条件 | 预期 |
| --- | --- | --- |
| 无联系人权限时搜索联系人 | 拒绝通讯录权限 | 不崩溃，返回权限不足的提示 |
| 无位置权限时读取位置 | 拒绝定位权限 | 不崩溃，返回权限不足的提示 |
| 无短信权限时发短信 | 拒绝 `SEND_SMS` 权限 | 退化为打开短信应用草稿模式 |
| 无通知访问时查通知 | 未授权通知监听 | 返回空列表或权限提示 |

---

## 5. 手工测试矩阵 -- 通用场景框架

本节用于回答“通用框架层需要覆盖哪些能力面”。这里关注覆盖范围和关键预期；如果要逐工具排查或做验收记录，优先看第 7 节以及第 8-10 节的专项场景。

### 5.1 服务网关 (ServiceGateway)

| 场景 | 步骤 | 预期结果 |
| --- | --- | --- |
| 服务配置加载 | 启动 App，检查日志中 `SkillAssetLoader` 输出 | 日志显示 `Registered service: tesla_fleet`, `geico`, `aaa_roadside`, `ctrip`, `opentable`, `marriott` |
| 服务查询 | 通过调试接口调用 `ServiceManager.listServices()` | 返回 6+ 个已注册服务，每个包含 id/name/category/actions |
| 服务调用成功 | 向模型说"查询我的保险保单" → Agent 调用 `call_service(geico, getPolicy)` | 返回服务响应（stub 模式返回含 serviceId/action 的确认） |
| 未注册服务调用 | 向模型说"调用不存在的服务" → `call_service(unknown, action)` | 返回 `ok=false, "Unknown service"` |
| 未授权服务调用 | 取消某服务授权后调用 | 返回 `ok=false, "not authorized"` |

### 5.2 子任务系统 (SubtaskExecutor)

| 场景 | 步骤 | 预期结果 |
| --- | --- | --- |
| 子任务创建 | Agent 调用 `spawn_subtask(taskId="test", instruction="...")` | 返回 `ok=true, "Subtask 'test' spawned"` |
| 重复 taskId 拒绝 | 再次 `spawn_subtask(taskId="test", ...)` | 返回 `ok=false, "already exists"` |
| 子任务状态查询 | `check_subtask(taskId="test")` | 返回状态：PENDING/RUNNING/COMPLETED/FAILED |
| 全部子任务查询 | `check_subtask()` (无参) | 列出所有子任务及其状态 |
| 共享事实读写 | 子任务内 `publishFact("key","val")` → 主任务 `check_subtask(factKey="key")` | 返回 `val` |

### 5.3 用户数据 (UserProfileStore)

| 场景 | 步骤 | 预期结果 |
| --- | --- | --- |
| 读取保险信息 | 预设数据后，"查看我的保险" → `read_user_profile(insurance)` | 返回保单信息 |
| 读取会员信息 | "我的 AAA 会员状态" → `read_user_profile(membership, AAA)` | 返回 AAA 会员详情 |
| 读取饮食偏好 | "我有什么忌口" → `read_user_profile(preferences, dining)` | 返回饮食偏好 |
| 读取紧急联系人 | `read_user_profile(emergency_contacts)` | 返回联系人列表 |
| 读取不存在的数据 | `read_user_profile(nonexistent)` | 返回 `ok=true, "No data found"` |
| 数据加密持久化 | 写入数据 → 杀进程 → 重启 → 再读取 | 数据持久存在 |

### 5.4 技能加载

| 场景 | 步骤 | 预期结果 |
| --- | --- | --- |
| 原子技能加载 | 启动 App，检查日志 | 日志显示 `emergency_response`, `insurance_claim`, `tow_and_repair`, `trip_replan`, `accommodation`, `dining`, `flight_booking`, `visa_preparation` 注册成功 |
| 场景技能加载 | 启动 App，检查日志 | 日志显示 `road_accident_orchestration`, `travel_abroad` 注册成功 |
| composesSkills 校验 | 场景技能引用的原子技能都存在 | 日志无 "references unknown composesSkill" 警告 |
| JSON 格式错误容错 | 在 `assets/skills/bundled/` 放一个格式错误的 JSON | App 不崩溃，跳过该文件并记录日志 |

### 5.5 事件系统 (EventSource)

| 场景 | 步骤 | 预期结果 |
| --- | --- | --- |
| TeslaEventSource 模拟碰撞 | 调用 `TeslaEventSource.simulateCollision()` | EventRouter 收到事件，发布到 MessageBus |
| CalendarEventSource 模拟提醒 | 调用 `CalendarEventSource.simulateReminder("会议", 5)` | 发布 `calendar_reminder` 事件 |
| HealthEventSource 心率告警 | 调用 `HealthEventSource.simulateHeartRateAlert(160)` | 发布 `health_alert` 事件，priority=CRITICAL |
| SmartHomeEventSource 烟雾告警 | 调用 `SmartHomeEventSource.simulateAlert("smoke", "detector-1", "kitchen")` | 发布 CRITICAL 优先级事件 |
| 未启动的 EventSource | `stop()` 后 `simulateCollision()` | 不产生事件 |

---

### 5.6 计划模式 (Plan Mode)

Agent 对复杂任务自动生成结构化计划，展示给用户审批后再逐步执行。核心组件：`CreatePlanTool`（`create_plan`）、`PlanManager`。

| 场景 | 步骤 | 预期结果 |
| --- | --- | --- |
| 计划创建 | 输入复杂多步请求，如 "帮我规划明天的行程：订餐厅、订酒店、买机票" | LLM 调用 `create_plan`，UI 显示 TodoListCard + ActionPrompt 三按钮 |
| 计划批准 | 点击"按此计划执行" / 输入 `approve_plan` | 逐步执行；TodoList 状态 PENDING → RUNNING → COMPLETED |
| 计划修改 | 点击"修改计划" / 输入 `edit_plan` | Agent 回复请用户修改；用户输入后 LLM 重新 `create_plan` |
| 计划取消 | 点击"取消" / 输入 `reject_plan` | 返回 "Plan cancelled."，不执行 |
| 自由文本修改 | 输入非按钮文本如 "把第二步改成去吃火锅" | 清除旧计划，LLM 重新生成 |
| 简单任务不触发 | 输入 "设个7点的闹钟" | 直接执行，不调用 `create_plan` |
| 用户要求计划 | 输入 "帮我做个计划" | LLM 始终调用 `create_plan` |
| 含 fork skill 步骤 | 计划步骤触发 fork skill | SubAgentRunner 完成后步骤标 COMPLETED |
| 参数校验 | 空 title 或空 steps | 返回 `ok=false`，不创建计划 |
| 全部完成 | 所有步骤完成 | PlanManager 状态转 DONE；Agent 输出汇总 |

#### 验证检查点

```
场景 A：完整计划流程
  输入: "帮我发短信给张三和李四，都说明天下午3点开会"
  验证:
    ✅ LLM 调用 create_plan，生成 2-3 步计划
    ✅ UI 显示 TodoListCard + 三个操作按钮
    ✅ 点击"按此计划执行"后，步骤依次变为 RUNNING → COMPLETED
    ✅ Agent 最终给出执行汇总

场景 B：计划修改循环
  输入: "帮我规划周末活动"
  验证:
    ✅ 收到计划后输入"加一个看电影的环节"
    ✅ LLM 重新调用 create_plan，新计划包含看电影
    ✅ 可以再次修改或批准

场景 C：计划取消
  输入: 任意复杂请求
  验证:
    ✅ 收到计划后点击"取消"
    ✅ 返回"Plan cancelled."
    ✅ 后续正常输入不受影响
```

---

## 6. 工具逐项验证方法 -- 原有工具

本节用于回答“某个工具具体怎么测、测到什么算通过”。与 `4.2` 的区别是：`4.2` 只做覆盖矩阵和冒烟入口，本节提供逐项验收步骤，适合回归签字、问题复现和排查。

### 6.1 `open_url`

```
输入: "帮我打开 https://www.baidu.com"
验证:
  ✅ 系统浏览器被唤起
  ✅ 打开的 URL 正确
  ✅ 无网络时返回错误信息而不是崩溃
```

### 6.2 `open_map`

```
输入: "导航到上海外滩"
验证:
  ✅ 地图应用被唤起
  ✅ 搜索/导航目标正确
```

### 6.3 `search_contacts`

```
输入: "查找联系人三张"
前置: 通讯录中至少有一个姓张的联系人
验证:
  ✅ 返回匹配的联系人信息
  ✅ 无匹配时返回空结果提示
```

### 6.4 `dial_number`

```
输入: "拨打 10086"
验证:
  ✅ 拨号盘打开，号码预填
  ✅ 不会自动拨出
```

### 6.5 `send_sms`

```
输入: "给 13800138000 发短信：测试消息"
验证（有权限）: ✅ 系统接受发送请求，并返回“已请求发送/待系统投递确认”之类结果
验证（无权限）: ✅ 打开短信应用，内容已预填
```

### 6.6 `get_current_location`

```
输入: "我在哪里"
前置: 已授权精确定位
验证: ✅ 返回经纬度或可读地址
```

### 6.7 `open_camera`

```
输入: "打开相机"
验证: ✅ 相机应用启动
```

### 6.8 `copy_to_clipboard`

```
输入: "把 hello world 复制到剪贴板"
验证: ✅ 打开任意文本框粘贴，内容为 "hello world"
```

### 6.9 `share_text`

```
输入: "分享一段话：今天天气真好"
验证: ✅ 系统分享面板弹出
```

### 6.10 `read_sandbox_file`

```
前置: 先通过 ACTION_SEND 分享文本到 MobileBot
输入: "读取 shared/incoming.txt"
验证: ✅ 返回之前分享进来的文本内容
```

### 6.11 `list_notifications`

```
前置: 系统设置中授权 MobileBot 通知访问
输入: "我有什么通知"
验证: ✅ 返回缓存的通知列表
```

---

## 7. 工具逐项验证方法 -- 新增通用工具

本节是第 5 节中工具能力的逐项验收手册。第 5 节负责列出覆盖面，本节负责给出更细的输入、前置条件和通过标准。

### 7.1 `call_service`

```
场景 A：调用已注册服务
  输入: "查询我的 Geico 保险保单"
  预期: Agent 调用 call_service(geico, getPolicy)，返回保单信息（stub 模式下返回确认）
  验证:
    ✅ 返回 ok=true
    ✅ dataJson 包含 serviceId 和 action 字段

场景 B：调用未注册服务
  输入: (触发 call_service 调用不存在的服务)
  验证:
    ✅ 返回 ok=false
    ✅ 消息包含 "Unknown service"

场景 C：空 serviceId
  验证:
    ✅ 返回 ok=false
    ✅ 消息包含 "required"
```

### 7.2 `spawn_subtask`

```
场景 A：正常创建子任务
  输入: (模型决定 spawn 子任务)
  验证:
    ✅ 返回 ok=true
    ✅ 消息包含 "spawned and running"

场景 B：taskId 重复
  验证:
    ✅ 返回 ok=false
    ✅ 消息包含 "already exists"

场景 C：空 taskId 或空 instruction
  验证:
    ✅ 返回 ok=false
```

### 7.3 `check_subtask`

```
场景 A：查询特定子任务
  前置: 已 spawn 一个子任务
  验证:
    ✅ 返回子任务状态（PENDING/RUNNING/COMPLETED/FAILED）
    ✅ dataJson 包含 taskId, status

场景 B：查询所有子任务
  前置: 已 spawn 多个子任务
  输入: check_subtask 无参
  验证:
    ✅ 列出所有子任务及状态摘要

场景 C：读取共享事实
  前置: 某子任务已 publishFact("police_case_number", "MT-2024-0891")
  输入: check_subtask(factKey="police_case_number")
  验证:
    ✅ 返回 "MT-2024-0891"

场景 D：查询不存在的子任务
  验证:
    ✅ 返回 ok=false, "Unknown subtask"
```

### 7.4 `read_user_profile`

```
场景 A：读取整个分类
  前置: UserProfileStore 中已预设 insurance 分类数据
  输入: "查看我的保险信息"
  验证:
    ✅ 返回该分类下所有 key-value 对

场景 B：读取特定 key
  输入: read_user_profile(membership, AAA)
  验证:
    ✅ 仅返回 AAA 对应的值

场景 C：分类不存在
  验证:
    ✅ 返回 ok=true, "No data found"

场景 D：空分类名
  验证:
    ✅ 返回 ok=false
```

---

## 8. 技能体系验证

### 8.1 原子技能独立使用

验证每个原子技能在独立上下文中能被正确触发和执行。

| 原子技能 | 独立触发输入 | 预期激活的技能 ID | 核心验证点 |
| --- | --- | --- | --- |
| 紧急响应 | "帮我报警" | `emergency_response` | 调用 `dial_number` + `get_current_location` |
| 保险理赔 | "帮我申请保险理赔" | `insurance_claim` | 调用 `read_user_profile(insurance)` + `call_service(geico, ...)` |
| 拖车维修 | "我车坏了需要拖车" | `tow_and_repair` | 调用 `read_user_profile(membership)` + `call_service(aaa_roadside, ...)` |
| 行程重规划 | "帮我调整旅行计划" | `trip_replan` | 调用 `read_user_profile(trip_plans)` |
| 住宿预订 | "帮我订个酒店" | `accommodation` | 调用 `call_service(marriott, ...)` |
| 餐饮预订 | "帮我订个餐厅" | `dining` | 调用 `call_service(opentable, ...)` |
| 机票预订 | "帮我订张飞机票" | `flight_booking` | 调用 `call_service(ctrip, searchFlights)` |
| 签证准备 | "去日本需要签证吗" | `visa_preparation` | 根据用户国籍提供签证指导 |

### 8.2 场景技能编排验证

| 场景技能 | 触发输入 | 预期并行子任务 | 预期顺序依赖 |
| --- | --- | --- | --- |
| `road_accident_orchestration` | "我发生了车祸" 或碰撞事件触发 | emergency + insurance + towing 并行 | trip_replan 等 emergency 完成 → hotel 等 replan 完成 → dinner 等 hotel 完成 |
| `travel_abroad` | "帮我规划去日本的旅行" | visa + flights 并行 | hotels 等 flights 确认 → itinerary + restaurants 等 hotels 确认 |

### 8.3 技能复用验证 (核心)

**目标**：同一个 `dining` 技能在不同上下文中自然产生不同行为，无需代码 overlay。

| 上下文 | 触发方式 | 预期 dining 行为差异 |
| --- | --- | --- |
| 用户独立使用 | 直接说"帮我订个餐厅" | 基于用户当前位置和偏好搜索 |
| 车祸事故子任务 | 由 `road_accident_orchestration` spawn: "Book dinner near Marriott Billings, comfort food" | 搜索 Billings 市 Marriott 附近的舒适餐厅 |
| 出国旅行子任务 | 由 `travel_abroad` spawn: "local cuisine in Tokyo, tourist-friendly" | 搜索东京当地美食，适合游客 |

**验证方法**：
1. 分别在三个上下文中触发 dining
2. 确认 Agent 调用 `call_service(opentable, searchRestaurants)` 时传递的参数不同
3. 确认三次使用的都是同一个 `dining.json` 技能定义

### 8.4 SkillManifest 新字段验证

| 字段 | 验证方法 |
| --- | --- |
| `category` | `SkillManager.listInstalled()` 返回的每个技能都有正确的 category |
| `requiredServices` | 技能声明的服务 ID 在 `ServiceGateway.listAvailableServices()` 中都存在 |
| `composesSkills` | 场景技能引用的原子技能 ID 都在 `SkillRegistry` 中注册 |
| `contextConditions.events` | `road_accident_orchestration` 的 events 包含 `vehicle_collision` |
| `userConfirmationPoints` | `road_accident_orchestration` 的 confirmationPoints 包含 `confirm_new_itinerary` |

---

## 9. 场景端到端验证

### 9.1 黄石事故全流程 (E2E)

```
前置条件:
  - UserProfileStore 已预设保险、AAA 会员、车辆、紧急联系人、行程数据
  - TeslaEventSource 已注册到 EventRouter
  - 至少一个 LLM API Key 可用

步骤:
  1. 调用 TeslaEventSource.simulateCollision(latitude=45.78, longitude=-109.95)
  2. 或直接输入"我刚发生了车祸碰撞"

验证清单:
  阶段 1: 即时响应（并行）
  ✅ emergency 子任务被 spawn
  ✅ insurance 子任务被 spawn
  ✅ towing 子任务被 spawn
  ✅ 三个子任务并行执行，不互相阻塞

  阶段 2: 稳定后
  ✅ trip_replan 子任务被 spawn（等前面完成）
  ✅ 行程重规划基于原有 14 天计划

  阶段 3: 行程确认后
  ✅ accommodation 子任务被 spawn
  ✅ 搜索的酒店在拖车目的地城市附近

  阶段 4: 住宿确认后
  ✅ dining 子任务被 spawn
  ✅ 搜索的餐厅在已订酒店附近

  阶段 5: 汇总
  ✅ Agent 输出完整的结果汇总卡片
  ✅ 涵盖：紧急状态、保险案件号、拖车目的地、新行程、酒店、晚餐
```

### 9.2 出国旅行全流程 (E2E)

```
前置条件:
  - UserProfileStore 已预设旅行偏好、会员信息
  - 至少一个 LLM API Key 可用

步骤:
  输入: "帮我规划一趟去日本东京的 7 天旅行"

验证清单:
  ✅ visa_preparation 子任务被 spawn（检查签证要求）
  ✅ flight_booking 子任务被 spawn（搜索机票）
  ✅ 上述两个并行执行
  ✅ accommodation 子任务在机票确认后 spawn
  ✅ dining 子任务搜索东京本地餐厅
  ✅ 最终输出完整旅行方案
```

### 9.3 新增场景零代码验证

**目标**：证明添加新场景只需 JSON，不改 Kotlin 代码。

```
步骤:
  1. 在 assets/skills/scenarios/ 新增 food_delivery.json:
     {
       "id": "food_delivery",
       "name": "Food Delivery",
       "triggers": ["外卖", "叫外卖", "点餐"],
       "applicableTools": ["spawn_subtask", "call_service", "read_user_profile"],
       "composesSkills": ["dining"],
       "promptBody": "...编排指令..."
     }
  2. 重新编译并安装
  3. 输入"帮我叫个外卖"

验证:
  ✅ food_delivery 场景技能被激活
  ✅ dining 原子技能在子任务中被复用
  ✅ 全程不需要修改任何 Kotlin 代码
```

---

## 10. Android 系统接入点验证

### 10.1 ACTION_SEND 接收

```
步骤:
  1. 在浏览器或任意 App 中选中文本
  2. 点分享 → 选择 MobileBot
验证:
  ✅ MobileBot 启动并进入聊天页
  ✅ filesDir/workspace/shared/incoming.txt 被正确写入
```

### 10.2 通知监听服务

```
步骤:
  1. 系统设置 → 通知访问 → 启用 MobileBot
  2. 用另一个 App 发送通知
  3. 在 MobileBot 聊天中触发 list_notifications
验证:
  ✅ 通知被镜像到内存缓存
  ✅ 可通过 list_notifications 查询
```

### 10.3 前台服务

```
步骤: 发送一条聊天消息
验证:
  ✅ 通知栏出现 Agent 运行的前台通知
  ✅ Agent 执行完成后前台通知消失
```

### 10.4 技能和服务启动加载

```
步骤: 安装 App 并首次启动
验证（Logcat 过滤 "SkillAssetLoader"）:
  ✅ 日志输出 "Registered bundled skill: ..." 若干条
  ✅ 日志输出 "Registered service: ..." 若干条
  ✅ 日志输出 "Registered skill from skills/scenarios: ..." 若干条
  ✅ 无 WARN 级别的 "references unknown composesSkill" 消息
```

---

## 11. 多模型供应商兼容回归

| 供应商 | Base URL | 默认模型 | 回归重点 |
| --- | --- | --- | --- |
| Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-2.0-flash` | 基线；工具触发、JSON Plan 解析 |
| DashScope / Qwen | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` | 系统消息格式、工具调用 |
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-4.7-flash` | messages 格式兼容性 |
| MiniMax | `https://api.minimaxi.com/v1` | `MiniMax-M2.7` | `reasoning_split=true` 自动附加 |

每个供应商至少执行：
1. 纯文本聊天
2. 触发一个原有工具（如 `open_url`）
3. 触发 `call_service` 通用工具
4. 触发 `spawn_subtask`（需要较强模型能力）
5. 触发多步骤技能（如 `smart_contact_action`）
6. 输入模糊指令，观察是否返回 `ask_user` 追问

---

## 12. 回归检查清单模板

每次版本发布或重大改动后，使用以下清单做回归：

```markdown
## 回归检查清单 - v____ / 日期 ____

### 编译与基础
- [ ] `.\gradlew.bat assembleDebug` 编译通过
- [ ] `.\gradlew.bat :core:domain:testDebugUnitTest` 通过
- [ ] `.\gradlew.bat :core:bridge:testDebugUnitTest` 通过
- [ ] `.\gradlew.bat :core:data:testDebugUnitTest` 通过
- [ ] App 能在 API 26 模拟器上启动
- [ ] App 能在 API 35 模拟器上启动

### 基础聊天
- [ ] 默认供应商纯文本聊天正常
- [ ] API Key 为空时有错误提示
- [ ] 消息列表能恢复历史记录

### 原有工具验证（11 个）
- [ ] open_url 正常
- [ ] open_map 正常
- [ ] search_contacts 正常
- [ ] dial_number 正常
- [ ] send_sms 正常（有权限 / 无权限各一次）
- [ ] get_current_location 正常
- [ ] open_camera 正常
- [ ] copy_to_clipboard 正常
- [ ] share_text 正常
- [ ] read_sandbox_file 正常
- [ ] list_notifications 正常

### 新增通用工具验证（4 个）
- [ ] call_service: 已注册服务调用成功
- [ ] call_service: 未注册服务拒绝
- [ ] spawn_subtask: 正常创建
- [ ] spawn_subtask: 重复 taskId 拒绝
- [ ] check_subtask: 查询特定子任务
- [ ] check_subtask: 查询共享事实
- [ ] read_user_profile: 读取已有分类
- [ ] read_user_profile: 读取不存在分类

### 技能体系
- [ ] 原子技能启动时全部加载（检查日志）
- [ ] 场景技能启动时全部加载（检查日志）
- [ ] 服务配置启动时全部加载（检查日志）
- [ ] composesSkills 校验无警告
- [ ] dining 技能独立触发正常
- [ ] dining 技能在场景子任务中触发正常
- [ ] 新增 JSON-only 场景不需要改 Kotlin 代码

### 场景端到端
- [ ] 黄石事故场景: 并行子任务正常 spawn
- [ ] 黄石事故场景: 阶段依赖正确（trip_replan 在 emergency 后）
- [ ] 出国旅行场景: 复用 dining/accommodation 正常

### 系统集成
- [ ] ACTION_SEND 分享导入正常
- [ ] 通知监听正常（如授权）
- [ ] 前台服务启停正常
- [ ] Heartbeat Worker 不引起崩溃

### 计划模式 (Plan Mode)
- [ ] 复杂请求触发 create_plan，显示计划 + 三按钮
- [ ] 点击"执行"后逐步执行，步骤状态正确更新
- [ ] 点击"修改"后可重新生成计划
- [ ] 点击"取消"后正常回退
- [ ] 简单单步请求不触发计划
- [ ] 用户显式要求 "做个计划" 触发 create_plan

### 权限降级
- [ ] 无联系人权限时不崩溃
- [ ] 无位置权限时不崩溃
- [ ] 无短信权限时退化为草稿模式
- [ ] 无通知访问时返回空/提示

### 多供应商（至少选一个非默认供应商测一遍）
- [ ] 供应商: ______，纯聊天通过
- [ ] 供应商: ______，call_service 工具触发通过
- [ ] 供应商: ______，多步骤场景通过
```

---

## 13. 编写新测试的指南（开发附录）

### 13.1 JVM 单元测试

项目使用 JUnit 4。新测试遵循同样的模式。

**放置位置**：`<模块>/src/test/java/com/mobilebot/<模块路径>/`

**关键原则**：
- 不依赖 Android SDK —— 测试在 JVM 上直接运行。
- 如果使用 `org.json.JSONObject`，确保模块的 `build.gradle.kts` 中有 `testImplementation("org.json:json:20231013")`。
- 不依赖网络。
- 每个测试方法只验证一个行为。
- 需要 `ServiceGateway` 时，使用内联的 `InMemoryGateway` 或 `StubServiceGateway`。
- 需要 `DeviceCapabilityBridge` 时，创建 `MinimalBridge` stub（参考 `CallServiceToolTest`）。
- 需要 `SubtaskExecutor` 时，用 `Provider { throw }` 作为 runtimeProvider 和 stub 的 SessionRepository/MemoryFacade（参考 `SubtaskExecutorTest`）。

### 13.2 建议优先补充的自动化测试

| 目标 | 模块 | 优先级 | 说明 |
| --- | --- | --- | --- |
| `SpawnSubtaskTool` 参数校验 | `core:domain` | P0 | 空 taskId/instruction 拒绝，重复 taskId 拒绝 |
| `CheckSubtaskTool` 各场景 | `core:domain` | P0 | 查询特定任务/全部任务/共享事实/不存在任务 |
| `ReadUserProfileTool` 参数校验 | `core:domain` | P0 | 空 category 拒绝，正常读取，不存在 key |
| `SkillManager` 安装/卸载 | `core:domain` | P1 | 版本比较、builtin 不可卸载、重复安装 |
| `ServiceManager` 操作 | `core:domain` | P1 | 安装/列举/授权检查 |
| `SkillAssetLoader` 集成 | `core:data` | P1 | 加载 bundled/ + scenarios/ + services/，composesSkills 校验 |
| `EventRouter` 事件分发 | `core:domain` | P2 | 注册 EventSource → 模拟事件 → 验证 MessageBus 收到 InboundMessage |
| `ToolPolicyEngine` 策略检查 | `core:domain` | P2 | 不同能力快照和前台状态下工具放行/拒绝 |
| `ContextBuilder` 消息裁剪 | `core:domain` | P2 | 消息数量限制和字符截断逻辑 |
| `ToolRegistry` 工具过滤 | `core:domain` | P2 | 给定能力集合，验证返回的可用工具列表（含 4 个新工具） |
| `SkillSelector` 条件激活 | `core:domain` | P2 | contextConditions 匹配逻辑（待实现后补充） |

### 13.3 测试代码模板 -- 新工具

```kotlin
class MyNewToolTest {
    private lateinit var tool: MyNewTool

    @Before
    fun setUp() {
        // 用 stub 依赖构造工具实例
        tool = MyNewTool(StubDependency())
    }

    @Test
    fun rejectsEmptyRequiredParam() = runBlocking {
        val result = tool.execute("""{"param":""}""")
        assertFalse(result.ok)
    }

    @Test
    fun executesSuccessfully() = runBlocking {
        val result = tool.execute("""{"param":"valid_value"}""")
        assertTrue(result.ok)
    }
}
```

### 13.4 测试代码模板 -- 技能 JSON 解析

```kotlin
class MySkillJsonTest {
    @Test
    fun parsesNewSkillJson() {
        val json = """
            {
              "id": "my_skill",
              "name": "My Skill",
              "description": "...",
              "triggers": ["trigger_word"],
              "applicableTools": ["call_service"],
              "category": "my_category",
              "composesSkills": ["sub_skill_a"]
            }
        """.trimIndent()

        val skill = BundledSkillJsonParser.parse(json)
        assertNotNull(skill)
        assertEquals("my_category", skill!!.manifest.category)
        assertEquals(listOf("sub_skill_a"), skill.manifest.composesSkills)
    }
}
```

---

## 14. CI/CD 配置与使用（开发附录）

### 14.1 配置文件

```
.github/workflows/ci.yml
```

### 14.2 CI 覆盖内容

| Job | 做什么 | 失败意味着 |
| --- | --- | --- |
| **build** | `assembleDebug` | 代码编译不通过 |
| **test** | `test` 全部 JVM 单元测试 | 有测试失败（含新增的框架测试） |
| **lint** | `lintDebug` | 代码质量问题 |

### 14.3 本地模拟 CI

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lintDebug
```

三个都通过等同于 CI 全绿。

---

## 15. JVM 集成测试（方案 C，开发/自动化附录）

方案 C 通过 Recording Bridge 替换真实 Android 系统能力，在纯 JVM 上自动验证完整的工具执行链路，无需设备。

### 15.1 架构原理

所有工具通过 `DeviceCapabilityBridge` facade 访问系统能力。测试中用 `RecordingDeviceCapabilityBridge` 组合多个 `RecordingXxxBridge` 替代真实 Android 实现。每个 Recording Bridge 会记录被调用的方法和参数，并返回可配置的结果。

### 15.2 测试替身（Test Doubles）

位于 `core/domain/src/test/java/com/mobilebot/domain/testdoubles/`：

| 文件 | 包含类 | 用途 |
| --- | --- | --- |
| `RecordingBridges.kt` | `RecordingBrowserBridge`, `RecordingMapsBridge`, `RecordingContactsBridge`, `RecordingTelephonyBridge`, `RecordingClipboardBridge`, `RecordingShareBridge`, `RecordingMediaBridge`, `RecordingLocationBridge`, `RecordingFileBridge`, `RecordingNotificationBridge` | 记录每个 Bridge 的调用历史 |
| `RecordingDeviceCapabilityBridge.kt` | `RecordingDeviceCapabilityBridge`, `StubAppStateBridge`, `StubAccessibilityBridge`, `StubServiceGatewayForTest` | 组合所有 Recording Bridge 成一个完整的测试用 `DeviceCapabilityBridge` |
| `TestInfra.kt` | `AllCapabilitiesProbe`, `NoCapabilitiesProbe`, `SelectiveCapabilitiesProbe`, `AlwaysForegroundReader`, `NeverForegroundReader` | 控制能力探测和前台状态的测试桩 |

### 15.3 测试文件

| 测试文件 | 测试范围 | 测试数量 |
| --- | --- | --- |
| `ToolRegistryIntegrationTest.kt` | 11 个工具的端到端执行验证 | 20 个用例 |
| `ToolPolicyEngineTest.kt` | 策略引擎的 4 种决策场景 | 5 个用例 |
| `AgentToolChainTest.kt` | Plan JSON 解析 → ToolRegistry 执行的完整子链路 | 7 个用例 |

### 15.4 运行方式

```powershell
# 运行全部方案 C 测试
.\gradlew.bat :core:domain:testDebugUnitTest --no-daemon

# 仅运行工具集成测试
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.mobilebot.domain.tools.ToolRegistryIntegrationTest"

# 仅运行策略引擎测试
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.mobilebot.domain.tools.ToolPolicyEngineTest"

# 仅运行 Agent 链路测试
.\gradlew.bat :core:domain:testDebugUnitTest --tests "com.mobilebot.domain.agent.AgentToolChainTest"
```

### 15.5 如何编写新的集成测试

```kotlin
@Test
fun myNewTool_callsExpectedBridge() = runBlocking {
    // 1. 创建 RecordingDeviceCapabilityBridge（内含所有 Recording Bridge）
    val bridge = RecordingDeviceCapabilityBridge()
    // 2. 配置 Recording Bridge 的返回值
    bridge.recordingContacts.results = listOf("张三|13800138000")
    // 3. 构建 ToolRegistry（用 AllCapabilitiesProbe 跳过能力检查）
    val probe = AllCapabilitiesProbe()
    val policyEngine = ToolPolicyEngine(probe, AlwaysForegroundReader(), bridge)
    val registry = ToolRegistry(setOf(MyNewTool(bridge)), probe, policyEngine)
    // 4. 执行工具
    val result = registry.execute("my_tool", """{"key":"value"}""")
    // 5. 验证 Bridge 被正确调用
    assertTrue(result.ok)
    assertEquals(1, bridge.recordingXxx.calls.size)
}
```

---

## 16. Maestro UI 自动化（方案 B，可选自动化）

方案 B 使用 Maestro 在真机或模拟器上驱动 App UI，配合 AI 断言验证 LLM 非确定性输出。

### 16.1 安装 Maestro

```bash
# macOS / Linux
curl -Ls "https://get.maestro.mobile.dev" | bash

# 验证
maestro --version
```

Windows 用户参考 `maestro/README.md`。

### 16.2 前置条件

1. 启动模拟器或连接真机
2. 构建并安装 Debug APK
3. 在 Settings 页配置好 API Key

### 16.3 运行 Maestro 测试

```bash
# 全部测试
maestro test maestro/flows/

# 单个测试
maestro test maestro/flows/01_basic_chat.yaml

# 带环境变量
MAESTRO_API_KEY="your-key" maestro test maestro/flows/02_settings.yaml
```

### 16.4 可用测试流程

| 文件 | 场景 | 涉及工具 |
| --- | --- | --- |
| `01_basic_chat.yaml` | 基础聊天 | 无 |
| `02_settings.yaml` | 设置页配置 | 无 |
| `03_open_url.yaml` | 打开 URL | `open_url` |
| `04_smart_contact.yaml` | 联系人 + 短信 | `search_contacts` + `send_sms` |
| `05_location.yaml` | 位置读取 | `get_current_location` |
| `06_clipboard.yaml` | 剪贴板复制 | `copy_to_clipboard` |

### 16.5 testTag 对照表

ChatScreen 和 SettingsScreen 已添加 Compose `testTag`，Maestro 通过 `id` 引用：

| testTag | 页面 | 元素 |
| --- | --- | --- |
| `chat_input` | ChatScreen | 消息输入框 |
| `send_button` | ChatScreen | 发送按钮 |
| `settings_button` | ChatScreen | 设置按钮 |
| `message_list` | ChatScreen | 消息列表 |
| `settings_api_key` | SettingsScreen | API Key 输入框 |
| `settings_base_url` | SettingsScreen | Base URL 输入框 |
| `settings_model` | SettingsScreen | Model ID 输入框 |
| `settings_save` | SettingsScreen | 保存按钮 |

### 16.6 AI 断言说明

Maestro 的 `assertWithAI` 使用视觉模型对屏幕截图做语义判断，适合 LLM 输出不确定的场景。如果未配置 Maestro Cloud AI 后端，可将 `assertWithAI` 降级为 `assertVisible`。

---

## 附录 A：测试相关文件索引

### 原有测试

| 文件 | 说明 |
| --- | --- |
| `core/domain/src/test/.../planner/PlanJsonParserTest.kt` | Plan JSON 解析测试 |
| `core/domain/src/test/.../planner/LlmJsonPlannerPayloadTest.kt` | Planner 请求体构造测试 |
| `core/domain/src/test/.../agent/InlineToolCallParserTest.kt` | 内联工具调用解析测试 |
| `core/domain/src/test/.../skill/BundledSkillJsonParserTest.kt` | Bundled JSON 技能解析测试 |
| `core/domain/src/test/.../skill/SmartContactActionSkillTest.kt` | 智能联系人技能触发测试 |
| `core/domain/src/test/.../skill/SkillSelectorBrowserSkillTest.kt` | 浏览器技能选择测试 |
| `core/data/src/test/.../settings/UserSettingsResolutionTest.kt` | 设置解析回退测试 |

### 通用场景框架测试

| 文件 | 说明 |
| --- | --- |
| `core/domain/src/test/.../skill/EnhancedSkillParserTest.kt` | 增强字段解析 + 原子/场景共注册 + 技能复用 |
| `core/domain/src/test/.../tools/CallServiceToolTest.kt` | call_service 工具参数校验 + 服务调用 |
| `core/domain/src/test/.../subtask/SubtaskExecutorTest.kt` | 共享事实发布/读取/覆盖 |
| `core/bridge/src/test/.../impl/StubServiceGatewayTest.kt` | 服务注册/调用/拒绝 |

### 技能 JSON 文件

| 文件 | 类型 |
| --- | --- |
| `core/data/src/main/assets/skills/bundled/emergency_response.json` | 原子技能 |
| `core/data/src/main/assets/skills/bundled/insurance_claim.json` | 原子技能 |
| `core/data/src/main/assets/skills/bundled/tow_and_repair.json` | 原子技能 |
| `core/data/src/main/assets/skills/bundled/trip_replan.json` | 原子技能 |
| `core/data/src/main/assets/skills/bundled/accommodation.json` | 原子技能 |
| `core/data/src/main/assets/skills/bundled/dining.json` | 原子技能 |
| `core/data/src/main/assets/skills/bundled/flight_booking.json` | 原子技能 |
| `core/data/src/main/assets/skills/bundled/visa_preparation.json` | 原子技能 |
| `core/data/src/main/assets/skills/scenarios/road_accident_orchestration.json` | 场景技能 |
| `core/data/src/main/assets/skills/scenarios/travel_abroad.json` | 场景技能 |

### 服务配置文件

| 文件 | 说明 |
| --- | --- |
| `core/data/src/main/assets/services/tesla_fleet.json` | Tesla Fleet API |
| `core/data/src/main/assets/services/geico.json` | Geico 保险 |
| `core/data/src/main/assets/services/aaa_roadside.json` | AAA 道路救援 |
| `core/data/src/main/assets/services/ctrip.json` | 携程旅行 |
| `core/data/src/main/assets/services/opentable.json` | OpenTable 餐厅 |
| `core/data/src/main/assets/services/marriott.json` | Marriott 酒店 |

### 框架源码文件

| 文件 | 说明 |
| --- | --- |
| `core/bridge/.../ServiceGateway.kt` | 统一服务网关接口 |
| `core/bridge/.../EventSource.kt` | 事件源接口 |
| `core/bridge/.../impl/HttpServiceGateway.kt` | HTTP 服务网关实现 |
| `core/bridge/.../impl/StubServiceGateway.kt` | Stub 服务网关（测试用） |
| `core/bridge/.../impl/TeslaEventSource.kt` | Tesla 碰撞事件源 |
| `core/bridge/.../impl/CalendarEventSource.kt` | 日历事件源 |
| `core/bridge/.../impl/HealthEventSource.kt` | 健康事件源 |
| `core/bridge/.../impl/SmartHomeEventSource.kt` | 智能家居事件源 |
| `core/bridge/.../impl/GeofenceEventSource.kt` | 地理围栏事件源 |
| `core/domain/.../subtask/SubtaskExecutor.kt` | 子任务执行器 |
| `core/domain/.../event/EventRouter.kt` | 事件路由器 |
| `core/domain/.../profile/UserProfileStore.kt` | 用户数据接口 |
| `core/domain/.../skill/SkillManager.kt` | 技能安装/卸载管理 |
| `core/domain/.../service/ServiceManager.kt` | 服务安装/授权管理 |
| `core/domain/.../tools/CallServiceTool.kt` | call_service 工具 |
| `core/domain/.../tools/CreatePlanTool.kt` | create_plan 工具（Plan Mode） |
| `core/domain/.../agent/PlanManager.kt` | 计划状态机管理 |
| `core/domain/.../tools/SpawnSubtaskTool.kt` | spawn_subtask 工具 |
| `core/domain/.../tools/CheckSubtaskTool.kt` | check_subtask 工具 |
| `core/domain/.../tools/ReadUserProfileTool.kt` | read_user_profile 工具 |
| `core/data/.../profile/UserProfileStoreImpl.kt` | 用户数据加密存储实现 |

### 方案 C 集成测试文件

| 文件 | 说明 |
| --- | --- |
| `core/domain/src/test/.../testdoubles/RecordingBridges.kt` | 所有 Recording Bridge 实现 |
| `core/domain/src/test/.../testdoubles/RecordingDeviceCapabilityBridge.kt` | 组合型测试 Bridge + Stub |
| `core/domain/src/test/.../testdoubles/TestInfra.kt` | 能力探测和前台状态测试桩 |
| `core/domain/src/test/.../tools/ToolRegistryIntegrationTest.kt` | 11 个工具的集成测试 |
| `core/domain/src/test/.../tools/ToolPolicyEngineTest.kt` | 策略引擎决策测试 |
| `core/domain/src/test/.../agent/AgentToolChainTest.kt` | Plan 到工具执行链路测试 |

### 方案 B Maestro 文件

| 文件 | 说明 |
| --- | --- |
| `maestro/config.yaml` | Maestro 全局配置 |
| `maestro/flows/01_basic_chat.yaml` | 基础聊天流程 |
| `maestro/flows/02_settings.yaml` | 设置页配置流程 |
| `maestro/flows/03_open_url.yaml` | open_url 工具触发 |
| `maestro/flows/04_smart_contact.yaml` | 多步技能流程 |
| `maestro/flows/05_location.yaml` | 位置读取流程 |
| `maestro/flows/06_clipboard.yaml` | 剪贴板复制流程 |
| `maestro/README.md` | Maestro 安装和运行指南 |

---

## 17. 虚拟 Bridge 系统详解（附录）

许多工具依赖真实设备能力或第三方 API，在模拟器上无法正常使用。项目内置了一套**配置驱动的虚拟 Bridge 系统**，为这些工具提供逼真的模拟数据，支持按 Bridge 逐一切换为真实接口。

### 17.1 设计原理

虚拟 Bridge 系统工作在 `DeviceCapabilityBridge` 接口之下，对上层的技能系统、工具系统、Planner 和 AgentRuntime **零侵入**。通过 `SwitchableDeviceCapabilityBridge` 按 Bridge 委派到真实或虚拟实现。

```
AgentRuntime -> ToolRegistry -> DeviceCapabilityBridge(接口)
                                        |
                              SwitchableDeviceCapabilityBridge
                              /                              \
              AndroidDeviceCapabilityBridge        VirtualDeviceCapabilityBridge
                  (真实 Android 实现)                    (虚拟模拟实现)
```

### 17.2 配置文件

`app/src/main/assets/virtual_bridge_config.json` 控制每个 Bridge 的模式：

```json
{
  "defaultMode": "virtual",
  "bridges": {
    "telephony": "virtual",
    "contacts": "virtual",
    "location": "virtual",
    "notifications": "virtual",
    "files": "virtual",
    "services": "virtual",
    "accessibility": "virtual",
    "media": "real",
    "browser": "real",
    "maps": "real",
    "clipboard": "real",
    "share": "real",
    "system": "real",
    "appState": "real"
  }
}
```

要切换某个 Bridge 为真实模式，将 `"virtual"` 改为 `"real"` 并重新构建即可，无需修改代码。

重新构建步骤：

```powershell
# 1. 修改配置后，重新构建 debug APK
.\gradlew.bat assembleDebug

# 2. 安装到模拟器或真机
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 或者直接通过 Android Studio 点击 Run 按钮，会自动完成构建和安装
```

配置文件打包在 APK 的 assets 中，因此修改后必须重新构建并安装才能生效。如果只是通过 Android Studio 运行（Run），IDE 会自动检测资源变更并增量构建。

### 17.3 Bridge 模式对照表

| Bridge | 默认模式 | 影响的工具 | 说明 |
| --- | --- | --- | --- |
| `TelephonyBridge` | virtual | `send_sms`, `dial_number` | SMS 发送需要真实 SIM 卡 |
| `ContactsBridge` | virtual | `search_contacts` | 需要真实通讯录 |
| `LocationBridge` | virtual | `get_current_location` | 模拟器上不可靠 |
| `NotificationBridge` | virtual | `list_notifications` | 需要通知监听服务 |
| `FileBridge` | virtual | `read_sandbox_file` | 需要预置文件 |
| `ServiceGateway` | virtual | `call_service` | 覆盖 6 个第三方服务，无真实 API |
| `AccessibilityBridge` | virtual | (辅助功能) | 已有桩实现 |
| `BrowserBridge` | real | `open_url` | 通过 Intent 打开，模拟器可用 |
| `MapsBridge` | real | `open_map` | 通过 Intent 打开，模拟器可用 |
| `ClipboardBridge` | real | `copy_to_clipboard` | 系统剪贴板，模拟器可用 |
| `ShareBridge` | real | `share_text` | 分享面板，模拟器可用 |
| `SystemBridge` | real | `open_settings`, `set_alarm` 等 | Intent 调用，模拟器可用 |
| `MediaBridge` | real | `open_camera` | 相机 Intent，模拟器可用 |
| `AppStateBridge` | real | (设备状态) | 读取设备状态，模拟器可用 |

### 17.4 虚拟模拟数据

虚拟模式下各 Bridge 返回的模拟数据：

| Bridge / 工具 | 模拟数据内容 |
| --- | --- |
| `VirtualContactsBridge` | 9 个预定义联系人（张三、李四、王五、John Smith 等），按查询关键字过滤 |
| `VirtualLocationBridge` | 固定坐标：上海外滩 (31.2304, 121.4737) |
| `VirtualTelephonyBridge` | `sendSms` 返回 `success=true, sentDirectly=true`；`dialNumber` 返回 `true` |
| `VirtualNotificationBridge` | 4 条模拟通知：微信消息、系统更新、日历提醒、支付宝快递签收 |
| `VirtualFileBridge` | 预置 `shared/incoming.txt` 和 `notes/todo.txt` 两个文件 |
| `UserProfileStore` | 预填 7 个分类：insurance（Geico 保单）、membership（AAA/Marriott）、vehicles（Tesla Model 3）、emergency_contacts、preferences、trip_plans（黄石 14 天自驾行程）、health（血型/过敏/用药） |

### 17.5 第三方服务模拟数据（VirtualServiceGateway）

`VirtualServiceGateway` 为 6 个已注册服务的每个 action 返回逼真的模拟响应：

| 服务 (serviceId) | action | 模拟响应关键字段 |
| --- | --- | --- |
| `geico` | `getPolicy` | 保单号 `GK-2024-8891726`，comprehensive，免赔额 $500，租车 $50/天 |
| `geico` | `fileClaim` | 理赔单号 `CLM-2024-0042`，状态 submitted |
| `geico` | `getClaimStatus` | 状态 under_review，理赔师 Mary Johnson |
| `geico` | `uploadEvidence` | 上传 ID `EVD-2026-0088`，已收到 |
| `tesla_fleet` | `getVehicleData` | 电量 78%，里程 12345 mi，位置坐标，软件版本 |
| `tesla_fleet` | `getCollisionReport` | 中度碰撞，后方撞击，4.2G 冲击力，安全气囊未弹出 |
| `tesla_fleet` | `getDashcamFootage` | 3 段行车记录仪视频（前/后/左） |
| `tesla_fleet` | `getLocation` | 车辆 GPS 坐标 |
| `tesla_fleet` | `flashLights` | 闪灯成功 |
| `ctrip` | `searchFlights` | 3 个航班选项（东航/国航/南航），含价格和时间 |
| `ctrip` | `bookFlight` | 订单号 `CT-FL-20260410-0078`，座位 32A |
| `ctrip` | `searchHotels` | 2 个酒店（华尔道夫/和平饭店） |
| `ctrip` | `bookHotel` | 订单号，豪华大床房，总价 ¥5,600 |
| `ctrip` | `getFlightStatus` | MU5101 准点，T1 航站楼 A12 登机口 |
| `aaa_roadside` | `requestTow` | 请求号，预计 25 分钟到达，司机信息 |
| `aaa_roadside` | `checkMembership` | 会员号 `AAA-438821907`，Plus 等级，剩余 4 次拖车 |
| `aaa_roadside` | `findServiceCenter` | 2 个附近授权维修站 |
| `aaa_roadside` | `getTowStatus` | 拖车在途中，距离 4.5 km，预计 12 分钟 |
| `opentable` | `searchRestaurants` | 3 个餐厅（M on the Bund / 南翔馒头店 / 鼎泰丰） |
| `opentable` | `getRestaurant` | 餐厅详情（地址/电话/营业时间/评分） |
| `opentable` | `checkAvailability` | 可用时段列表 |
| `opentable` | `makeReservation` | 预约确认号 `MB0891` |
| `opentable` | `cancelReservation` | 取消成功 |
| `marriott` | `searchHotels` | 3 个酒店（W 酒店 / JW 万豪 / 丽思卡尔顿） |
| `marriott` | `getHotelDetails` | 酒店详情（设施/入住时间/评分） |
| `marriott` | `checkAvailability` | 房型及积分兑换选项 |
| `marriott` | `bookRoom` | 确认号 `MAR-92847561`，获得 1680 积分 |
| `marriott` | `getBonvoyBalance` | Gold Elite，85420 积分 |

### 17.6 文件清单

| 文件路径 | 说明 |
| --- | --- |
| `app/src/main/assets/virtual_bridge_config.json` | Bridge 模式配置 |
| `core/bridge/.../virtual/VirtualBridgeManager.kt` | 配置读取与模式管理 |
| `core/bridge/.../virtual/VirtualTelephonyBridge.kt` | 虚拟电话/短信 |
| `core/bridge/.../virtual/VirtualContactsBridge.kt` | 虚拟通讯录 |
| `core/bridge/.../virtual/VirtualLocationBridge.kt` | 虚拟定位 |
| `core/bridge/.../virtual/VirtualNotificationBridge.kt` | 虚拟通知 |
| `core/bridge/.../virtual/VirtualFileBridge.kt` | 虚拟文件系统 |
| `core/bridge/.../virtual/VirtualServiceGateway.kt` | 虚拟第三方服务网关 |
| `core/bridge/.../virtual/VirtualMockData.kt` | 集中管理的模拟数据 |
| `core/bridge/.../virtual/VirtualDeviceCapabilityBridge.kt` | 全虚拟 Bridge 门面 |
| `core/bridge/.../virtual/SwitchableDeviceCapabilityBridge.kt` | 按 Bridge 可切换委派 |
| `core/data/.../virtual/VirtualDataBootstrapper.kt` | 启动时预填 UserProfileStore 测试数据 |

### 17.7 渐进式替换

当某个真实接口可用后：

1. 修改 `virtual_bridge_config.json` 中对应字段为 `"real"`
2. 重新构建
3. `SwitchableDeviceCapabilityBridge` 自动委派到真实 Android 实现

对于 `ServiceGateway` 内部的服务级别切换（如仅 Geico API 可用），可扩展 `VirtualServiceGateway` 增加服务级别的覆盖映射。

### 17.8 验证方法

虚拟模式下测试场景示例：

```
场景：查询保险保单
  输入: "查询我的 Geico 保险保单"
  预期路径 A: Agent 调用 read_user_profile(insurance) -> 返回预填的 Geico 保单数据
  预期路径 B: Agent 调用 call_service(geico, getPolicy) -> VirtualServiceGateway 返回保单模拟数据
  验证:
    ✅ 返回有意义的保单详情（保单号、保障类型、免赔额）
    ✅ 不再出现 "No data found"

场景：发送短信
  输入: "给张三发短信：明天下午开会"
  预期: Agent 调用 search_contacts(张三) -> 返回 "张三 - 13800001111"
        Agent 调用 send_sms(13800001111, 明天下午开会) -> 返回 success=true
  验证:
    ✅ 联系人查找返回结果
    ✅ 短信发送返回成功

场景：获取位置
  输入: "我在哪里"
  预期: Agent 调用 get_current_location -> 返回上海外滩坐标
  验证:
    ✅ 返回有效经纬度 (31.2304, 121.4737)
```

启动时 Logcat 中搜索 `VirtualBridgeManager` 可以看到所有 Bridge 的模式日志，确认虚拟配置是否生效。

---

### 测试后同步本地数据库

如果你每次手工测试或 Maestro 测试后都要把模拟器内的 Room 数据库同步到 Windows，本仓库提供了一个脚本：

```powershell
.\scripts\pull-mobilebot-db.ps1
```

默认行为：

- 从包名 `com.mobilebot` 导出 `databases/mobilebot.db`
- 如果存在，也一起导出 `mobilebot.db-wal` 和 `mobilebot.db-shm`
- 默认保存到仓库内 `artifacts/device-db/`

常用示例：

```powershell
# 指定设备序列号
.\scripts\pull-mobilebot-db.ps1 -Serial emulator-5554

# 指定导出目录
.\scripts\pull-mobilebot-db.ps1 -OutputDir "D:\exports\mobilebot-db"
```

如果要先确认模拟器里数据库文件是否存在，可以先执行：

```powershell
adb shell run-as com.mobilebot ls databases
```
