---
name: product-search
description: "搜索商品、比价、查看评价。当用户提到买东西、购物、比价时使用。"
category: shopping
version: "1"
allowed-tools:
  - call_service
  - open_url
  - deep_link_app
context: inline
effort: medium
risk: low
requires:
  connectivity: true
---

## 商品搜索流程

### Step 1: 明确需求
- 用户想买什么商品？
- 有没有品牌、型号偏好？
- 预算范围？

### Step 2: 搜索
使用 `call_service` 搜索商品或使用 `open_url` 在电商网站搜索。

### Step 3: 比价
展示多个来源的价格对比：
- 商品名、价格、店铺、评分
- 标注最优选择

### Step 4: 购买
如需在 App 内购买，使用 `deep_link_app` 跳转到对应电商 App。
