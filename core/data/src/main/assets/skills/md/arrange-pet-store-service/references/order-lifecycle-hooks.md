# 订单生命周期 Hooks

## 事件定义

## 统一事件载荷

所有订单事件至少包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| event_id | string | 事件唯一 ID |
| event_type | string | order_created / order_completed / order_cancelled / task_error |
| task_id | string | 当前任务 ID |
| idempotency_key | string | Hook 去重键 |
| occurred_at | string | 事件发生时间 |
| order_id | string | 订单编号 |
| payload | object | 商品、数量、金额、门店、取件地址、送达地址、配送方式等 |

订单配送由 `order_created` Hook 统一安排。主编排 Skill 在商品下单成功后只创建订单并发出事件，避免主流程和 Hook 重复叫车。

### order_created

**触发时机**：商品订单创建成功后

**自动动作**：

1. 写入订单状态
   - 记录订单编号、商品、数量、金额、配送地址
   - 格式：`{时间} 已创建订单：{商品} x{数量}，订单编号 {order_id}`

2. 安排同城配送
   - 使用 `call_service(pet_transport_mock, request_transport, ...)` 安排同城配送
   - service_type：默认 same_city_delivery；如果用户要求和已有宠物专车一起送回，可使用 courier_pickup
   - 取件地址：门店地址
   - 送达地址：用户地址
   - cargo_info：商品列表和预估重量
   - 传入 `reference_id: {order_id}`
   - 传入 `idempotency_key: order_created:{order_id}:delivery`

3. 写入 AI Log
   - 格式：`{时间} 订单 {order_id} 已创建，预计 {预计送达时间} 送达`

**幂等规则**：
- 同一 order_id 只创建一次配送订单
- 如果配送已安排，跳过
- 如果用户选择到店自取，事件 payload 标记 `delivery_required: false`，Hook 只写日志不叫车
- 如果事件 payload 包含 `delivery_service_type`，Hook 按该值调用运力服务；未提供时使用 `same_city_delivery`

### order_completed

**触发时机**：商品送达后

**自动动作**：

1. 根据消耗周期创建下次购买提醒
   - 狗粮/猫粮：使用 `create_notification`，间隔 30 天
   - 猫砂：使用 `create_notification`，间隔 21 天
   - 零食：不设置周期提醒
   - 标题：`{商品名} 快用完了，该补货了`
   - 传入 `reference_id: {order_id}`
   - 传入 `idempotency_key: order_completed:{order_id}:restock_reminder`

2. 写入 AI Log
   - 格式：`{时间} 订单 {order_id} 已送达，已设置 {间隔}天后补货提醒`

**幂等规则**：
- 同一 order_id 只创建一次提醒
- 如果提醒已存在，跳过创建
- 提醒创建失败不改变订单已送达状态，只记录失败并给出补救建议

### order_cancelled

**触发时机**：用户确认取消订单后

**自动动作**：

1. 取消同城配送
   - 使用 `call_service(pet_transport_mock, cancel_transport, ...)` 取消配送
   - 原因：订单已取消

2. 写入 AI Log
   - 格式：`{时间} 已取消订单 {order_id}，原因：{取消原因}`

**幂等规则**：
- 同一 order_id 只取消一次
- 如果配送已取消，跳过

### task_error

**触发时机**：任一关键步骤失败后

**自动动作**：

1. 记录失败原因
   - 写入 AI Log
   - 格式：`{时间} 操作失败：{步骤}，原因：{错误信息}`

2. 生成可恢复的下一步建议
   - 库存不足 -> 建议换门店或等待补货
   - 配送不可用 -> 建议到店自取
   - 价格变动 -> 重新确认价格后继续

**幂等规则**：
- 同一错误只记录一次
- 不重复发送通知
