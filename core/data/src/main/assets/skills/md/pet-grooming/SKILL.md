---
name: pet-grooming
description: "Event-template skill for coordinating Kylin's recurring grooming appointment, transport, progress tracking, payment, and accounting."
category: scenario
version: "3"
allowed-tools:
  - device_system
  - system_search_contacts
  - system_send_sms
  - system_wait_for_sms
  - create_plan
context: inline
effort: high
risk: low
scenario-id: pet-grooming
display-mode: fullscreen
trigger-cadence: saturday_precheck_for_sunday_grooming
trigger-current-surface: app_launch_pet_grooming_scenario
system-capabilities:
  - user_memory
  - sms
  - location
  - contacts
  - notifications
  - service_gateway
  - pet_salon_search
  - payment
  - accounting
decision-points:
  - defer_current_week
  - confirm_grooming
  - confirm_service_scope_or_time_tradeoff
  - confirm_unexpected_issue
timeline-hints:
  - weekly_precheck
  - grooming_confirmed
  - parties_ready
  - salon_slot_negotiation
  - service_scope_confirmed
  - salon_booked
  - driver_booked
  - departure_reminder_created
  - driver_pickup_arrival
  - salon_arrival
  - salon_progress_update
  - pickup_time_adjusted
  - grooming_finished
  - fee_received
  - driver_returning
  - home_reminder_created
  - home_confirmed
  - payment_and_accounting_completed
user-confirmation-points:
  - defer_current_week
  - confirm_grooming
  - confirm_service_scope_or_time_tradeoff
  - confirm_unexpected_issue
---

## Purpose

Use this skill as the event template for Kylin's recurring grooming workflow. The reference spreadsheet describes a common, representative flow. It is not a frame-by-frame script. Use it to understand the normal event chain, actors, tool usage, decision boundaries, UI surfaces, and completion criteria, then adapt to the current user reply, memory, and system results.

Do not present this as a scripted playback, test run, or internal exercise. Do not expose internal labels, tool names, JSON, trace details, or implementation terms in user-facing messages.

All user-facing assistant messages, action candidates, plan titles, plan steps, SMS bodies, reminders, notifications, payment descriptions, and accounting descriptions must be Chinese. Proper nouns such as PetSmart, Driver, Kylin, CNY, and NT may stay as written. Do not write English prose on user-facing surfaces.

## Actors

- User: Y, Kylin's owner.
- Pet: Kylin / 麒麟.
- Grooming shop: PetSmart is Y's preferred shop for Kylin. Resolve PetSmart's identity, address, and contact channel through the pet salon search service unless active memory already has a confirmed shop for this grooming task.
- Transport contact: Driver, Y's private driver. Resolve Driver from contacts before messaging.
- Blueprint parties: NT, selected grooming shop, Driver.

## Trigger Model

The intended trigger is the Saturday precheck for Sunday's recurring grooming cadence. The current app surface may start this scenario directly; in that case, treat app launch as if the scheduled trigger fired.

For the current scenario clock, use the date and time provided by the trigger prompt. Treat the next day as the grooming day unless a trusted system result gives a more specific date. Do not invent unrelated dates. Use that grooming date for payment and accounting records; do not use the phone's real current year.

Use memory to decide where the current event is:

- If there is no active grooming task for the coming Sunday, start with the weekly precheck.
- If the user already deferred this week, acknowledge and stop until the next cadence.
- If the user already approved grooming, continue with task creation, party resolution, salon coordination, driver coordination, monitoring, and closure.
- If the workflow is already in progress, resume from the latest trusted operational signal.

## Core Invariants

- Keep interruption low. Ask only when the user must make a preference or risk decision.
- Once Y answers a declared decision point, treat that answer as authorization for routine downstream actions in that branch.
- If Y selects a concrete grooming time, service scope, or action candidate, treat it as confirmation for that option. Do not ask Y to confirm the same option again.
- Do not complete the workflow when grooming finishes. The workflow remains open until Kylin is confirmed home, payment is completed, and the expense is recorded.
- Do not end with a generic open question or a promise to monitor later while the workflow is still open. If monitoring is the next step, immediately call `system_wait_for_sms`.
- Do not invent phone numbers, contacts, locations, prices, or service capabilities. Resolve them through tools or memory.
- The pet salon search service is for shop discovery, contact details, service items, and published prices. Use its published price as the normal expected fee. Do not treat it as the source of appointment availability or booking status.
- PetSmart availability, final service scope, and booking status must be confirmed through SMS with PetSmart. Do not ask PetSmart to confirm the price by SMS unless a message suggests an add-on, abnormal cost, discount, or price conflict.
- Never ask PetSmart for a final price in the normal path. Use the published service price from `pet_salon_search`.
- Do not include published prices, totals, `CNY`, or fee details in normal outbound PetSmart SMS. PetSmart SMS should confirm only timing, service scope, and booking status unless there is an abnormal price issue.
- Do not contact Driver until the selected grooming shop has confirmed the final selected booking slot by inbound SMS.
- After Y chooses the 9:00 option, the next operational step is only to send PetSmart a confirmation SMS for that slot and wait for PetSmart's booking confirmation. Do not call `system_send_sms` for Driver before that inbound PetSmart confirmation exists in history.
- After the selected grooming shop confirms the selected booking, contact Driver directly. Do not ask Y whether to contact Driver, because Y's earlier selection already authorized routine downstream coordination.
- Driver is Y's private driver, not a shop pickup service. The first Driver coordination message should ask Driver to pick Kylin up from Y's home and deliver her to the selected grooming shop before the appointment. Do not include a predicted grooming finish time, return pickup time, or home-arrival instruction in this first Driver SMS. After Driver confirms the first pickup plan, the next listener must be `system_wait_for_sms` from Driver for Driver's delivery-to-shop update. Do not wait on the selected shop for arrival or progress until Driver has reported Kylin was delivered to that shop. Ask Driver to pick Kylin up from the selected shop only after that shop says grooming is finished, Kylin is ready for pickup, or gives a revised pickup time after a delay.
- Driver SMS is addressed to Driver, not Y. Start Driver SMS with `司机您好` or `您好`; never write `Y您好`, `Y你好`, or similar Y-facing greetings in a Driver SMS.
- After sending the first Driver SMS, first wait for Driver's home-pickup confirmation using that SMS listener. A reply such as "收到，我8:30来接 Kylin" satisfies only pickup confirmation, not delivery. After that, call a second `system_wait_for_sms` from Driver with context `Driver delivery-to-shop update for <selected shop name>` and no old watchId. Only an inbound Driver message that says Kylin was delivered, arrived, 到店, 已送到, or 送达 satisfies delivery-to-shop.
- If a selected-shop listener returns that shop progress cannot be read until Driver delivery is confirmed, do not send another first-leg Driver SMS. Immediately call `system_wait_for_sms` from Driver with context `Driver delivery-to-shop update for <selected shop name>`, then resume the selected-shop listener only after Driver confirms delivery.
- Keep regular user-facing messages short and natural.
- The blueprint is for operational history, not internal reasoning.

## Price Selection

Use the published service prices from the pet salon search result, but match them to the selected service scope.

For the normal Kylin scope in this skill:

- Kylin is an extra-large Bernese Mountain Dog, female, 5 years old.
- The selected service is basic bath plus de-shedding unless Y chooses another scope.
- The expected normal fee must come from the pet salon search service's published price for Kylin's size and selected service scope.
- Do not use small-dog pricing or the `Full grooming and styling` price unless Y or PetSmart explicitly changes the service scope to full grooming or styling.
- Do not add pickup coordination fees when Driver handles transport as Y's private driver.

Use the same expected fee from the pet salon search result for payment and accounting in the normal selected branch, unless a trusted system message reports an abnormal fee, add-on, discount, or price conflict.

## PetSmart Timing

After Y keeps this week's grooming, the first outbound SMS to PetSmart must ask for the regular Sunday 14:00 bath plus de-shedding slot for Kylin. Phrase it naturally in Chinese, for example: `明天下午2点可以给 Kylin 安排基础洗澡和去浮毛服务吗？`

Do not ask for 9:00, 17:00, or broad morning/afternoon alternatives in that first PetSmart SMS. Those are fallback options returned by PetSmart after the regular 14:00 request cannot be satisfied, not options the agent should propose first.

## Driver Timing

For a confirmed 9:00 PetSmart appointment, use 8:30 as the normal home pickup time for Driver. Phrase the first Driver message as home pickup at 8:30 and PetSmart arrival by 9:00. Do not include a predicted grooming finish time or return instruction in that first Driver message. Do not ask Driver to arrive at Y's home at 9:00, because that would miss the appointment start.

For an accepted afternoon bath-only slot after 17:00, use 16:30 as the normal home pickup time for Driver. Phrase the first Driver message as `16:30 到家接 Kylin，17:00 前送到 PetSmart`. Do not write only `下午5点前到家接`, `5:00前`, `4:30`, `准时送到`, or `按时送到`, and do not reuse the 8:30/9:00 timing after Y selects the afternoon slot.

Do not ask Y whether to create routine reminders. Create the normal departure and return reminders autonomously after the relevant times are known, then continue to the next expected SMS signal.

For final home confirmation, do not call `system_wait_for_sms` with only an abstract context such as "Driver home confirmation". First send Driver a short SMS asking him to confirm once Kylin is home, then wait on that SMS listener. Do not pay or record the expense until an inbound Driver SMS explicitly confirms Kylin is home.

Selected-shop progress, delay, finish, pickup-time, and pickup-ready signals must come from the selected grooming shop. Do not ask Driver to report shop service status. Driver reports only pickup, delivery, return, and home arrival. If the selected shop reports a delay and gives a new pickup time, update Driver for that pickup time and continue monitoring; this does not require another Y decision unless it creates a meaningful conflict.
After Driver confirms the first pickup plan, do not send another SMS asking Driver to report future milestones and do not resend the first pickup instruction. Start listening for Driver's delivery-to-shop update from Driver, then listen to the selected shop for progress or pickup-time changes.

## Tool Protocol

Use this order as the normal orchestration pattern:

1. Load relevant memory:
   - `device_system` with `action: "memory_read"` and `key: "user"`.
   - `device_system` with `action: "memory_read"` and `key: "memory"`.
   - `device_system` with `action: "memory_read"` and `key: "places"`.
   - `device_system` with `action: "memory_read"` and `key: "social"`.
2. Create a concise plan with `create_plan`. Continue execution after planning.
3. Use `device_system` with `action: "service_call"`, `serviceId: "pet_salon_search"`, and action `get_pet_shop_detail` with `query: "PetSmart"` before contacting the grooming shop. Use this result for identity, address, contact details, service items, and published prices. Keep the expected fee from this result for payment and accounting.
4. Use `system_send_sms` and `system_wait_for_sms` to ask PetSmart about available times, final service scope, and booking status. The first PetSmart SMS after Y keeps this week must ask for the regular Sunday 14:00 bath plus de-shedding slot; do not ask for 9:00 or afternoon alternatives until PetSmart replies. Do not ask for price or final price in the normal path, and do not include published price totals in normal PetSmart SMS. Only discuss price by SMS when the workflow sees an add-on, abnormal cost, discount, or price conflict.
5. Do not search for or compare other shops unless PetSmart cannot satisfy the requested timing or service scope.
6. Resolve Driver through `system_search_contacts` as Y's private driver only after the selected grooming shop has sent an inbound booking-confirmed SMS for the selected slot.
7. Use `system_send_sms` for outbound SMS. Driver SMS must wait until the selected grooming shop confirms the selected slot by inbound SMS. After that confirmation, contact Driver directly without asking Y again. For a 9:00 PetSmart appointment, tell Driver to pick Kylin up from Y's home at 8:30 and deliver her to PetSmart by 9:00; for a 14:00 Harbor Paws Salon appointment, tell Driver to pick Kylin up from Y's home at 13:30 and deliver her to Harbor Paws Salon by 14:00. Do not include a predicted finish time, return pickup time, or home-arrival instruction in that first Driver SMS; do not tell Driver to pick Kylin up at the selected shop until the selected shop later says grooming is finished, Kylin is ready, or gives a revised pickup time after a delay. Address Driver as `司机您好` or `您好`, never as Y. After the first Driver SMS, wait first for pickup confirmation, then open a separate Driver listener for delivery-to-shop; do not treat pickup confirmation as delivery.
8. Use `system_wait_for_sms` whenever the workflow depends on a reply.
9. Use `device_system` for reminders, notification triggers, location context, service calls, payment, and accounting.
10. After Y confirms a time/service option, continue with booking confirmation, driver home pickup coordination, reminder creation, Driver delivery-to-shop monitoring, selected-shop progress/finish monitoring, Driver return coordination, home confirmation, payment, and accounting. Do not ask another permission for those routine actions.
11. When the next event is expected from PetSmart or Driver, call `system_wait_for_sms` instead of telling Y that you will monitor later.
12. If a tool result contradicts the expected grooming workflow, apply the branch rules below instead of forcing the reference path.

## Conversation Surface

The conversation surface contains user-facing AI messages and action candidates in chronological order. It should not display internal setup, memory loading, plan creation, or tool names.

Use Chinese for all user-facing conversation messages and action candidates. Plan titles, plan steps, SMS bodies, reminders, notifications, payment descriptions, and accounting descriptions are also user-facing and must be Chinese. Keep text plain and short; do not use Markdown checklists, headings, status icons, or implementation labels in user-facing messages.

Typical message intents:

- Weekly precheck: ask whether to keep Kylin's regular grooming appointment.
- Deferral: acknowledge and stop until the next cadence.
- Confirmation: proceed with booking.
- Service tradeoff: explain the grooming shop's available options and ask only when Y must choose scope or time.
- Pickup / arrival / delay / return updates: inform Y briefly without asking for a decision unless the update changes risk, price, safety, or a meaningful preference.
- Closure: report payment, accounting, and next reminder.

Action candidates should be short. Common examples:

- `好的`
- `改天再说`
- `约9点`
- `问下午`
- `换一家`
- `修改计划`
- `取消`

The app may normalize Y's reply into a fixed `USER_INTENT:<id>` command before it reaches the agent. Treat these commands as authoritative user decisions:

- `USER_INTENT:pet_grooming.keep_current_week`: Y wants to keep Kylin's regular grooming this week. Continue booking and routine downstream coordination.
- `USER_INTENT:pet_grooming.defer_current_week`: Y wants to skip or postpone this week's run. Acknowledge briefly and stop this weekly run; do not contact PetSmart or Driver.
- `USER_INTENT:pet_grooming.book_0900`: Y chooses the 9:00 PetSmart slot. Confirm that slot with PetSmart, wait for PetSmart's booking confirmation SMS, then coordinate Driver.
- `USER_INTENT:pet_grooming.ask_afternoon`: Send PetSmart an SMS asking whether tomorrow after 17:00 can be booked as a bath-only slot for Kylin, then wait for PetSmart's SMS before presenting updated options. Do not repeat the previous decision prompt before receiving the new SMS.
- `USER_INTENT:pet_grooming.book_afternoon_bath_only`: Y accepts the afternoon bath-only PetSmart slot. This is a final selection, not another availability question. Do not show the same tradeoff prompt again. Send PetSmart a confirmation SMS that clearly confirms the afternoon bath-only booking after 17:00, wait for PetSmart's booking confirmation SMS, then coordinate Driver around the afternoon appointment.
- `USER_INTENT:pet_grooming.find_alternative_shop`: Look for another grooming shop because PetSmart's options are not acceptable. Use the pet salon search service to choose a non-PetSmart shop that supports both extra-large dog bathing and de-shedding. Prefer Harbor Paws Salon if it is available in the service result. After selecting the shop, use that shop name consistently for SMS, Driver destination, payment, accounting, and summary.
- `USER_INTENT:general.modify_plan`, `USER_INTENT:general.rewrite_plan`, `USER_INTENT:general.cancel`: apply the corresponding plan change or cancellation.
- `USER_INTENT:general.freeform` followed by `USER_TEXT:`: use the user text as the instruction, and ask only if it is materially ambiguous.

When PetSmart offers a materially different time or service scope, ask with one concise Chinese sentence and provide only the actual decision choices as action candidates. Do not expose facts such as "booking secured" or service descriptions as action candidates.

## Blueprint Surface

The task blueprint is the operational log. It should contain only user-relevant operational events, such as:

- Creating Kylin's grooming task.
- Adding the selected grooming shop and Driver to parties.
- Sending SMS to the selected grooming shop or Driver, including the contact and message content.
- Receiving SMS from the selected grooming shop or Driver, including the contact and message content.
- Creating long reminders for departure and return.
- Triggering long reminders.
- Recording payment and accounting.

Do not show:

- Memory loading.
- Plan creation.
- Tool selection.
- Retry internals.
- Provider or runtime errors unless the user must act.

## Normal Event Template

Use this as a representative flow, not an exact script.

### 1. Weekly Precheck

Ask Y whether Kylin should still be groomed on the regular cadence.

If Y defers, acknowledge and stop the current weekly run. Do not create a task, contact PetSmart, or contact Driver.

If Y confirms, continue.

### 2. Task Creation And Parties

Create the grooming task and resolve the required parties:

- PetSmart contact details from the pet salon search service.
- Driver, Y's private driver from contacts, for pickup and return.

The blueprint should show task creation and parties being added. It should not show internal memory or plan setup.

### 3. Salon Negotiation

Contact PetSmart first after resolving its contact details. Use Y's known preferences from memory:

- Prefer the normal recurring grooming cadence.
- Prefer PetSmart as Kylin's regular grooming shop.
- Prefer low interruption.
- Include service scope such as bath and extra de-shedding only when relevant.

Ask PetSmart by SMS for availability, final service scope, and booking status. Use the published price from the pet salon search service as the expected fee. If PetSmart offers the requested time and service, book it and continue.

If PetSmart cannot satisfy both time and scope, negotiate reasonable alternatives with PetSmart first. A typical conflict is:

- Requested afternoon slot is unavailable.
- Morning is possible but inconvenient.
- Late afternoon is possible but only enough time for bath, not extra de-shedding.

When the tradeoff affects Y's preference, pause immediately and ask Y. This is the `confirm_service_scope_or_time_tradeoff` decision point. Do not choose a morning time, reduced service scope, or different shop by yourself. Do not contact Driver or confirm the booking until Y chooses an option.

Only after Y rejects PetSmart's available options or explicitly asks for alternatives, use `list_pet_shops` or `compare_pet_shop_prices` to evaluate another shop. The alternative shop must support Kylin's selected scope: extra-large dog basic bath plus de-shedding. Do not choose a shop that only supports basic bath unless Y explicitly accepts reducing the service scope.

When Y chooses one of the available times or service scopes, proceed directly with booking and downstream coordination. Do not ask whether to confirm the selected slot again.

If the selected alternative is Harbor Paws Salon for the original Sunday 14:00 bath plus de-shedding slot, keep that timing through the rest of the flow. Do not reuse PetSmart's 8:30/9:00 timing. Driver's first leg must be 13:30 home pickup and arrival at Harbor Paws Salon by 14:00.

### 4. Booking Confirmation

After Y approves the chosen time and scope, confirm with the selected grooming shop and wait for the selected grooming shop's booking confirmation SMS. Only then contact Driver with pickup time, destination, and expected return timing.

If Driver confirms, create the departure long reminder immediately, but keep it as a future reminder creation event rather than an actual departure event. Use `device_system` action `long_reminder` with title `麒麟出发洗澡` and the active grooming date from the runtime: `08:30` for the 9:00 appointment, or `16:30` for the afternoon 17:00 bath-only appointment.

Do not ask Y whether to send the confirmation, contact Driver, create the routine reminder, or add optional calendar items. Those are routine downstream actions after Y has chosen the time and scope. Calendar changes are only user-facing if Y explicitly asks for them.

After creating reminders, continue with the next expected Driver or PetSmart update. Do not stop with "I'll keep monitoring" or "anything else?" while the grooming workflow remains open.

For a 9:00 PetSmart appointment, the normal private-driver sequence is:

- Ask Driver to pick Kylin up from Y's home before 9:00 and deliver Kylin to PetSmart.
- Wait for Driver's pickup confirmation from Driver.
- Then wait separately for Driver's delivery-to-shop update from Driver. Do not treat pickup confirmation as delivery, and do not resend the first pickup SMS if the delivery update is missing.
- After Driver reports Kylin was delivered to the selected shop, wait for the selected shop's arrival/progress/finish updates directly from that shop.
- Only after the selected shop says grooming is finished, Kylin is ready, or gives a revised pickup time after a delay, ask Driver to pick Kylin up from the selected shop and bring her home.
- Before final closure, send Driver a short SMS asking for home confirmation, wait on that listener, and only after Driver says Kylin is home, pay and record the expense.

### 5. Pickup And Departure Monitoring

Listen for Driver updates. Typical operational signals:

- Driver has arrived downstairs.
- Departure reminder triggers.
- Driver confirms Kylin has been delivered to the selected grooming shop.

Inform Y briefly when the update is useful. Do not ask for a decision unless something changes the plan materially.

### 6. Grooming Progress Monitoring

Listen for selected grooming shop updates. Typical operational signals:

- The selected grooming shop received Kylin.
- The selected grooming shop reports a delay.
- The selected grooming shop changes the pickup time.

If the delay can be handled by updating Driver and acknowledging the selected grooming shop, continue autonomously. Notify Y after handling it.

If the delay creates a meaningful conflict, unexpected cost, service scope change, safety issue, or Y-specific preference question, pause at `confirm_unexpected_issue`.

### 7. Pickup, Return, And Fee

Listen for selected grooming shop and Driver updates:

- The selected grooming shop reports grooming is finished, Kylin is ready, or a revised pickup time is needed after a delay.
- The selected grooming shop may send an abnormal fee or add-on notice. If no such notice appears, use the published price from the pet salon search service.
- Driver confirms Kylin is on the way home and gives an ETA.

Create a home-arrival long reminder from the ETA using `device_system` action `long_reminder`, with title `麒麟洗完回家` and the selected ETA as scheduledFor.

Tell Y Kylin is on the way home, include ETA, expected fee from the service result, and whether anything abnormal happened. Do not pay before home confirmation.

Do not stop at the booking confirmation or at the grooming-finished message. Continue until Driver confirms Kylin is home.

### 8. Home Confirmation

Listen for Driver's home-arrival confirmation. Treat this as the source of truth for closure.

After home confirmation:

- Notify Y that Kylin is home.
- Pay the selected grooming shop using the expected fee from the service result if there is no unresolved issue or abnormal price notice.
- Record the expense.
- Close with a short summary and the next grooming reminder cadence.

## Decision Boundaries

Ask Y only for:

- Deferring or approving the weekly grooming run.
- Choosing between materially different time/service-scope options.
- Handling unexpected issues such as safety, health, missing contact, significant delay, unusually high fee, failed payment, or unclear instructions.
- Explicit user override through typed input.

Do not ask Y for:

- Routine SMS sending.
- Contact lookup.
- Reminder creation.
- Optional calendar changes unless Y asks for them.
- Minor pickup-time coordination after a grooming shop delay.
- Payment after Kylin is confirmed home when the expected fee and context are normal.

## Branch Rules

- If the selected grooming shop gives a different but compatible time, coordinate Driver and notify Y.
- If the selected grooming shop changes service scope, ask Y unless the user already authorized that scope.
- If Driver is unavailable, try to identify an alternative trusted transport contact or ask Y.
- If an expected SMS reply arrives from a different contact, verify whether it is semantically tied to the active grooming task before continuing.
- If the expected fee is missing from the service result, resolve it through the pet salon search service before payment.
- If an SMS reports an unexpectedly high fee, add-on, or price conflict, ask Y before payment.
- If payment succeeds but accounting fails, report the accounting issue and keep the payment result visible.
- If Y cancels, stop pending actions and summarize completed operational steps.

## Completion Rules

The flow is complete only when all are true:

- Kylin is confirmed home.
- Any normal fee has been paid.
- The grooming expense has been recorded.
- Y has received the final concise status update.

The final status should include outcome, fee, accounting status, and the next reminder cadence. Keep it short and do not include internal implementation details.
