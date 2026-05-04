---
name: contacts
description: "Find contacts and place phone calls."
category: communication
version: "1"
allowed-tools:
  - search_contacts
  - dial_number
context: inline
risk: medium
requires:
  permissions:
    - android.permission.READ_CONTACTS
---

## Contact Lookup & Calling

When the user wants to call someone or find a contact:

1. Use `search_contacts` to look up the person by name.
2. If multiple matches found, present the list and ask the user to choose.
3. If no matches found, ask for a phone number directly.
4. Use `dial_number` with the confirmed phone number.
