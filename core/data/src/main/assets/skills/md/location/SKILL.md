---
name: location
description: "Get the device's current GPS location and coordinates."
category: utility
version: "1"
allowed-tools:
  - get_current_location
context: inline
risk: low
requires:
  permissions:
    - android.permission.ACCESS_FINE_LOCATION
---

## Device Location

When the user asks where they are or needs their current location:

1. Use `get_current_location` to get GPS coordinates.
2. Present the latitude and longitude to the user.
3. If the user wants a map view, suggest using the maps skill.
