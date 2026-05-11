---
name: schedule-management
description: "管理日程、创建日历事件、查看安排。当用户提到日程、安排、日历时使用。"
category: utility
version: "1"
allowed-tools:
  - create_calendar_event
  - query_calendar
  - set_alarm
context: inline
effort: low
risk: low
---

## 日程管理

### 创建事件
从用户输入提取：
- 事件标题
- 日期和时间
- 时长或结束时间
- 地点（可选）

使用 `create_calendar_event` 创建事件。
如需提前提醒，使用 `set_alarm`。

### 查询日程
使用 `query_calendar` 查看指定日期的安排。
- 支持"今天"、"明天"、"本周"等自然语言时间
