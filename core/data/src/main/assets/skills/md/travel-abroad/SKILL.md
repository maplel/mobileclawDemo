---
name: travel-abroad
description: "Plan and coordinate an international trip: visa, flights, accommodation, dining, and itinerary."
category: life-service
version: "1"
context: fork
effort: high
risk: medium
allowed-tools:
  - use_skill
  - call_service
  - read_user_profile
  - open_map
  - open_url
composes-skills:
  - visa-preparation
  - flight-booking
  - accommodation
  - dining
  - trip-replan
requires:
  connectivity: true
user-confirmation-points:
  - confirm_flights
  - confirm_hotel
---

## International Travel Planning Orchestration

You are helping the user plan an international trip. Coordinate all aspects in a logical order.

### Phase 1: Trip Framework
- Ask user: destination, dates, budget, travel style
- Use `read_user_profile` for travel preferences and existing trip plans

### Phase 2: Prerequisites (parallel)
1. **Visa**: Use `use_skill` to invoke `visa-preparation`. Check visa requirements for the destination.
2. **Flights**: Use `use_skill` to invoke `flight-booking`. Search flights based on user dates and preferences.

### Phase 3: Accommodations (after flights confirmed)
3. **Hotels**: Use `use_skill` to invoke `accommodation`. Find hotels considering location near attractions, user preferences, and loyalty programs.

### Phase 4: Daily Planning (after hotels confirmed)
4. **Itinerary**: Create a day-by-day itinerary including top attractions, local experiences, and travel between locations.
5. **Dining**: Use `use_skill` to invoke `dining` for restaurant recommendations for each day.

### Phase 5: Summary
Present a complete trip package:
- Visa status and requirements
- Flight details and confirmation
- Hotel bookings
- Day-by-day itinerary
- Restaurant recommendations/reservations
- Packing suggestions based on destination weather
