---
name: navigation
description: "导航到目的地、查看路线、搜索地点。当用户提到导航、怎么走、路线时使用。"
category: transport
version: "1"
allowed-tools:
  - open_map
  - get_current_location
context: inline
effort: low
risk: low
---

## 导航流程

### Step 1: 获取出发地
如果用户没指定出发地，使用 `get_current_location` 获取当前位置。

### Step 2: 导航
使用 `open_map` 导航：
- 传入目的地名称或地址
- 支持步行、驾车、公交等模式
- 如果用户说"附近的xxx"，传入搜索关键词
