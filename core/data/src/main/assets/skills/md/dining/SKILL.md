---
name: dining
description: "搜索附近餐厅、查看评价、预订座位、点外卖。当用户提到吃饭、餐厅、外卖、美食推荐时使用。"
category: food
version: "1"
allowed-tools:
  - call_service
  - get_current_location
  - open_map
  - read_user_profile
context: inline
effort: medium
risk: medium
requires:
  connectivity: true
required-services:
  - opentable
  - meituan
user-confirmation-points:
  - confirm_reservation
  - confirm_payment
---

## 餐厅搜索与预订流程

### Step 1: 明确用户需求
- 如果用户说"帮我找个餐厅"或"附近有什么吃的"，优先快速搜索附近餐厅。
- 只在真正需要时才问额外细节。

### Step 2: 确定搜索区域
- 如果用户已给出地址，直接使用。
- 如果要搜附近，使用 `get_current_location` 获取坐标。
- 获取位置后立即继续搜索，不要只返回坐标。

### Step 3: 搜索餐厅
- 使用 `call_service`(opentable/meituan, searchRestaurants) 搜索餐厅。
- 或使用 `open_map` 让用户在地图上浏览。
- 可用 `read_user_profile`(preferences, dining) 了解口味偏好。
- 按菜系、评分、距离、人均消费过滤。

### Step 4: 展示结果
- 先展示附近或匹配的餐厅列表。
- 包含：餐厅名称、评分、距离、人均消费。
- 如需导航，使用 `open_map`。

### Step 5: 预订
- 用户选定餐厅后，使用 `call_service`(opentable, makeReservation)。
- 预订前必须确认：餐厅名、日期、时间、人数。
- 优先推荐评分 4.0 以上的餐厅。
