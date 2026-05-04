---
name: messaging
description: "Send SMS messages and find contacts for messaging."
category: communication
version: "1"
allowed-tools:
  - send_sms
  - search_contacts
context: inline
risk: medium
requires:
  permissions:
    - android.permission.READ_CONTACTS
---

## SMS Messaging

When the user wants to send a text message:

1. If a name is given instead of a number, use `search_contacts` first to resolve the phone number.
2. Never invent or guess phone numbers.
3. Use `send_sms` with the resolved phone number and message body.
4. If the message body is missing, ask the user what to send.
