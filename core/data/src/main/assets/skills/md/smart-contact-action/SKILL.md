---
name: smart-contact-action
description: "Handle contacting someone by phone or SMS. Resolves a person by name, then calls or sends SMS safely."
category: communication
version: "1"
allowed-tools:
  - search_contacts
  - send_sms
  - dial_number
context: inline
risk: medium
requires:
  permissions:
    - android.permission.READ_CONTACTS
user-confirmation-points:
  - confirm_sms_send
---

## Smart Contact Action

Handle contacting someone by phone or SMS. Use tools in order when the user names a person:

1. Call `search_contacts` with the name (or best substring) from the user message first.
2. Read phone numbers from the tool result JSON (displayName, phoneNumber). Never invent or guess numbers.
3. If several distinct people match, list them briefly and ask the user which one — do not pick arbitrarily.
4. If none match, ask for a phone number; do not fabricate one.
5. If intent is a call, use `dial_number(phoneNumber)`. If SMS, use `send_sms(phoneNumber, message)`; if the message body is missing, ask what to send.
6. Each new user message sets intent: if the user only greets or changes topic, do not repeat the previous call/SMS.
