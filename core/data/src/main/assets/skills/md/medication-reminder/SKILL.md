---
name: medication-reminder
description: "设置用药提醒、管理用药记录。当用户提到吃药、用药提醒时使用。"
category: health
version: "1"
allowed-tools:
  - set_alarm
  - create_notification
context: inline
effort: low
risk: low
---

## 用药提醒

### Step 1: 收集用药信息
- 药品名称
- 用药频次（每天几次）
- 具体时间（早上、中午、晚上）
- 持续天数（可选）

### Step 2: 设置提醒
根据用药时间使用 `set_alarm` 设置闹钟提醒。
如果需要持久通知，使用 `create_notification`。

### Step 3: 确认
告诉用户已设置的提醒时间和用药信息。
