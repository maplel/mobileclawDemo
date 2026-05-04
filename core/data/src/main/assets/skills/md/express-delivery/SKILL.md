---
name: express-delivery
description: "查询快递状态、预约取件。当用户提到快递、包裹、取件时使用。"
category: life-service
version: "1"
allowed-tools:
  - call_service
  - create_notification
context: inline
effort: low
risk: low
requires:
  connectivity: true
---

## 快递服务

### 查询快递
使用 `call_service` 查询快递状态：
- serviceId: "kuaidi100"
- action: "track"
- 参数：trackingNumber, carrier（可选）

### 预约取件
使用 `call_service` 预约上门取件：
- 确认取件地址、时间、物品信息

### 到件通知
使用 `create_notification` 设置快递到件提醒。
