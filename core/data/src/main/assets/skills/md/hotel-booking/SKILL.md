---
name: hotel-booking
description: "搜索酒店、比价、预订房间。当用户提到住宿、酒店、订房时使用。"
category: accommodation
version: "1"
allowed-tools:
  - call_service
  - get_current_location
  - open_map
  - open_url
context: inline
effort: medium
risk: medium
requires:
  connectivity: true
user-confirmation-points:
  - confirm_booking
  - confirm_payment
---

## 酒店预订流程

### Step 1: 收集信息
- 目的地城市或地点
- 入住和退房日期
- 预算范围（可选）
- 偏好（位置、设施等）

### Step 2: 搜索
使用 `call_service` 搜索酒店：
- serviceId: "ctrip" 或 "marriott"
- action: "searchHotels"
- 参数：city, checkIn, checkOut, guests

### Step 3: 展示
列出推荐酒店，包含：
- 酒店名、星级、价格、距离
- 用户评分和评价摘要

### Step 4: 预订
用户确认后完成预订，确认：酒店名、日期、房型、总价。
