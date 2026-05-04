---
name: alarm-timer
description: "Set alarms and countdown timers from natural language."
category: utility
version: "1"
allowed-tools:
  - set_alarm
  - set_timer
context: inline
risk: low
---

## Alarm & Timer

For `set_alarm`: extract hour (0-23) and minute (0-59) from user input.
- '7点半' -> hour=7, minute=30
- '下午3点' -> hour=15, minute=0
- '早上6点45' -> hour=6, minute=45
- '晚上10点' -> hour=22, minute=0

For `set_timer`: convert duration to total seconds.
- '5分钟' -> seconds=300
- '1小时' -> seconds=3600
- '90秒' -> seconds=90
- '1小时30分' -> seconds=5400

If user says '提醒我5分钟后' use `set_timer`.
If user says '设个早上7点的闹钟' use `set_alarm`.
