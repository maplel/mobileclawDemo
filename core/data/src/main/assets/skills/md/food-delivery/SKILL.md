---
name: food-delivery
description: "点外卖、查看附近外卖选项、下单。当用户提到点外卖、送餐、叫外卖时使用。"
category: food
version: "1"
allowed-tools:
  - call_service
  - get_current_location
  - deep_link_app
context: inline
effort: medium
risk: medium
requires:
  connectivity: true
user-confirmation-points:
  - confirm_order
  - confirm_payment
---

## 外卖点餐流程

### Step 1: 获取位置
使用 `get_current_location` 确定送餐地址。

### Step 2: 搜索外卖
使用 `call_service` 搜索附近外卖选项：
- serviceId: "meituan" 或 "eleme"
- action: "searchDelivery"
- 参数：latitude, longitude, cuisine（可选）

### Step 3: 展示选项
整理为用户友好的列表：
- 店名、评分、配送时间、起送价
- 如果用户指定了菜系，优先展示

### Step 4: 下单
如果外卖平台需要 App 内操作，使用 `deep_link_app` 跳转。
下单前必须确认用户选择。
