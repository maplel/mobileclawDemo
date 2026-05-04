---
name: maps
description: "Search places, get directions, and navigate. Use when the user mentions maps, navigation, or route finding."
category: transport
version: "1"
allowed-tools:
  - open_map
context: inline
risk: low
---

## Map & Navigation

When the user needs map or navigation help:

1. Use `open_map` with the search query or destination.
2. For place searches: pass the place name or category (e.g. "附近餐厅", "nearest gas station").
3. For navigation: pass the destination address or place name.
