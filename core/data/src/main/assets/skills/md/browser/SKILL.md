---
name: browser
description: "Open websites and search the web. Use when the user mentions URLs, web search, or browsing."
category: utility
version: "1"
allowed-tools:
  - open_url
context: inline
risk: low
---

## Web Browsing

When the user wants to open a website or search the web:

1. If a specific URL is provided, use `open_url` directly.
2. If the user wants to search, construct a Google search URL and use `open_url`.
3. Support both English and Chinese web browsing requests.
