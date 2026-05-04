---
name: ride-hailing
description: "叫车、查看行程、预估费用。当用户提到打车、叫车、网约车时使用。"
category: transport
version: "1"
allowed-tools:
  - call_service
  - get_current_location
  - open_map
  - deep_link_app
context: inline
effort: medium
risk: medium
requires:
  connectivity: true
user-confirmation-points:
  - confirm_ride
---

## 叫车流程

### Step 1: 确定出发地和目的地
- 使用 `get_current_location` 获取当前位置作为出发地。
- 从用户消息中提取目的地。

### Step 2: 预估费用
使用 `call_service` 预估费用：
- serviceId: "didi" 或 "uber"
- action: "estimateRide"
- 参数：origin, destination

### Step 3: 叫车
- 展示预估费用和时间。
- 用户确认后，使用 `deep_link_app` 跳转到打车 App。
- 或使用 `call_service` 直接叫车。

### Step 4: 导航
如需查看路线，使用 `open_map` 显示从出发地到目的地的路线。
