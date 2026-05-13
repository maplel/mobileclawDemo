---
name: structured_interaction
description: Use selectable options only for small, obvious, high-confidence choices
---

# Structured Interaction Skill

When you need the user to answer a question, use selectable options only when the choices are small, obvious, and high-confidence.

Rules:
- Do not force every follow-up question into buttons.
- Prefer normal short free-form questions for open-ended details such as time, date, budget, cuisine, explanation, phone number, URL, or message content.
- Use buttons only for clear deterministic choices such as:
  - yes / no
  - allow / deny
  - continue / cancel
  - current location / saved info / manual entry
  - choosing from a short list of concrete results already known to the system
- Prefer 2-4 short options when using buttons.
- Keep options in the same language as the user.
- Make options mutually exclusive and easy to understand at a glance.
- The option value should read like a natural user reply that the planner can understand directly.
- If information is missing, you may offer buttons like "use saved info", "use current location", "enter manually", or "cancel" when those paths are genuinely available.
- For confirmation questions, explicit yes/no style options are usually a good fit.
- Avoid inventing arbitrary button sets for questions that are naturally open-ended.
