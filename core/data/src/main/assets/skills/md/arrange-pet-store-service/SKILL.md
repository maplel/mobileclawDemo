---
name: arrange-pet-store-service
description: >-
  编排宠物店洗护预约、商品购买、改约取消、宠物专车和同城配送。
  当用户提到猫狗洗澡、修毛、刷牙、买狗粮猫粮猫砂、预约宠物服务、改约、接送宠物、配送宠物用品时调用。
  不用于泛泛宠物知识问答。
  【重要】处理任何服务请求前，必须先调用 list_pet_profiles 和 list_user_addresses 查询已有信息，基于已有信息与用户确认，禁止从零开始询问。
category: life-service
version: "0.1.0"
allowed-tools:
  - use_skill
  - call_service
  - get_current_location
  - query_calendar
  - create_calendar_event
  - create_notification
  - recall_facts
  - remember_fact
  - deep_link_app
context: inline
effort: high
risk: medium
requires:
  connectivity: true
  permissions:
    - android.permission.ACCESS_FINE_LOCATION
composes-skills:
  - schedule-management
user-confirmation-points:
  - confirm_grooming_plan
  - confirm_product_order
  - confirm_reschedule
  - confirm_cancellation
  - confirm_pet_pickup
  - confirm_delivery
required-services:
  - pet_store_mock
  - pet_transport_mock
runtime-context:
  - current-time
references:
  - references/mock-pet-store-data.md
  - references/decision-nodes.md
  - references/open-world-interaction-rules.md
  - references/service-exception-handbook.md
  - references/ui-log-template.md
  - references/user-confirmation-template.md
  - references/booking-lifecycle-hooks.md
  - references/order-lifecycle-hooks.md
---

# 宠物店服务编排 Skill

## 职责

本 Skill 是宠物店服务的主编排层，负责：

- 判断用户意图：洗护预约、商品购买、确认、改期、取消、查询、追加服务、修改运力
- 通过 `call_service(pet_store_mock, ...)` 查询门店、服务项目、商品、库存和档期
- 通过 `query_calendar` 检查用户日程冲突
- 通过 `call_service(pet_transport_mock, ...)` 安排宠物专车接送或同城配送
- 通过 `create_calendar_event` 和 `create_notification` 设置提醒
- 通过 `remember_fact` 自动记录宠物档案、洗护记录、商品库存和订单状态
- 处理中途插入的新需求

## 自动记忆与宠物档案

在处理宠物服务前，优先使用 `recall_facts` 查询已有宠物档案，避免重复询问用户已经说过的信息。

常用查询：

```
recall_facts(query="pet", limit=10)
recall_facts(query="dog_food", limit=5)
recall_facts(query="last_grooming", limit=5)
```

当用户或服务结果中出现宠物相关信息时，必须自动写入两处：

1. 通过 `call_service(pet_store_mock, save_pet_profile/save_user_address, ...)` 写入宠物 mock 服务。
2. 通过 `remember_fact` 写入本地记忆数据库。

不要询问用户是否记录，除非信息明显敏感、冲突或无法判断。

### 需要自动记录的信息

- 宠物基础信息：名字、类型（dog/cat）、品种、性别、年龄、体重、性格、健康注意事项。
- 洗护信息：上次洗澡日期、预约日期、服务项目、门店、预约编号、是否需要宠物专车、接送时间。
- 地址信息：宠物当前所在地址、默认接宠地址、配送地址、地址标签（如“家”“公司”）。
- 商品与库存：狗粮/猫粮/猫砂是否快没了、刚购买的商品、规格、数量、订单编号、预计送达时间、补货周期。
- 偏好与限制：常用门店、是否偏好上门接送、是否怕吹风机、是否对刷牙/修毛敏感。

### 记忆命名规范

使用稳定 key，namespace 固定为 `pets`、`pet_inventory` 或 `pet_addresses`：

```
remember_fact(namespace="pets", key="pet.{宠物名}.name", value="{宠物名}")
remember_fact(namespace="pets", key="pet.{宠物名}.type", value="dog")
remember_fact(namespace="pets", key="pet.{宠物名}.last_grooming_at", value="2026-05-04T16:00:00+08:00")
remember_fact(namespace="pets", key="pet.{宠物名}.last_grooming_service", value="狗狗洗澡 + 刷牙 @ 泡泡宠物生活馆 南山店")
remember_fact(namespace="pets", key="pet.{宠物名}.next_grooming_reminder_at", value="2026-05-18T16:00:00+08:00")
remember_fact(namespace="pet_addresses", key="default_pickup_address", value="深圳市南山区...")
remember_fact(namespace="pet_inventory", key="dog_food.status", value="low")
remember_fact(namespace="pet_inventory", key="dog_food.last_purchase", value="Adult dog food 10kg, quantity=1, order_id=...")
```

如果不知道宠物名，但知道宠物类型，可先用 `pet.default_dog.*` 或 `pet.default_cat.*`，等用户之后提供名字时再写入命名 key。

### 自动记忆触发点

- 用户说“我的狗叫元宝”：立即记录 `pet.元宝.name=元宝` 和 `pet.元宝.type=dog`。
- 用户说“从我家/xx小区接它”：调用 `call_service(pet_store_mock, save_user_address, ...)` 保存地址，并记录 `pet_addresses.default_pickup_address`。
- 用户说“狗粮没了/快没了”：记录 `pet_inventory.dog_food.status=low`。
- 商品订单创建成功：记录商品、规格、数量、订单编号、购买时间和预计送达时间。
- 洗护预约创建成功：记录宠物名、服务、门店、预约开始时间、预约编号、宠物专车信息，并更新 `last_grooming_at` 与 `next_grooming_reminder_at`。
- 服务完成后：确认更新 `last_grooming_completed_at`。

### 宠物 mock 保存规则

只要获得新的宠物档案、地址、库存或洗护记录，优先调用 `pet_store_mock` 的保存接口：

```
call_service(serviceId="pet_store_mock", action="save_pet_profile", params={
  pet_name, pet_type, breed, weight, health_notes, preferences, last_grooming_at, next_grooming_reminder_at
})
call_service(serviceId="pet_store_mock", action="save_user_address", params={
  label: "默认接宠地址", address, is_default: true
})
```

`pet_store_mock` 是宠物服务的业务侧档案；`remember_fact` 是助手本地记忆。两者都要写，避免下一轮对话或下次使用 skill 时丢失信息。

## 意图识别

| 用户表达 | 意图 |
| --- | --- |
| "帮我给狗洗澡" / "麒麟该洗澡了" | grooming_booking |
| "买袋狗粮" / "猫粮快没了" | product_purchase |
| "确认" / "好的" / "就这样" | confirm |
| "改个时间" / "换到明天" | reschedule |
| "别洗了" / "取消吧" | cancel |
| "看看预约状态" / "到哪了" | query |
| "加个刷牙" / "顺便修个毛" | add_service |
| "晚点送回来" / "换辆车" | modify_ride |

## ⚠️ 强制前置：先查后问

**识别到 grooming_booking 或 product_purchase 意图后，在向用户提出任何问题之前，必须先执行以下查询。禁止跳过。**

```
call_service(serviceId="pet_store_mock", action="list_pet_profiles", params={})
call_service(serviceId="pet_store_mock", action="list_user_addresses", params={})
recall_facts(query="pet name type breed address grooming", limit=10)
```

查询结果决定后续对话策略：

| 查询结果 | 对话策略 |
| --- | --- |
| 已有宠物档案 | 直接说"是给{宠物名}安排吗？"，**不要**重新问名字、品种、体型 |
| 无宠物档案 | 才询问宠物信息，获取后立即 `save_pet_profile` |
| 已有默认地址 | 在涉及接送/配送时说"使用默认地址（{地址}）还是填写新地址？"，**不要**从零问起 |
| 无地址 | 在需要时才请用户填写 |

**违反示例**（禁止）：
- 用户说"帮我给狗洗澡" → agent 直接问"您的狗叫什么名字？什么品种？"（❌ 没有先查已有档案）
- 用户说"买袋狗粮" → agent 直接问"您的狗是什么类型？"（❌ 没有先查已有档案）

**正确示例**：
- 用户说"帮我给狗洗澡" → agent 先调 `list_pet_profiles` → 返回 [{name:"元宝",type:"dog"}] → agent 说"是给元宝安排洗澡吗？"（✅）
- 用户说"买袋狗粮" → agent 先调 `list_pet_profiles` → 返回空 → agent 才问"您养的是猫还是狗？"（✅）

## 参考文档

- `references/mock-pet-store-data.md`：门店、服务、商品、库存、档期、活动 Mock 数据
- `references/decision-nodes.md`：意图识别、门店选择、档期冲突、库存不足、宠物专车、同城配送、确认节点的决策树
- `references/open-world-interaction-rules.md`：开放世界插入类型分类、处理策略、多插入优先级排序
- `references/service-exception-handbook.md`：门店闭店、档期不足、库存不足、专车不可用、配送不可用、日历权限、日程冲突等异常处理方案
- `references/ui-log-template.md`：AI Log 格式模板（查询/预约/改约/取消/下单/异常各阶段）
- `references/user-confirmation-template.md`：用户确认 UI 模板（洗护方案/商品订单/改约/追加服务/取消/日程冲突/库存不足）
- `references/booking-lifecycle-hooks.md`：预约生命周期 Hook 定义
- `references/order-lifecycle-hooks.md`：订单生命周期 Hook 定义

## Mock 数据

所有门店、服务、商品、库存、档期数据通过 `call_service(pet_store_mock, ...)` 调用。
Mock 数据定义见 `references/mock-pet-store-data.md`。

宠物专车和同城配送优先通过 `call_service(pet_transport_mock, ...)` 调用。只有用户明确要求打开真实打车或配送 App 时，才使用 `deep_link_app`。

### call_service action 白名单

`call_service` 的 `action` 必须使用服务配置中真实存在的 action 名。不要把用户意图、确认点或自然语言动词当成 action；尤其不要调用 `reserve`、`confirm`、`confirm_grooming_plan`、`confirm_pet_pickup`。

宠物门店服务 `pet_store_mock` 只能使用：

`list_locations`, `list_services`, `list_products`, `query_inventory`, `list_promotions`, `get_store_calendar`, `query_availability`, `list_user_addresses`, `save_user_address`, `list_pet_profiles`, `save_pet_profile`, `create_booking`, `modify_booking`, `cancel_booking`, `create_order`, `modify_order`, `cancel_order`, `get_status`

宠物专车服务 `pet_transport_mock` 只能使用：

`estimate_transport`, `request_transport`, `modify_transport`, `cancel_transport`, `get_transport_status`

用户回复“确认/好的/就这样”时，根据当前已展示的方案执行下一步真实 action：

- 确认洗护预约：调用 `pet_store_mock.create_booking`
- 确认宠物专车：调用 `pet_transport_mock.request_transport`
- 确认商品订单：调用 `pet_store_mock.create_order`
- 确认改约：调用 `pet_store_mock.modify_booking`，如有专车再调用 `pet_transport_mock.modify_transport`
- 确认取消：调用 `pet_store_mock.cancel_booking`，如有专车再调用 `pet_transport_mock.cancel_transport`

当前手机端没有通用的“更新/删除日历事件”和“周期性提醒”工具。如果流程需要这些能力，使用一次性 `create_notification` 或给用户明确说明降级，并在 AI Log 中记录。

## 洗护预约流程 (grooming_booking)

### 时间约束

- 使用 `Runtime Context` 中的当前设备时间解释“今天、明天、现在、马上、稍后、下午、晚上”等相对时间。
- 洗护预约、宠物专车接宠、送回时间都不能早于当前设备时间。
- 当用户要求“现在、马上、尽快、今天”但没有指定具体时间时，最早只能推荐当前设备时间 **30 分钟之后** 的可用档期。
- 如果 `query_availability` 返回的今天档期早于“当前设备时间 + 30 分钟”，必须过滤掉，不能向用户推荐。
- 如果今天没有满足该规则的档期，推荐明天或后续最近可用档期，并说明今天已没有符合提前量的时间。

### Step 0: 查询已有宠物档案和地址

在进入洗护流程前，先查询用户已保存的宠物档案和地址，避免重复询问：

```
call_service(serviceId="pet_store_mock", action="list_pet_profiles", params={})
call_service(serviceId="pet_store_mock", action="list_user_addresses", params={purpose: "pet_pickup"})
recall_facts(query="pet name type breed", limit=5)
recall_facts(query="pet pickup address default_pickup_address", limit=5)
```

- 如果 `list_pet_profiles` 返回已有宠物，向用户确认"是给{宠物名}预约吗？"，不要重新问名字、品种。
- 如果返回为空或用户说"不是/另一只"，再询问宠物信息，并立即调用 `save_pet_profile` 保存。
- 如果 `list_user_addresses` 返回已有默认地址，在安排专车时让用户选择"使用默认地址"或"填写新地址"。
- 如果返回为空，在安排专车步骤时再请用户填写。

### Step 1: 查询门店和服务

使用 `call_service` 查询附近门店和洗护服务：

```
call_service(serviceId="pet_store_mock", action="list_locations", params={pet_type: "dog"})
call_service(serviceId="pet_store_mock", action="list_services", params={pet_type: "dog"})
```

### Step 2: 查询档期和活动

```
call_service(serviceId="pet_store_mock", action="query_availability", params={store_id, service_id, date})
call_service(serviceId="pet_store_mock", action="list_promotions", params={store_id, date})
```

### Step 3: 检查用户日程冲突

使用 `query_calendar` 检查用户在预约时间段是否有冲突：

```
query_calendar(startDate="2026-05-04", endDate="2026-05-04")
```

如果冲突，推荐替代时间。

### Step 4: 展示推荐方案，等待用户确认

展示门店、服务、时间、费用、活动折扣，等待用户确认。
**必须确认后才创建预约。**

### Step 5: 创建预约

```
call_service(serviceId="pet_store_mock", action="create_booking", params={
  store_id, service_id, time, pet_name, pet_type, idempotency_key
})
```

### Step 6: 安排宠物专车（如用户需要）

安排宠物专车前，必须先确定 `pickup_address`（宠物当前所在地址）和 `dropoff_address`（门店地址）。不能只根据当前位置、城市、门店距离或经纬度推断接宠地址；如果没有明确文本地址，禁止调用 `estimate_transport` 或 `request_transport`。

地址处理顺序：

1. 先查已保存地址：

```
call_service(serviceId="pet_store_mock", action="list_user_addresses", params={purpose: "pet_pickup"})
recall_facts(query="pet pickup address default_pickup_address", limit=5)
```

2. 如果没有地址，直接请用户补充宠物当前所在地址，例如“第一次安排接送，需要知道宠物现在在哪里，请发我接宠地址（小区/街道/门牌可选）。”
3. 如果有默认地址，先让用户选择“使用默认地址”或“填写新地址”，不要擅自使用旧地址下单。
4. 用户填写新地址后，立即保存：

```
call_service(serviceId="pet_store_mock", action="save_user_address", params={
  label: "默认接宠地址", address: pickup_address, is_default: true
})
remember_fact(namespace="pet_addresses", key="default_pickup_address", value=pickup_address)
```

5. 只有地址确认完成后，才能继续估价和下单。最终回复中必须展示接宠地址、到店地址、接宠时间和送回时间。

使用 `call_service(pet_transport_mock, ...)` 安排宠物专车接送：

```
call_service(serviceId="pet_transport_mock", action="estimate_transport", params={
  service_type: "pet_pickup_dropoff", pickup_address, dropoff_address, pickup_time, pet_info
})
call_service(serviceId="pet_transport_mock", action="request_transport", params={
  service_type: "pet_pickup_dropoff", pickup_address, dropoff_address, pickup_time, pet_info, reference_id, idempotency_key
})
```

或使用 `deep_link_app` 跳转到打车 App。

### Step 7: 自动创建本次预约日历事件

```
create_calendar_event(title="麒麟 - 狗狗基础洗澡 @ 泡泡宠物生活馆南山店", startTime="2026-05-04T16:00:00", endTime="2026-05-04T17:00:00", location="深圳市南山区粤海街道科技园科苑南路2666号")
```

预约创建成功后，必须自动调用 `create_calendar_event` 写入本次洗护日历事件。不要询问用户是否添加日历，因为用户已经确认了预约方案。

### Step 8: 自动创建下次洗澡日历提醒

预约创建成功后，必须自动为同一只宠物创建 14 天后的下次洗澡日历提醒。不要再询问“是否设置下次洗澡提醒”“是否添加到日历”“还是发短信提醒”。默认使用日历事件；不主动发送短信。

下次洗澡提醒时间规则：
- 日期：本次洗护开始日期 + 14 天。
- 时间：优先沿用本次洗护开始时间；如果无法确定具体开始时间，使用 09:00。
- 时长：30 分钟。
- 标题：`{宠物名} - 下次洗澡提醒`。
- 说明：`距离上次洗澡约14天，建议安排下次洗护。`

示例：

```
create_calendar_event(title="麒麟 - 下次洗澡提醒", startTime="2026-05-18T16:00:00", endTime="2026-05-18T16:30:00", location="", description="距离上次洗澡约14天，建议安排下次洗护。")
```

如果 `create_calendar_event` 因日历权限或系统能力失败，再降级调用 `create_notification`，并在回复中说明“日历写入失败，已改用通知提醒”。不要向用户反复确认。

```
create_notification(title="麒麟该洗澡了", message="距离上次洗澡已14天，该安排下次洗护了")
```

## 商品购买流程 (product_purchase)

### Step 0: 查询已有宠物档案和配送地址

```
call_service(serviceId="pet_store_mock", action="list_pet_profiles", params={})
call_service(serviceId="pet_store_mock", action="list_user_addresses", params={purpose: "delivery"})
recall_facts(query="pet name type breed", limit=5)
recall_facts(query="pet inventory food litter", limit=5)
```

- 如果已有宠物档案，直接按宠物类型筛选商品，不要重复问宠物信息。
- 如果已有配送地址，在配送步骤让用户选择"使用默认地址"或"填写新地址"。

### Step 1: 查询商品和库存

```
call_service(serviceId="pet_store_mock", action="list_products", params={pet_type: "cat", category: "food"})
call_service(serviceId="pet_store_mock", action="query_inventory", params={product_id, store_id})
call_service(serviceId="pet_store_mock", action="list_promotions", params={product_ids: [...]})
```

### Step 2: 展示推荐订单，等待用户确认

展示商品、数量、价格、库存、活动折扣，等待用户确认。

### Step 3: 创建订单

```
call_service(serviceId="pet_store_mock", action="create_order", params={
  product_id, quantity, store_id, delivery_address, idempotency_key
})
```

### Step 4: 安排同城配送

使用 `call_service(pet_transport_mock, ...)` 安排同城配送：

```
call_service(serviceId="pet_transport_mock", action="estimate_transport", params={
  service_type: "same_city_delivery", pickup_address, dropoff_address, cargo_info
})
call_service(serviceId="pet_transport_mock", action="request_transport", params={
  service_type: "same_city_delivery", pickup_address, dropoff_address, cargo_info, reference_id, idempotency_key
})
```

### Step 5: 送达后设置补货提醒

```
create_notification(title="成猫粮5kg快用完了", message="该补货了，需要帮你再买一袋吗？")
```

## 改约流程 (reschedule)

### 改洗护预约

1. 查询新档期：`call_service(pet_store_mock, query_availability, ...)`
2. 检查日程冲突：`query_calendar(startDate, endDate)`
3. 展示变更方案，等待用户确认
4. 修改预约：`call_service(pet_store_mock, modify_booking, ...)`
5. 更新日历事件：`create_calendar_event(...)`（覆盖旧事件）
6. 如有宠物专车，同步修改接送时间：`call_service(pet_transport_mock, modify_transport, ...)`
7. 通知用户变更摘要：`create_notification(title="预约已变更", message="{变更前后对比}")`

### 改商品订单

1. 查询新商品/规格/数量：`call_service(pet_store_mock, list_products, ...)`
2. 查询库存：`call_service(pet_store_mock, query_inventory, ...)`
3. 展示变更方案，等待用户确认
4. 修改订单：`call_service(pet_store_mock, modify_order, ...)`
5. 如有同城配送，同步修改配送信息：`call_service(pet_transport_mock, modify_transport, ...)`

### 改宠物专车时间

用户单独要求改专车时间（不改预约本身）时：

1. 确认新的接送时间
2. 修改接送：`call_service(pet_transport_mock, modify_transport, params={transport_id, new_pickup_time, ...})`
3. 通知用户新的接送安排

## 取消流程 (cancel)

### 取消洗护预约

1. 确认用户取消意图
2. 取消预约：`call_service(pet_store_mock, cancel_booking, params={booking_id})`
3. 如有宠物专车，同步取消接送：`call_service(pet_transport_mock, cancel_transport, params={transport_id, reason: "预约已取消"})`
4. 取消本次洗护日历事件和 14 天后洗澡提醒
5. 通知用户取消结果

### 取消商品订单

1. 确认用户取消意图
2. 取消订单：`call_service(pet_store_mock, cancel_order, params={order_id})`
3. 如有同城配送，同步取消配送：`call_service(pet_transport_mock, cancel_transport, params={transport_id, reason: "订单已取消"})`
4. 通知用户取消结果

### 取消宠物专车

用户单独要求取消专车（不取消预约本身）时：

1. 确认用户取消意图
2. 取消接送：`call_service(pet_transport_mock, cancel_transport, params={transport_id, reason: "用户取消"})`
3. 保留预约不变
4. 通知用户取消结果，提醒用户自行前往门店

## 追加服务 (add_service)

1. 查询可追加服务：`call_service(pet_store_mock, list_services, ...)`
2. 计算差价和额外耗时
3. 检查档期是否允许延长
4. 用户确认后修改预约：`call_service(pet_store_mock, modify_booking, ...)`

## 修改运力 (modify_ride)

1. 使用 `call_service(pet_transport_mock, modify_transport, ...)` 修改接送时间或方式
2. 通知用户新的接送安排

## 开放世界插入处理

| 用户插入 | 处理方式 |
| --- | --- |
| "我今天没空了" | 查日程空档，调用改约，更新宠物专车 |
| "加个刷牙吧" | 查询差价和耗时，确认后修改预约 |
| "顺便买袋狗粮" | 查询狗粮规格、库存和同城配送，确认后下单 |
| "我家猫粮快没了" | 查询猫粮商品、库存和同城配送 |
| "别洗了" | 确认取消后取消预约和宠物专车 |
| "晚点送回来" | 修改送回时间 |
| "换近一点的店" | 重新查询门店并比较距离、档期、库存 |
| "猫今天应激了" | 暂停服务，建议改期或人工确认 |
| "不想要了" | 确认取消后取消订单和配送 |
| "改个时间" | 询问是改预约还是改专车，按对应流程处理 |
| "专车不用了" | 仅取消宠物专车，保留预约 |
| "狗死了" | 表达慰问，自动取消所有相关预约、专车、订单 |

## 特殊情况自动处理

当检测到以下特殊情况时，**无需等待用户主动要求**，自动触发取消或修改：

### 门店闭店 → 自动改约

**触发条件**：`call_service(pet_store_mock, list_locations)` 返回门店 `closed_dates` 包含预约日期

**自动动作**：
1. 告知用户门店当天闭店
2. 查询最近可用日期的档期：`call_service(pet_store_mock, query_availability, ...)`
3. 如有其他门店开门，同时推荐替代门店
4. 等待用户选择后执行改约流程
5. 同步修改宠物专车时间

### 宠物专车不可用 → 自动提示替代方案

**触发条件**：`call_service(pet_transport_mock, estimate_transport)` 返回无可用车辆

**自动动作**：
1. 告知用户暂无宠物专车
2. 询问用户是否自行前往门店（保留预约不变）
3. 如用户选择自行前往，取消专车安排
4. 如用户选择改期，查询有专车的时间段并改约

### 预约时间冲突 → 自动建议改约

**触发条件**：`query_calendar` 返回与预约时间重叠的事件

**自动动作**：
1. 告知用户存在日程冲突
2. 展示冲突事件详情
3. 查询无冲突的替代时间段
4. 等待用户确认后执行改约流程

### 宠物应激/健康异常 → 自动暂停并建议改期

**触发条件**：用户提到宠物应激、生病、受伤等

**自动动作**：
1. 表达关心，建议暂不进行洗护
2. 询问是否改期（推荐 7 天后）
3. 如用户确认改期，执行改约流程
4. 如用户确认取消，执行取消流程
5. 取消宠物专车

### 极端天气/交通异常 → 自动调整接送

**触发条件**：宠物专车预估时间异常偏长或返回交通异常提示

**自动动作**：
1. 告知用户当前交通状况
2. 建议提前出发或延后接送
3. 修改专车时间：`call_service(pet_transport_mock, modify_transport, ...)`
4. 预约时间不变（门店端不受影响）

### 商品库存变化 → 自动通知

**触发条件**：已下单商品库存变为 0 或商品下架

**自动动作**：
1. 通知用户商品状态变化
2. 推荐替代商品
3. 如用户选择换商品，修改订单
4. 如用户选择取消，取消订单和配送

### 用户主动取消所有服务 → 一键清理

**触发条件**：用户表达"都不要了"、"全部取消"等

**自动动作**：
1. 确认取消范围（预约 + 专车 + 订单）
2. 逐一取消：`cancel_booking` → `cancel_transport` → `cancel_order`
3. 清除所有相关日历事件和提醒
4. 通知用户取消结果摘要

## 用户确认节点

只在以下场景要求用户确认：

1. 确认最终服务或订单方案（confirm_grooming_plan / confirm_product_order）
2. 执行重大变更：取消、改期、加价服务、替换商品（confirm_reschedule / confirm_cancellation）
3. 授权敏感能力：安排宠物专车、发起同城配送（confirm_pet_pickup / confirm_delivery）

不要为以下自动后续动作再次请求确认：
- 已确认预约后的本次洗护日历事件创建。
- 已确认预约后的 14 天后下次洗澡日历提醒创建。
- 日历创建失败后的通知提醒降级。

## 生命周期事件与 Hook

主 Skill 在关键动作完成后发出事件，由 Hooks 执行后续动作：

| 事件 | 触发时机 | Hook 自动动作 |
| --- | --- | --- |
| booking_created | 预约创建成功后 | 自动创建本次预约日历事件、自动创建14天后下次洗澡日历提醒、写入 AI Log |
| booking_modified | 改约后 | 更新日历事件、更新宠物专车、通知变更摘要 |
| booking_cancelled | 取消预约后 | 删除日历事件、取消宠物专车 |
| service_completed | 洗护完成后 | 如 booking_created 时未创建提醒，则补建14天后洗澡提醒 |
| order_created | 订单创建后 | 安排同城配送 |
| order_completed | 商品送达后 | 创建补货提醒（粮30天/砂21天） |
| task_error | 关键步骤失败 | 记录失败原因、生成恢复建议 |

Hook 详见 `references/booking-lifecycle-hooks.md` 和 `references/order-lifecycle-hooks.md`。
