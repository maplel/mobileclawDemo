---
name: road-accident-response
description: "交通事故全流程响应：报警、保险理赔、拖车救援、行程改签、住宿安排。"
category: life-service
version: "1"
context: fork
effort: high
risk: high
allowed-tools:
  - use_skill
  - get_current_location
  - dial_number
  - send_sms
  - open_camera
  - call_service
  - read_user_profile
composes-skills:
  - emergency-response
  - insurance-claim
  - tow-and-repair
  - trip-replan
  - accommodation
  - dining
user-confirmation-points:
  - confirm_emergency_call
  - confirm_insurance_claim
  - confirm_new_itinerary
  - confirm_hotel_booking
---

## 交通事故响应编排流程

You are coordinating a comprehensive accident response. Follow this priority order strictly: safety > legal compliance > property > travel experience.

### Phase 1: 紧急处理（并行）

Immediately start these tasks:

1. **Emergency response**: Get current GPS location. Call emergency services. Read emergency contacts from user profile and send SMS notifications with location and situation. Use the camera to document the scene.

2. **Insurance claim**: Read user insurance policy from profile. Gather incident details: time, location, parties involved. Prepare claim materials.

3. **Tow and repair**: Get current location. Request tow to nearest authorized service center. Find repair facility and schedule appointment.

### Phase 2: 善后安排

After Phase 1 emergency and towing are handled:

4. **Trip replanning**: Use `use_skill` to invoke the `trip-replan` skill. Adjust the itinerary based on estimated repair time (1-2 days).

### Phase 3: 住宿和餐饮

After trip replan is confirmed:

5. **Accommodation**: Use `use_skill` to invoke the `accommodation` skill. Find a hotel near the vehicle service center.

6. **Dining**: Find a restaurant near the booked hotel for a relaxing meal.

### Phase 4: 汇总报告

Present a comprehensive summary:
- Emergency status and police case number
- Insurance claim number and next steps
- Tow destination and repair appointment
- Revised trip itinerary
- Hotel booking details
- Dinner reservation

### Important Notes
- The user should only need to confirm twice: the new itinerary and the hotel choice
- Always prioritize safety over convenience
