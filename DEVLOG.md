# 开发日志：宠物洗护服务 Skill 适配与功能增强

## 概述

基于 `mobile-wash-dog-skill-plan.md` 的需求，为 mobileclawDemo 项目适配了宠物洗护服务 Skill，并在测试过程中修复了多个 Bug、增强了多项功能。

---

## 1. Skill 适配与创建

### 新增文件

| 文件 | 说明 |
|------|------|
| `core/data/src/main/assets/skills/md/arrange-pet-store-service/SKILL.md` | 主 Skill 定义，包含洗护预约、商品购买、改约、取消、追加服务、修改运力、特殊情况自动处理等完整流程 |
| `core/data/src/main/assets/skills/md/arrange-pet-store-service/references/booking-lifecycle-hooks.md` | 预约生命周期 Hooks（booking_created/modified/cancelled, service_completed） |
| `core/data/src/main/assets/skills/md/arrange-pet-store-service/references/order-lifecycle-hooks.md` | 订单生命周期 Hooks（order_created/completed/cancelled, task_error） |
| `core/data/src/main/assets/skills/md/arrange-pet-store-service/references/decision-nodes.md` | 决策节点树：意图识别、先查后问、门店选择、档期冲突、库存、专车、取消/改约、特殊情况自动触发 |
| `core/data/src/main/assets/skills/md/arrange-pet-store-service/references/mock-pet-store-data.md` | Mock 数据参考文档 |
| `core/data/src/main/assets/skills/md/arrange-pet-store-service/references/open-world-interaction-rules.md` | 开放世界交互规则 |
| `core/data/src/main/assets/skills/md/arrange-pet-store-service/references/service-exception-handbook.md` | 服务异常处理手册 |
| `core/data/src/main/assets/skills/md/arrange-pet-store-service/references/ui-log-template.md` | UI 日志模板 |
| `core/data/src/main/assets/skills/md/arrange-pet-store-service/references/user-confirmation-template.md` | 用户确认模板 |
| `core/data/src/main/assets/services/pet_store_mock.json` | 宠物门店 Mock 服务配置（18 个 action） |
| `core/data/src/main/assets/services/pet_transport_mock.json` | 宠物专车 Mock 服务配置 |

### 修改文件

| 文件 | 改动 |
|------|------|
| `core/domain/src/main/java/com/mobilebot/domain/skill/Skill.kt` | SkillManifest 新增 `runtimeContext` 字段 |
| `core/domain/src/main/java/com/mobilebot/domain/skill/SkillMdParser.kt` | 解析 YAML frontmatter 中的 `runtime_context` 字段 |
| `core/domain/src/main/java/com/mobilebot/domain/skill/SkillExecutor.kt` | 新增 `buildRuntimeContext()` 方法，注入当前设备时间和 30 分钟提前量约束 |
| `core/domain/src/main/java/com/mobilebot/domain/DomainModule.kt` | 注册 `CreateCalendarEventTool`、`QueryCalendarTool`、`CreateNotificationTool` 等工具 |
| `core/domain/src/main/java/com/mobilebot/domain/tools/RememberFactTool.kt` | 新增：持久化用户事实到本地数据库 |
| `core/domain/src/main/java/com/mobilebot/domain/tools/RecallFactsTool.kt` | 新增：从本地数据库检索已记住的事实 |

---

## 2. Bug 修复

### 2.1 宠物信息重复询问

**问题**：每次对话 agent 都重新询问宠物名字、品种、地址等信息，即使之前已经提供过。

**根因**：`VirtualServiceGateway` 用 `ConcurrentHashMap` 存储宠物档案和地址，数据在内存中，app 重启即丢失。

**修复**：
- `VirtualServiceGateway` 注入 `@ApplicationContext`，改用 `SharedPreferences` + JSON 序列化持久化宠物档案和地址数据
- SKILL.md 新增"先查后问"强制前置步骤：识别意图后必须先调用 `list_pet_profiles`、`list_user_addresses`、`recall_facts` 查询已有信息
- decision-nodes.md 新增"先查后问决策"树

### 2.2 14 天后洗澡日历提醒未创建

**问题**：确认预约后，agent 没有自动在 14 天后创建下次洗澡提醒日历事件。

**根因**：
1. `virtual_bridge_config.json` 中 `"system": "real"`，导致 `SwitchableDeviceCapabilityBridge` 使用 `AndroidSystemBridge`，而 `AndroidSystemBridge` 没有实现 `createCalendarEvent` 和 `queryCalendarEvents`（默认返回 `false`/`null`）
2. `AndroidManifest.xml` 缺少 `WRITE_CALENDAR` 权限

**修复**：
- `virtual_bridge_config.json` 中 `"system"` 改为 `"virtual"`，使模拟器使用 `VirtualSystemBridgeWithCalendar`
- `AndroidSystemBridge` 新增 `createCalendarEvent`（通过 `CalendarContract.Events` 写入系统日历）和 `queryCalendarEvents`（查询系统日历）
- `AndroidManifest.xml` 新增 `android.permission.WRITE_CALENDAR` 权限
- `CreateCalendarEventTool` 新增返回值检查，失败时返回 `ok=false`

### 2.3 预约时间 30 分钟提前量未生效

**问题**：SKILL.md 中写了"最早只能推荐当前时间 30 分钟之后的档期"，但 agent 仍推荐了不符合提前量的时间。

**根因**：只有 SKILL.md 文字规则，服务端和 Runtime Context 都没有真正执行过滤。

**修复**：
- `SkillExecutor.buildRuntimeContext()` 新增 `Minimum booking time (current + 30min)` 字段和硬约束说明
- `VirtualMockData.buildAvailabilityResponse()` 新增当前时间过滤：查今天档期时自动过滤掉当前时间+30分钟之前的 slot，返回 `filterNote` 说明过滤数量

### 2.4 重启聊天后宠物信息丢失

**问题**：重启 app 或新开聊天后，之前保存的宠物档案、地址、日历事件全部丢失。

**根因**：所有用户个人数据存在 JVM 内存中（`ConcurrentHashMap`/`mutableListOf`），进程结束即丢失。

**修复**：
- `VirtualServiceGateway`：宠物档案和地址改用 `SharedPreferences` 持久化
- `VirtualSystemBridgeWithCalendar`：日历事件改用 `SharedPreferences` 持久化，init 时从磁盘加载，createCalendarEvent 后写磁盘
- `VirtualDeviceCapabilityBridge` 构造函数注入 `@ApplicationContext` 并传给 `VirtualSystemBridgeWithCalendar`

### 2.5 Windows 构建问题

**问题**：项目路径包含非 ASCII 字符（中文），Gradle 构建报错。

**修复**：`gradle.properties` 新增 `android.overridePathCheck=true`，并降低 JVM 内存配置以适应 Windows 环境。

---

## 3. 功能增强

### 3.1 取消/修改订单与专车

**新增流程**：
- 取消洗护预约：`cancel_booking` + `cancel_transport` + 清除日历
- 取消商品订单：`cancel_order` + `cancel_transport`
- 取消宠物专车（保留预约）：`cancel_transport` only
- 全部取消（一键清理）：`cancel_booking` → `cancel_transport` → `cancel_order`
- 改洗护预约：`modify_booking` + `modify_transport` + 更新日历
- 改商品订单：`modify_order` + `modify_transport`
- 改专车时间（不改预约）：`modify_transport` only

### 3.2 特殊情况自动处理

| 特殊情况 | 自动动作 |
|---------|---------|
| 门店闭店 | 自动改约，推荐替代门店 |
| 宠物专车不可用 | 提示替代方案（自行前往/改期） |
| 预约时间冲突 | 自动建议改约 |
| 宠物应激/健康异常 | 自动暂停，建议改期或取消 |
| 极端天气/交通异常 | 自动调整接送时间 |
| 商品库存变化 | 自动通知，推荐替代 |
| 用户要求全部取消 | 一键清理所有预约/专车/订单 |

### 3.3 异常处理增强

新增 6 种取消/修改相关异常处理：
- 取消预约失败、修改预约失败
- 取消专车失败、修改专车时间失败
- 取消订单失败、级联取消异常

---

## 4. 文件变更清单

### 修改的文件

```
app/src/main/AndroidManifest.xml                                          (+1 WRITE_CALENDAR)
app/src/main/assets/virtual_bridge_config.json                            (system: real → virtual)
core/bridge/src/main/java/.../impl/AndroidSystemBridge.kt                 (+115 createCalendarEvent/queryCalendarEvents)
core/bridge/src/main/java/.../virtual/VirtualDeviceCapabilityBridge.kt    (+context injection, calendar persistence)
core/bridge/src/main/java/.../virtual/VirtualMockData.kt                  (+30min advance filtering)
core/bridge/src/main/java/.../virtual/VirtualServiceGateway.kt            (ConcurrentHashMap → SharedPreferences)
core/domain/src/main/java/.../domain/skill/Skill.kt                       (+runtimeContext field)
core/domain/src/main/java/.../domain/skill/SkillExecutor.kt               (+buildRuntimeContext with time constraints)
core/domain/src/main/java/.../domain/skill/SkillMdParser.kt               (+runtime_context parsing)
core/domain/src/main/java/.../domain/tools/CreateCalendarEventTool.kt     (+success check)
core/domain/src/main/java/.../domain/DomainModule.kt                      (+tool registrations)
gradle.properties                                                         (+overridePathCheck, memory tuning)
```

### 新增的文件

```
core/data/src/main/assets/services/pet_store_mock.json
core/data/src/main/assets/services/pet_transport_mock.json
core/data/src/main/assets/skills/md/arrange-pet-store-service/SKILL.md
core/data/src/main/assets/skills/md/arrange-pet-store-service/references/booking-lifecycle-hooks.md
core/data/src/main/assets/skills/md/arrange-pet-store-service/references/decision-nodes.md
core/data/src/main/assets/skills/md/arrange-pet-store-service/references/mock-pet-store-data.md
core/data/src/main/assets/skills/md/arrange-pet-store-service/references/open-world-interaction-rules.md
core/data/src/main/assets/skills/md/arrange-pet-store-service/references/order-lifecycle-hooks.md
core/data/src/main/assets/skills/md/arrange-pet-store-service/references/service-exception-handbook.md
core/data/src/main/assets/skills/md/arrange-pet-store-service/references/ui-log-template.md
core/data/src/main/assets/skills/md/arrange-pet-store-service/references/user-confirmation-template.md
core/domain/src/main/java/.../domain/tools/RememberFactTool.kt
core/domain/src/main/java/.../domain/tools/RecallFactsTool.kt
```
