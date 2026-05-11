---
name: hospital-appointment
description: "预约挂号、查找医院和科室。当用户提到看病、挂号、医院时使用。"
category: health
version: "1"
allowed-tools:
  - call_service
  - get_current_location
  - open_map
context: inline
effort: medium
risk: medium
requires:
  connectivity: true
user-confirmation-points:
  - confirm_appointment
---

## 医院预约流程

### Step 1: 了解需求
- 什么症状或科室？
- 偏好的医院或地区？
- 时间偏好？

### Step 2: 搜索医院
使用 `get_current_location` + `call_service` 搜索附近医院：
- 按科室、距离、评分筛选
- 展示可预约时段

### Step 3: 预约
用户确认后完成预约，确认：医院、科室、医生、时间。
使用 `open_map` 提供导航。
