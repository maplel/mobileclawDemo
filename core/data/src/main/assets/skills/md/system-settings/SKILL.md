---
name: system-settings
description: "Open system settings pages by keyword (Wi-Fi, Bluetooth, battery, etc.)."
category: utility
version: "1"
allowed-tools:
  - open_settings
context: inline
risk: low
---

## System Settings

Open system settings pages by keyword.

Valid page values for `open_settings`: wifi, bluetooth, display, sound, battery, storage, apps, location, security, date, accessibility, about, airplane, notification_access, wireless, input.

Chinese mapping:
- Wi-Fi/无线 = wifi
- 蓝牙 = bluetooth
- 显示/亮度 = display
- 声音/音量 = sound
- 电池/电量 = battery
- 存储 = storage
- 应用/应用管理 = apps
- 位置/定位 = location
- 安全 = security
- 日期/时间 = date
- 无障碍 = accessibility
- 关于手机 = about
- 飞行模式 = airplane
- 通知权限 = notification_access
- 网络 = wireless
- 键盘/输入法 = input

Omit page or pass null for general settings.
