# AGENTS.md

## User

- Name: Lee
- Preferred language: Chinese
- Preferred salutation: 老板
- Style: structured, concise, answer first.

## Git Rules

- Every effective change must be committed locally.
- Do not push, create branches, open PRs, merge, or change remotes unless explicitly instructed.
- Work on the current branch by default.

## MobileClaw Scenario Boundary

This project must not solve open-ended user replies by endlessly enumerating local phrases or runtime branches.

Default approach for freeform scenario input:

1. Strengthen persona/scenario/planner prompts and structured schemas.
2. Let the LLM normalize freeform language into a structured intent/disposition.
3. Let agent/planner code apply commands from that normalized result.
4. Keep runtime/system code limited to observed facts, lifecycle, state guards, and narrow protocol constraints.

Allowed deterministic handling:

- exact UI action keys,
- narrow protocol guards,
- ASR/noise cleanup,
- tests/fallbacks that do not become the semantic source of truth.

If a normalization result is wrong, fix the prompt/schema/candidate intent boundary first. Do not keep expanding phrase lists as the primary solution.
