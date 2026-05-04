# 决策节点

## 意图识别决策

```
用户输入
  ├─ 提到洗澡/洗护/修毛/刷牙 → grooming_booking
  ├─ 提到买粮/买猫砂/买用品 → product_purchase
  ├─ 提到改时间/换时间 → reschedule
  ├─ 提到取消/不要了/别洗了 → cancel
  ├─ 提到加服务/加个XX → add_service
  ├─ 提到晚点送/换车/改专车 → modify_ride
  ├─ 提到查询/到哪了 → query
  ├─ 提到宠物生病/应激/受伤 → pet_health_alert → 自动建议改期或取消
  ├─ 提到都不要了/全部取消 → cancel_all → 一键清理
  └─ 确认/好的/就这样 → confirm
```

## ⚠️ 先查后问决策（grooming_booking / product_purchase 必经）

**在向用户提出任何问题之前，必须先查询已有信息。禁止跳过此步骤。**

```
识别到 grooming_booking 或 product_purchase
  │
  ├─ 第一步：查询已有档案
  │   call_service(pet_store_mock, list_pet_profiles)
  │   call_service(pet_store_mock, list_user_addresses)
  │   recall_facts(query="pet name type breed address grooming")
  │
  ├─ 已有宠物档案？
  │   ├─ 是 → 直接确认："是给{宠物名}安排吗？"
  │   │       不再问名字、品种、体型
  │   └─ 否 → 询问宠物信息，获取后立即 save_pet_profile
  │
  └─ 已有默认地址？
      ├─ 是 → 需要接送/配送时确认："使用默认地址（{地址}）还是填写新地址？"
      └─ 否 → 在需要时请用户填写
```

## 门店选择决策

```
需要选择门店
  ├─ 用户指定门店 → 使用指定门店
  ├─ 用户指定距离偏好 → 按距离排序推荐
  ├─ 用户指定价格偏好 → 按活动价格排序推荐
  └─ 无偏好 → 按距离 + 档期可用性综合排序推荐
```

## 档期冲突决策

```
查询档期
  ├─ 偏好时间可用 → 推荐偏好时间
  ├─ 偏好时间冲突
  │   ├─ 有相邻可用档期 → 推荐最近档期
  │   └─ 当天无可用档期 → 推荐最近日期的可用档期
  └─ 偏好时间不在营业时间 → 提醒营业时间，推荐最近可用时间
```

## 日程冲突决策

```
query_calendar 返回事件列表后
  ├─ 预约时间段内无事件 → 无冲突，继续
  ├─ 预约时间段内有事件
  │   ├─ 事件不重叠（时间错开） → 无冲突，继续
  │   └─ 事件时间重叠 → 告知用户冲突事件，推荐替代时间
  └─ query_calendar 失败或无权限 → 跳过冲突检查，继续流程
```

## 库存不足决策

```
查询库存
  ├─ 库存充足 → 正常下单
  ├─ 库存不足
  │   ├─ 其他门店有库存 → 建议换门店或跨店配送
  │   ├─ 有替代商品 → 建议替代商品
  │   └─ 无替代 → 提供补货提醒
  └─ 无库存 → 建议补货提醒或换门店
```

## 宠物专车决策

```
洗护预约确认
  ├─ 用户在服务半径内 → 建议宠物专车接送
  │   ├─ 用户接受
  │   │   ├─ 已有默认接宠地址 → 询问使用默认地址或填写新地址
  │   │   ├─ 没有接宠地址 → 直接要求用户填写宠物当前所在地址
  │   │   ├─ 用户填写新地址 → call_service(pet_store_mock, save_user_address) 并 remember_fact
  │   │   └─ 地址确认后 → call_service(pet_transport_mock, request_transport, service_type="pet_pickup_dropoff")
  │   └─ 用户拒绝 → 仅创建预约，不安排接送
  └─ 用户超出服务半径 → 仅创建预约，提示用户自行前往
```

## 同城配送决策

```
商品订单确认
  ├─ 用户在配送范围内 → 建议同城配送
  │   ├─ 用户接受 → 创建订单，call_service(pet_transport_mock, request_transport, service_type="same_city_delivery")
  │   └─ 用户拒绝 → 建议到店自取
  └─ 用户超出配送范围 → 建议到店自取或快递
```

## 确认节点决策

```
需要用户确认？
  ├─ 首次启动服务 → 是
  ├─ 最终方案确认 → 是
  ├─ 重大变更（取消/改期/加价/替换） → 是
  ├─ 敏感能力授权 → 是
  ├─ 小调整（备注/偏好） → 否
  └─ 信息补充（宠物名/地址） → 否，直接询问
```

## 确认后 action 映射

```
用户回复“确认/好的/就这样”
  ├─ 当前等待确认的是洗护方案 → call_service(pet_store_mock, create_booking)
  ├─ 当前等待确认的是宠物专车 → call_service(pet_transport_mock, request_transport)
  ├─ 当前等待确认的是商品订单 → call_service(pet_store_mock, create_order)
  ├─ 当前等待确认的是改约 → call_service(pet_store_mock, modify_booking)，如有专车再 modify_transport
  └─ 当前等待确认的是取消 → call_service(pet_store_mock, cancel_booking)，如有专车再 cancel_transport
```

不要使用 `reserve`、`confirm`、`confirm_grooming_plan`、`confirm_pet_pickup` 作为 `call_service.action`。

## 取消决策

```
识别到 cancel 意图
  │
  ├─ 用户要取消什么？
  │   ├─ 洗护预约
  │   │   ├─ 确认取消意图
  │   │   ├─ call_service(pet_store_mock, cancel_booking, {booking_id})
  │   │   ├─ 有关联专车？ → call_service(pet_transport_mock, cancel_transport, {transport_id})
  │   │   ├─ 取消本次日历事件和 14 天后洗澡提醒
  │   │   └─ 通知用户取消结果
  │   │
  │   ├─ 商品订单
  │   │   ├─ 确认取消意图
  │   │   ├─ call_service(pet_store_mock, cancel_order, {order_id})
  │   │   ├─ 有关联配送？ → call_service(pet_transport_mock, cancel_transport, {transport_id})
  │   │   └─ 通知用户取消结果
  │   │
  │   ├─ 宠物专车（保留预约）
  │   │   ├─ 确认取消意图
  │   │   ├─ call_service(pet_transport_mock, cancel_transport, {transport_id})
  │   │   ├─ 保留预约不变
  │   │   └─ 提醒用户自行前往门店
  │   │
  │   └─ 全部取消（cancel_all）
  │       ├─ 确认取消范围
  │       ├─ cancel_booking → cancel_transport → cancel_order（逐一取消）
  │       ├─ 清除所有相关日历事件和提醒
  │       └─ 通知用户取消结果摘要
```

## 改约决策

```
识别到 reschedule 意图
  │
  ├─ 用户要改什么？
  │   ├─ 洗护预约时间
  │   │   ├─ call_service(pet_store_mock, query_availability, ...) 查询新档期
  │   │   ├─ query_calendar 检查日程冲突
  │   │   ├─ 展示变更方案，等待确认
  │   │   ├─ call_service(pet_store_mock, modify_booking, ...)
  │   │   ├─ 更新日历事件
  │   │   ├─ 有关联专车？ → call_service(pet_transport_mock, modify_transport, ...)
  │   │   └─ 通知变更摘要
  │   │
  │   ├─ 商品订单
  │   │   ├─ 查询新商品/规格/库存
  │   │   ├─ 展示变更方案，等待确认
  │   │   ├─ call_service(pet_store_mock, modify_order, ...)
  │   │   ├─ 有配送？ → call_service(pet_transport_mock, modify_transport, ...)
  │   │   └─ 通知变更摘要
  │   │
  │   └─ 宠物专车时间（不改预约）
  │       ├─ 确认新的接送时间
  │       ├─ call_service(pet_transport_mock, modify_transport, {transport_id, new_pickup_time})
  │       └─ 通知新接送安排
```

## 特殊情况自动触发决策

```
检测到特殊情况（无需用户主动要求）
  │
  ├─ 门店闭店
  │   ├─ 告知用户门店当天闭店
  │   ├─ 查询最近可用日期档期
  │   ├─ 推荐替代门店（如有）
  │   └─ 等待用户选择后执行改约
  │
  ├─ 宠物专车不可用
  │   ├─ 告知用户暂无专车
  │   ├─ 询问自行前往或改期
  │   ├─ 自行前往 → 取消专车，保留预约
  │   └─ 改期 → 查询有专车的时间并改约
  │
  ├─ 日程冲突
  │   ├─ 告知用户冲突事件
  │   ├─ 查询无冲突的替代时间
  │   └─ 等待用户确认后改约
  │
  ├─ 宠物应激/健康异常（pet_health_alert）
  │   ├─ 表达关心，建议暂不洗护
  │   ├─ 用户同意改期 → 推荐约 7 天后，执行改约
  │   ├─ 用户选择取消 → 执行取消流程
  │   └─ 取消关联专车
  │
  ├─ 极端天气/交通异常
  │   ├─ 告知交通状况
  │   ├─ 建议提前出发或延后接送
  │   ├─ call_service(pet_transport_mock, modify_transport, ...)
  │   └─ 预约时间不变
  │
  └─ 商品库存变化
      ├─ 通知用户商品状态变化
      ├─ 推荐替代商品
      ├─ 用户换商品 → 修改订单
      └─ 用户取消 → 取消订单和配送
```
