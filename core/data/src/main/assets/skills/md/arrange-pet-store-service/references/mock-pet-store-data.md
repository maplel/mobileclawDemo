# Mock 宠物店数据

## 门店

```json
{
  "locations": [
    {
      "id": "pet_store_nanshan_001",
      "name": "泡泡宠物生活馆 南山店",
      "address": "深圳市南山区粤海街道科技园科苑南路 2666 号",
      "service_radius_km": 8,
      "business_hours": {
        "weekday": "09:30-20:00",
        "weekend": "10:00-21:00"
      },
      "closed_dates": ["2026-05-06"]
    },
    {
      "id": "pet_store_futian_001",
      "name": "汪喵宠物生活馆 福田店",
      "address": "深圳市福田区福田街道福华三路 118 号",
      "service_radius_km": 10,
      "business_hours": {
        "weekday": "10:00-19:30",
        "weekend": "10:00-20:30"
      },
      "closed_dates": []
    }
  ]
}
```

## 洗护服务

```json
{
  "services": [
    {
      "id": "dog_bath_basic",
      "name": "狗狗基础洗澡",
      "pet_type": "dog",
      "duration_minutes": 60,
      "price": 99,
      "supported_locations": ["pet_store_nanshan_001", "pet_store_futian_001"]
    },
    {
      "id": "dog_bath_teeth",
      "name": "狗狗洗澡 + 刷牙",
      "pet_type": "dog",
      "duration_minutes": 80,
      "price": 139,
      "supported_locations": ["pet_store_nanshan_001"]
    },
    {
      "id": "cat_bath_care",
      "name": "猫咪温和洗护",
      "pet_type": "cat",
      "duration_minutes": 90,
      "price": 169,
      "supported_locations": ["pet_store_futian_001"]
    }
  ]
}
```

## 商品

```json
{
  "products": [
    {
      "id": "dog_food_adult_10kg",
      "name": "成犬粮 10kg",
      "pet_type": "dog",
      "price": 289,
      "stock": 12,
      "locations": {
        "pet_store_nanshan_001": 8,
        "pet_store_futian_001": 4
      }
    },
    {
      "id": "cat_food_adult_5kg",
      "name": "成猫粮 5kg",
      "pet_type": "cat",
      "price": 199,
      "stock": 8,
      "locations": {
        "pet_store_nanshan_001": 8,
        "pet_store_futian_001": 0
      }
    },
    {
      "id": "cat_litter_tofu_6l",
      "name": "豆腐猫砂 6L",
      "pet_type": "cat",
      "price": 49,
      "stock": 20,
      "locations": {
        "pet_store_nanshan_001": 10,
        "pet_store_futian_001": 10
      }
    }
  ]
}
```

## 门店日历

```json
{
  "store_calendar": {
    "pet_store_nanshan_001": {
      "2026-05-04": {
        "open": "09:30",
        "close": "20:00",
        "breaks": ["12:30-13:30"],
        "booked_slots": [
          {
            "booking_id": "bk_other_001",
            "service_id": "dog_bath_basic",
            "start": "10:00",
            "end": "11:00",
            "pet_type": "dog",
            "note": "其他用户已预约"
          },
          {
            "booking_id": "bk_other_002",
            "service_id": "dog_bath_teeth",
            "start": "16:00",
            "end": "17:20",
            "pet_type": "dog",
            "note": "其他用户已预约"
          }
        ],
        "staff_unavailable": ["18:00-19:00"]
      },
      "2026-05-05": {
        "open": "09:30",
        "close": "20:00",
        "breaks": ["12:30-13:30"],
        "booked_slots": []
      }
    },
    "pet_store_futian_001": {
      "2026-05-04": {
        "open": "10:00",
        "close": "19:30",
        "breaks": ["13:00-14:00"],
        "booked_slots": [
          {
            "booking_id": "bk_other_101",
            "service_id": "cat_bath_care",
            "start": "15:00",
            "end": "16:30",
            "pet_type": "cat",
            "note": "其他用户已预约"
          }
        ]
      }
    }
  }
}
```

## 活动

```json
{
  "promotions": [
    {
      "id": "promo_weekday_bath",
      "name": "工作日洗护 9 折",
      "applies_to": ["dog_bath_basic", "dog_bath_teeth", "cat_bath_care"],
      "discount": 0.9,
      "valid_days": ["monday", "tuesday", "wednesday", "thursday", "friday"]
    },
    {
      "id": "promo_food_bundle",
      "name": "猫粮 + 猫砂组合减 20 元",
      "applies_to": ["cat_food_adult_5kg", "cat_litter_tofu_6l"],
      "discount_amount": 20
    }
  ]
}
```

## 预约规则

```json
{
  "availability_rules": {
    "slot_granularity_minutes": 30,
    "min_advance_minutes": 60,
    "booking_conflict_policy": "reject_conflict_and_suggest_nearest_slots"
  }
}
```
