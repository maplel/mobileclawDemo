# MobileBot 真实 Android / 第三方接口准备指南

本文档面向测试团队、接入团队和开发团队，用于回答三个问题：

1. 当前测试和业务主路径到底需要准备哪些真实 Android 接口与第三方服务接口。
2. 这些接口在项目中的接入位置、方法签名和调用链路是什么。
3. 如何把现有虚拟模拟实现逐步替换成真实 Android / HTTP 实现。

本文档与 `TESTING.md` 配套使用：

- `TESTING.md` 负责告诉测试团队如何执行测试和记录问题。
- 本文档负责告诉接入团队需要准备什么接口、优先级如何划分、替换步骤如何落地。

---

## 1. 架构总览

项目当前通过 `DeviceCapabilityBridge` 统一封装 Android 能力和第三方服务能力，上层工具、Planner、技能系统和 `AgentRuntime` 都只依赖这个统一接口，不直接依赖具体实现。

当前接入链路如下：

```text
AgentRuntime / ToolRegistry / Tools
                |
                v
      DeviceCapabilityBridge
                |
                v
  SwitchableDeviceCapabilityBridge
      |                      |
      v                      v
AndroidDeviceCapabilityBridge   VirtualDeviceCapabilityBridge
      |                      |
      v                      v
真实 Android / HTTP 实现        虚拟 Bridge / VirtualMockData
```

### 关键文件

| 文件 | 作用 |
| --- | --- |
| `core/bridge/src/main/java/com/mobilebot/bridge/DeviceCapabilityBridge.kt` | 所有能力的统一门面接口 |
| `core/bridge/src/main/java/com/mobilebot/bridge/impl/AndroidDeviceCapabilityBridge.kt` | 真实 Android 能力组合实现 |
| `core/bridge/src/main/java/com/mobilebot/bridge/virtual/VirtualDeviceCapabilityBridge.kt` | 虚拟能力组合实现 |
| `core/bridge/src/main/java/com/mobilebot/bridge/virtual/SwitchableDeviceCapabilityBridge.kt` | 按 bridge 独立选择 real / virtual |
| `core/bridge/src/main/java/com/mobilebot/bridge/di/BridgeModule.kt` | Hilt 注入入口，决定最终绑定 real 还是 virtual |
| `app/src/main/assets/virtual_bridge_config.json` | 每个 bridge 的配置开关 |
| `core/bridge/src/main/java/com/mobilebot/bridge/virtual/VirtualMockData.kt` | 模拟服务与模拟用户数据 |
| `core/bridge/src/main/java/com/mobilebot/bridge/virtual/VirtualServiceGateway.kt` | 虚拟第三方服务网关 |
| `core/bridge/src/main/java/com/mobilebot/bridge/impl/HttpServiceGateway.kt` | 真实第三方服务网关占位实现 |
| `core/data/src/main/java/com/mobilebot/data/virtual/VirtualDataBootstrapper.kt` | 启动时向 `UserProfileStore` 注入虚拟测试数据 |

---

## 2. 当前默认模式与替换原则

`app/src/main/assets/virtual_bridge_config.json` 当前的默认配置是：

- `telephony`、`contacts`、`location`、`notifications`、`files`、`services`、`accessibility` 为 `virtual`
- `media`、`browser`、`maps`、`clipboard`、`share`、`system`、`appState` 为 `real`

### 替换原则

1. 先保证测试团队有稳定的虚拟链路可回归。
2. 再按 bridge 独立替换成真实实现，不要求一次性全部切完。
3. 替换某个 bridge 时，不影响其他 bridge 继续使用 virtual。
4. 每替换一个 bridge，都必须同步补齐：
   - 权限准备
   - 账号 / token / API key
   - 测试数据
   - 成功与失败路径
   - 测试记录模板中的环境说明

---

## 3. 第一优先级：当前必须准备的真实 Android 接口

第一优先级的目标是支撑当前已存在的测试路径和主要业务场景，尤其是 `TESTING.md` 中已经列出的工具测试、技能测试和 E2E 场景。

### 3.1 `TelephonyBridge`

**接口文件**：`core/bridge/src/main/java/com/mobilebot/bridge/TelephonyBridge.kt`

| 方法 | 用途 | 当前真实实现 |
| --- | --- | --- |
| `dialNumber(phoneNumber: String): Boolean` | 打开拨号盘，不自动拨出 | `AndroidTelephonyBridge` |
| `openSmsComposer(phoneNumber: String, message: String): Boolean` | 打开短信编辑器并预填内容 | `AndroidTelephonyBridge` |
| `sendSms(phoneNumber: String, message: String): SmsSendResult` | 有权限时直接发短信，无权限时回退草稿模式 | `AndroidTelephonyBridge` |

**测试团队要准备的内容**

- 真机
- 可用 SIM 卡
- `SEND_SMS` 权限验证方案
- 无权限场景验证方案
- 可收发短信的测试号码

**接入说明**

- 当前真实实现已经接到 Android `Intent` 和 `SmsManager`。
- 重点不是补方法，而是保证真机权限、号码和异常场景可测试。
- 真实回归要覆盖直接发送和退化到短信草稿两条路径。

### 3.2 `ContactsBridge`

**接口文件**：`core/bridge/src/main/java/com/mobilebot/bridge/ContactsBridge.kt`

| 方法 | 用途 | 当前真实实现 |
| --- | --- | --- |
| `searchContacts(query: String, limit: Int = 10): List<String>` | 从系统通讯录中搜索联系人 | `AndroidContactsBridge` |

**测试团队要准备的内容**

- 真机或带联系人数据的测试设备
- 至少 3-5 个可检索联系人
- 已授权和未授权两种权限状态

**接入说明**

- 当前真实实现已经通过 `ContactsContract` 查询联系人。
- 返回值格式为 `name|number`，测试团队在验证时要注意联系人姓名和号码是否都正确返回。

### 3.3 `LocationBridge`

**接口文件**：`core/bridge/src/main/java/com/mobilebot/bridge/LocationBridge.kt`

| 方法 | 用途 | 当前真实实现 |
| --- | --- | --- |
| `getCoarseLocation(): LocationResult` | 获取粗略位置 | `AndroidLocationBridge` |
| `getFineLocation(): LocationResult` | 获取精确位置 | `AndroidLocationBridge` |

**测试团队要准备的内容**

- 真机优先
- 系统定位权限
- 可控的测试地点或模拟定位方案
- “没有缓存位置”“未授权”“有授权但无信号”的异常验证方案

**接入说明**

- 当前实现依赖 `LocationManager.getLastKnownLocation`，如果系统还没有缓存位置，会返回 `No cached location yet`。
- 因此真实回归时，先用地图类 App 激活定位能力，再进入 MobileBot 测试。

### 3.4 `NotificationBridge`

**接口文件**：`core/bridge/src/main/java/com/mobilebot/bridge/NotificationModels.kt`

| 方法 | 用途 | 当前真实实现 |
| --- | --- | --- |
| `listRecent(limit: Int = 20): List<NotificationItem>` | 获取最近通知列表 | `CachingNotificationBridge` |
| `findByPackage(packageName: String, limit: Int = 20): List<NotificationItem>` | 按包名过滤通知 | `CachingNotificationBridge` |

**测试团队要准备的内容**

- 真机
- 系统通知访问授权
- 至少一个可主动发送通知的第三方 App
- 一套验证通知写入缓存的操作步骤

**接入说明**

- 当前 `CachingNotificationBridge` 只是读取缓存，真正的数据来源取决于通知监听链路是否正确把通知写入 `NotificationHistoryStore`。
- 因此测试时必须把“通知监听授权”本身视为前置条件的一部分。

### 3.5 `FileBridge`

**接口文件**：`core/bridge/src/main/java/com/mobilebot/bridge/FileBridge.kt`

| 方法 | 用途 | 当前真实实现 |
| --- | --- | --- |
| `readWorkspaceText(relativePath: String, maxChars: Int = 32000): WorkspaceFileRead` | 读取 `filesDir/workspace` 内的文本文件 | `AndroidFileBridge` |

**测试团队要准备的内容**

- 能够通过 `ACTION_SEND` 或调试方式向 App 注入文件
- 明确 `shared/incoming.txt` 等约定路径
- 文件不存在、超长文本、路径非法三类异常验证

**接入说明**

- 当前真实实现只允许读取 App 内部 `workspace` 目录下的文件，自动拦截越权路径。
- 测试团队需要重点验证分享导入链路和读取链路是否一致。

### 3.6 `ServiceGateway`

**接口文件**：`core/bridge/src/main/java/com/mobilebot/bridge/ServiceGateway.kt`

| 方法 | 用途 | 当前真实实现 |
| --- | --- | --- |
| `call(request: ServiceRequest): ServiceResponse` | 调第三方服务 action | `HttpServiceGateway` |
| `listAvailableServices(): List<ServiceDescriptor>` | 查看已注册服务 | `HttpServiceGateway` / `VirtualServiceGateway` |
| `isServiceAuthorized(serviceId: String): Boolean` | 判断服务是否可调用 | `HttpServiceGateway` / `VirtualServiceGateway` |
| `registerService(descriptor: ServiceDescriptor)` | 启动时注册服务 JSON 描述 | `HttpServiceGateway` / `VirtualServiceGateway` |

**测试团队要准备的内容**

- 每个第三方服务的账号、授权方式、测试账号或沙箱环境
- 每个 action 的请求参数样例
- 正常、未授权、未知 action、返回空数据、第三方错误码路径
- 服务级别的测试数据与结果核对标准

**重要现状**

- `VirtualServiceGateway` 已经可以返回稳定模拟数据。
- `HttpServiceGateway` 目前还没有真正把 JSON 描述转换成实际 HTTP 请求，只是保留了真实接入路径和日志占位。
- 也就是说：`services` 切到 `real` 之前，接入团队必须先完成 `HttpServiceGateway` 的真实 HTTP 打通。

---

## 4. 第一优先级：当前必须准备的第三方服务列表

以下服务已经出现在当前仓库的技能、场景或测试链路中，属于现阶段必须优先接入的真实第三方服务。

### 4.1 `geico`

**配置文件**：`core/data/src/main/assets/services/geico.json`

| action | method | path | 说明 |
| --- | --- | --- | --- |
| `getPolicy` | `GET` | `/policies/{policyId}` | 查询保单 |
| `fileClaim` | `POST` | `/claims` | 发起理赔，需要用户确认 |
| `getClaimStatus` | `GET` | `/claims/{claimId}` | 查询理赔状态 |
| `uploadEvidence` | `POST` | `/claims/{claimId}/evidence` | 上传证据材料 |

**接入准备**

- OAuth2 授权
- 测试保单号
- 理赔测试账号
- 文件上传样例

### 4.2 `tesla_fleet`

**配置文件**：`core/data/src/main/assets/services/tesla_fleet.json`

| action | method | path | 说明 |
| --- | --- | --- | --- |
| `getVehicleData` | `GET` | `/api/1/vehicles/{vehicleId}/vehicle_data` | 查询车辆综合信息 |
| `getCollisionReport` | `GET` | `/api/1/vehicles/{vehicleId}/collision_report` | 查询碰撞信息 |
| `getDashcamFootage` | `GET` | `/api/1/vehicles/{vehicleId}/dashcam/clips` | 查询行车记录片段 |
| `getLocation` | `GET` | `/api/1/vehicles/{vehicleId}/location` | 查询车辆位置 |
| `flashLights` | `POST` | `/api/1/vehicles/{vehicleId}/command/flash_lights` | 闪灯定位车辆 |

**接入准备**

- OAuth2 授权
- 测试车辆 ID
- 车辆位置、碰撞报告、dashcam 等测试数据

### 4.3 `aaa_roadside`

**配置文件**：`core/data/src/main/assets/services/aaa_roadside.json`

| action | method | path | 说明 |
| --- | --- | --- | --- |
| `requestTow` | `POST` | `/roadside/tow` | 叫拖车，需要用户确认 |
| `checkMembership` | `GET` | `/members/{memberId}/status` | 查会员状态 |
| `findServiceCenter` | `GET` | `/facilities/search` | 搜索维修点 |
| `getTowStatus` | `GET` | `/roadside/tow/{requestId}/status` | 查询拖车状态 |

**接入准备**

- API key
- 测试会员号
- 拖车请求和状态回调样例

### 4.4 `ctrip`

**配置文件**：`core/data/src/main/assets/services/ctrip.json`

| action | method | path | 说明 |
| --- | --- | --- | --- |
| `searchFlights` | `GET` | `/flights/search` | 搜索机票 |
| `bookFlight` | `POST` | `/flights/book` | 预订机票，需要用户确认 |
| `searchHotels` | `GET` | `/hotels/search` | 搜索酒店 |
| `bookHotel` | `POST` | `/hotels/book` | 预订酒店，需要用户确认 |
| `getFlightStatus` | `GET` | `/flights/{flightNumber}/status` | 查询航班状态 |

**接入准备**

- API key
- 机票、酒店搜索参数样例
- 下单测试账号

### 4.5 `opentable`

**配置文件**：`core/data/src/main/assets/services/opentable.json`

| action | method | path | 说明 |
| --- | --- | --- | --- |
| `searchRestaurants` | `GET` | `/restaurants/search` | 搜索餐厅 |
| `getRestaurant` | `GET` | `/restaurants/{restaurantId}` | 查询餐厅详情 |
| `checkAvailability` | `GET` | `/restaurants/{restaurantId}/availability` | 查询可订时段 |
| `makeReservation` | `POST` | `/reservations` | 预订餐厅，需要用户确认 |
| `cancelReservation` | `DELETE` | `/reservations/{reservationId}` | 取消预订 |

**接入准备**

- API key
- 餐厅搜索参数样例
- 可订时段与下单样例

### 4.6 `marriott`

**配置文件**：`core/data/src/main/assets/services/marriott.json`

| action | method | path | 说明 |
| --- | --- | --- | --- |
| `searchHotels` | `GET` | `/hotels/search` | 搜索酒店 |
| `getHotelDetails` | `GET` | `/hotels/{hotelId}` | 查询酒店详情 |
| `checkAvailability` | `GET` | `/hotels/{hotelId}/availability` | 查房态 |
| `bookRoom` | `POST` | `/reservations` | 订房，需要用户确认 |
| `getBonvoyBalance` | `GET` | `/loyalty/{memberId}/balance` | 查询积分余额 |

**接入准备**

- OAuth2 授权
- 测试会员号
- 房态、预订和积分样例

---

## 5. 第二优先级：面向未来衣食住行扩展的接口池

这部分不是当前测试交付的阻塞项，但建议从现在开始预留设计，以便后续覆盖更完整的用户生活场景。

### 5.1 已经出现在仓库中的第二优先级服务

#### `visa_checker`

**配置文件**：`core/data/src/main/assets/services/visa_checker.json`

| action | method | path | 说明 |
| --- | --- | --- | --- |
| `checkRequirements` | `GET` | `/requirements` | 查询签证要求 |
| `checkStatus` | `GET` | `/applications/{applicationId}/status` | 查询签证办理状态 |

#### `hotel_search`

**配置文件**：`core/data/src/main/assets/services/hotel_search.json`

| action | method | path | 说明 |
| --- | --- | --- | --- |
| `search` | `GET` | `/hotels/search` | 搜索酒店 |
| `getDetails` | `GET` | `/hotels/{hotelId}` | 查询酒店详情 |
| `book` | `POST` | `/hotels/book` | 预订酒店，需要用户确认 |

### 5.2 建议预留的未来服务方向

以下接口方向可作为后续扩展池，不要求本轮就完成，但建议在账号、权限、数据模型和错误码方面提前规划：

- 衣：服饰、洗衣、干洗、行李寄送
- 食：外卖、生鲜、餐厅排队、营养建议
- 住：酒店、公寓、民宿、保洁、维修
- 行：打车、公交、地铁、租车、停车、加油、充电
- 吃喝拉撒：药店、医院、便利店、厕所导航、宠物服务、快递代收

建议做法是继续沿用当前 `services/*.json` 的声明式方式，每个服务先定义：

- `id`
- `name`
- `category`
- `baseUrl`
- `authType`
- `actions`

---

## 6. 虚拟实现替换为真实实现的落地步骤

### 第一步：确认要替换的是哪一层

先明确当前需要替换的是下面哪种类型：

- Android 设备能力：例如通讯录、短信、位置、通知、文件
- 第三方服务能力：例如保险、拖车、酒店、餐厅、机票

### 第二步：补齐真实实现本身

对于 Android 能力：

- 确认对应 bridge 已有真实实现，或补齐真实实现类
- 核对权限申请、异常处理和降级逻辑
- 在真机上准备可复现的测试数据

对于第三方服务：

- 在 `services/*.json` 中确认 action 列表、HTTP 方法和路径
- 在 `HttpServiceGateway` 中把 descriptor 真正映射为实际 HTTP 请求
- 补齐认证、请求参数、错误码和响应解析

### 第三步：切换配置

打开 `app/src/main/assets/virtual_bridge_config.json`，把目标 bridge 从 `virtual` 改成 `real`。

示例：

```json
{
  "bridges": {
    "telephony": "real",
    "contacts": "real",
    "location": "real",
    "services": "real"
  }
}
```

### 第四步：重新构建并安装

```powershell
.\gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 第五步：确认运行时生效

- 启动 App
- 在 Logcat 搜索 `VirtualBridgeManager`
- 确认目标 bridge 已显示为 `REAL`
- 如果仍显示为 `VIRTUAL`，优先检查是否忘记重新安装 APK

### 第六步：执行真实回归

必须至少验证：

- 正常路径
- 权限拒绝 / 未授权路径
- 空数据路径
- 第三方接口失败路径
- 返回格式是否满足工具和 UI 渲染要求

### 第七步：保留可回退的虚拟能力

即使真实接口已经接入，也建议保留虚拟能力，原因有两点：

1. 便于模拟器回归和 CI / 自动化使用。
2. 真实第三方接口不可用时，可以快速切回虚拟模式进行问题隔离。

---

## 7. 服务接入的调用链路

第三方服务从声明到被工具调用的顺序如下：

1. 在 `core/data/src/main/assets/services/*.json` 中声明服务描述。
2. App 启动时，`SkillAssetLoader.loadServiceConfigs()` 读取这些 JSON 并调用 `registerService(descriptor)`。
3. 工具 `call_service` 通过 `DeviceCapabilityBridge.services` 取到当前网关。
4. 如果 `services=virtual`，则走 `VirtualServiceGateway`，从 `VirtualMockData` 返回模拟数据。
5. 如果 `services=real`，则走 `HttpServiceGateway`，由真实 HTTP 实现请求第三方服务。

### 对测试团队的意义

- 如果看到返回里有 `_virtual=true`，说明当前还在虚拟模式。
- 如果切到 `real` 后返回结构异常、数据缺失或 action 不匹配，优先检查：
  - 服务 JSON 是否已注册
  - action 名称是否一致
  - 鉴权是否完成
  - 请求参数是否映射正确

---

## 8. 当前已知缺口与风险提醒

### 8.1 `HttpServiceGateway` 还未完成真实 HTTP 打通

当前代码已经具备真实接入入口，但还没有完成从 `ServiceDescriptor` 到实际网络请求的完整逻辑。因此：

- `services=real` 不代表真实第三方服务已经可用
- 接入团队必须先补齐这个能力，再交给测试团队回归

### 8.2 `accessibility` 真实能力暂未完成

虽然配置里可以切 `accessibility`，但真实栈仍然是 `StubAccessibilityBridge`。因此：

- 当前不建议把它列入本轮真实接口准备范围
- 也不要把它作为测试团队的交付阻塞项

### 8.3 `virtual_bridge_config.json` 的 `defaultMode` 不生效

当前实现只读取 `bridges` 对象内显式配置的键。因此：

- 不能只改 `defaultMode`
- 必须对目标 bridge 单独设置 `real`

### 8.4 通知、定位、短信都高度依赖设备状态

这些能力虽然已有真实实现，但是否稳定可测取决于：

- 权限是否已授予
- 系统服务是否正常
- 测试设备是否有缓存位置、SIM 卡、通知来源

因此测试团队必须把环境准备写进每轮回归记录。

---

## 9. 推荐交付方式

为了让测试团队与接入团队配合更顺畅，建议每个真实接口接入项都按照下面的表格建一份状态表。

| 项目 | 是否已准备 | 负责人 | 测试账号/设备 | 阻塞项 | 下一步 |
| --- | --- | --- | --- | --- | --- |
| `TelephonyBridge` |  |  |  |  |  |
| `ContactsBridge` |  |  |  |  |  |
| `LocationBridge` |  |  |  |  |  |
| `NotificationBridge` |  |  |  |  |  |
| `FileBridge` |  |  |  |  |  |
| `geico` |  |  |  |  |  |
| `tesla_fleet` |  |  |  |  |  |
| `aaa_roadside` |  |  |  |  |  |
| `ctrip` |  |  |  |  |  |
| `opentable` |  |  |  |  |  |
| `marriott` |  |  |  |  |  |
| `visa_checker` |  |  |  |  |  |
| `hotel_search` |  |  |  |  |  |

---

## 10. 结论

现阶段的建议是：

- 先把第一优先级接口准备完整，满足当前主路径测试与版本回归。
- 第二优先级接口作为生活服务扩展池推进，不阻塞当前交付。
- 真实第三方服务接入的核心关键点不是增加更多 JSON，而是完成 `HttpServiceGateway` 的真正 HTTP 化，以及为测试团队提供可稳定复现的账号、设备和数据。
