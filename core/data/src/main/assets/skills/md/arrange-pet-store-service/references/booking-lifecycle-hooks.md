# 预约生命周期 Hooks

## 事件定义

## 统一事件载荷

所有预约事件至少包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| event_id | string | 事件唯一 ID |
| event_type | string | booking_created / booking_modified / booking_cancelled / service_completed / task_error |
| task_id | string | 当前任务 ID |
| idempotency_key | string | Hook 去重键 |
| occurred_at | string | 事件发生时间 |
| booking_id | string | 预约编号 |
| payload | object | 门店、服务、宠物、时间、地址、ride_id、calendar_event_id 等 |

Hook 只能消费事件并执行副作用，不应重新决定用户方案；缺少必要字段时写入 `task_error`，不要猜测执行。

### booking_created

**触发时机**：洗护预约创建成功后

**自动动作**：

1. 写入 AI Log
   - 记录预约编号、门店、服务、时间、宠物信息
   - 格式：`{时间} 已创建预约：{服务} @ {门店} {时间}，预约编号 {booking_id}`

2. 创建日历事件
   - 使用 `create_calendar_event`
   - 传入 `reference_id: {booking_id}`
   - 传入 `idempotency_key: booking_created:{booking_id}:calendar_event`
   - 标题：`{宠物名} - {服务名} @ {门店名}`
   - 时间：预约开始到结束
   - 地点：门店地址
   - 提醒：提前 30 分钟

3. 创建 14 天后下次洗澡日历提醒
   - 使用 `create_calendar_event`
   - 不要询问用户是否创建；用户确认预约后自动执行
   - 不要询问是否改用短信；默认写入日历
   - 传入 `idempotency_key: booking_created:{booking_id}:next_grooming_calendar`
   - 标题：`{宠物名} - 下次洗澡提醒`
   - 时间：预约开始时间 + 14 天，结束时间为开始后 30 分钟
   - 说明：`距离上次洗澡约14天，建议安排下次洗护。`
   - 如果日历创建失败，降级使用 `create_notification`，并记录失败原因

**幂等规则**：
- 同一 booking_id 只创建一次日历事件
- 如果日历事件已存在，跳过创建，仅更新
- 同一 booking_id 只创建一次下次洗澡提醒

### booking_modified

**触发时机**：改约、换服务、换门店后

**自动动作**：

1. 更新日历事件
   - 使用 `create_calendar_event` 覆盖旧事件
   - 更新标题、时间、地点等变更字段

2. 通知用户变更摘要
   - 使用 `create_notification`
   - 列出变更前后对比
   - 格式：`预约已变更：{变更项}，新时间 {新时间}`

3. 如果涉及宠物专车，更新接送安排
   - 使用 `call_service(pet_transport_mock, modify_transport, ...)` 修改接送时间
   - 仅在事件 payload 中已有 `ride_id` 时执行

**幂等规则**：
- 同一 booking_id 的同一变更只处理一次
- 使用变更时间戳去重

### booking_cancelled

**触发时机**：用户确认取消预约后

**自动动作**：

1. 删除或标记日历事件
   - 日历事件标记为已取消

2. 取消宠物专车
   - 使用 `call_service(pet_transport_mock, cancel_transport, ...)` 取消
   - 原因：预约已取消
   - 仅在事件 payload 中已有 `ride_id` 时执行

3. 写入 AI Log
   - 格式：`{时间} 已取消预约 {booking_id}，原因：{取消原因}`

**幂等规则**：
- 同一 booking_id 只取消一次
- 如果日历事件已删除，跳过
- 如果宠物专车已取消，跳过

### service_completed

**触发时机**：洗护服务完成后

**自动动作**：

1. 创建下次洗澡提醒（仅当 booking_created 阶段未创建时补建）
   - 优先使用 `create_calendar_event`
   - 传入 `idempotency_key: service_completed:{booking_id}:grooming_reminder`
   - 标题：`{宠物名} - 下次洗澡提醒`
   - 时间：服务完成或预约开始时间 + 14 天，结束时间为开始后 30 分钟
   - 内容：`距离上次洗澡约14天，建议安排下次洗护。`
   - 不要询问用户是否添加到日历或是否发送短信
   - 如果日历创建失败，降级使用 `create_notification`

2. 写入 AI Log
   - 格式：`{时间} {宠物名} 洗护服务已完成，已设置 14 天后洗澡提醒`

**幂等规则**：
- 同一 booking_id 只创建一次提醒
- 如果提醒已存在，跳过创建
- 提醒创建失败不影响"服务已完成"状态，只记录失败并提示用户可手动补设

### task_error

**触发时机**：任一关键步骤失败后

**自动动作**：

1. 记录失败原因
   - 写入 AI Log
   - 格式：`{时间} 操作失败：{步骤}，原因：{错误信息}`

2. 生成可恢复的下一步建议
   - 根据失败步骤提供建议
   - 例如：档期查询失败 -> 建议换门店或换日期
   - 例如：打车服务不可用 -> 建议用户自行前往

**幂等规则**：
- 同一错误只记录一次
- 不重复发送通知
