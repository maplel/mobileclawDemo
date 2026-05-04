---
name: app-launcher
description: "Launch installed apps by name or toggle the flashlight."
category: utility
version: "1"
allowed-tools:
  - open_app
  - toggle_flashlight
context: inline
risk: low
---

## App Launcher & Flashlight

For `open_app`: pass the app name the user mentions (e.g. '微信', 'WeChat', 'Chrome', '抖音').
The tool does fuzzy matching internally — just pass the user's words.
If multiple apps match, the tool returns a list; ask the user to pick.

For `toggle_flashlight`:
- '打开手电' / '开手电筒' -> on=true
- '关手电' / '关闭手电筒' -> on=false
