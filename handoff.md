# MobileClaw Handoff

## Current State

- Refreshed: 2026-05-23, Asia/Shanghai.
- Workspace: `D:\MyCodeSpace\mobileclaw`
- Branch: `demonstration-scenario-integration`
- Remote branch: `origin/demonstration-scenario-integration`
- App package: `com.mobilebot`
- Last local Android device used: `481QFGDR222MJ`
- Do not clear app data during validation. API keys live in app settings.

## Non-Negotiable Boundary

Read `AGENTS.md` before changing scenario behavior.

For open-ended scenario replies, do not keep adding local phrase lists or runtime branches as the primary solution.

Default flow:

1. Strengthen persona/scenario/planner prompts and structured schemas.
2. Let the LLM normalize freeform language into a structured intent/disposition.
3. Let the agent/planner apply commands from that normalized result.
4. Keep runtime/system code limited to observed facts, lifecycle, state guards, and narrow protocol constraints.

Allowed deterministic handling:

- exact UI action keys,
- narrow protocol guards,
- ASR/noise cleanup,
- tests or fallbacks that do not become the semantic source of truth.

If normalization is wrong, fix the prompt/schema/candidate-intent boundary first.

## Implemented Scope

The branch contains the PetSmart grooming flow plus the first Qwen-backed Ella half-duplex voice-call path.

Voice path:

- Ella role call uses Qwen LLM through the OpenAI-compatible client.
- User voice turns use DashScope/Qwen ASR.
- Ella replies use DashScope/Qwen TTS.
- Call mode is push-to-talk half-duplex:
  - wait for Ella audio to finish,
  - press and hold `Talk` / `按住说话`,
  - release to submit the recorded turn.
- `VoiceCallSessionRuntime` stores turn-by-turn transcripts and emits `CallEndedEvent` with runtime transcript on hangup.
- Post-hangup planner can create/update the family-shopping task from the runtime transcript.

Recent fixes:

- Fixed push-to-talk gesture lifecycle so recording no longer cancels itself when `recording` changes.
- Hardened ASR normalization:
  - removed scenario keyword bias from the ASR prompt,
  - drops leaked ASR instruction text if Qwen returns prompt-like content,
  - strips leaked scenario suffixes only as ASR/noise cleanup.
- Removed feature-layer deterministic Ella refusal reply. Ella replies stay under the role-call LLM persona.
- Added Ella spouse/requester persona boundary:
  - Ella is the user's wife,
  - Ella asks the user or AIOS to help arrange household shopping,
  - Ella must not claim she is personally buying or executing the order.
- Family-shopping call-end task surface is planner-owned.
- `plannerPolicy.familyShoppingCallTranscriptPolicy` requires the LLM planner to normalize the transcript into `purchaseDisposition = accepted | declined | needs_clarification`.
- If normalized disposition is `declined`, planner must produce a `DONE` family-shopping task, omit purchase decisions, and avoid `BLOCKED` shopping candidates.
- Local shopping state sync treats a planner-produced `DONE`/no-decision shopping result as cancelled so later Ella/Ole events do not revive the shopping flow.
- `AGENTS.md` now stores the project boundary against phrase-list drift.

## Architecture Boundaries

SystemRuntime:

- Emits already-observed system facts.
- Manages call session lifecycle and runtime transcript storage.
- Does not decide open-ended scenario meaning or write task-card business copy.

Feature Chat:

- Renders UI and owns gesture/screen state.
- Calls runtime, ASR/TTS, role-call model, and planner interfaces.
- Must not contain scenario-specific business entities such as PetSmart, Kylin, Ella, Driver, or Ole.

Core Domain:

- Generic LLM/tool/protocol layer only.
- Must not contain one-hour scenario copy or scenario-specific actors.

Scenarios / skill assets:

- Own concrete scenario policy, persona prompts, task surfaces, narrow deterministic fallbacks, and tests.
- Scenario prompts/schemas are the first place to fix semantic normalization failures.

## Key Files

Workspace rules:

- `AGENTS.md`
- `handoff.md`

Voice/runtime:

- `core/systemruntime/src/main/java/com/mobilebot/systemruntime/VoiceCallSessionRuntime.kt`
- `core/systemruntime/src/main/java/com/mobilebot/systemruntime/SystemRuntimeEvent.kt`

Qwen role call / ASR / TTS:

- `core/network/src/main/java/com/mobilebot/network/RoleCallModel.kt`
- `core/network/src/main/java/com/mobilebot/network/DashScopeSpeechModels.kt`
- `core/network/src/main/java/com/mobilebot/network/NetworkModule.kt`
- `core/data/src/main/java/com/mobilebot/data/settings/UserSettingsResolution.kt`

UI / ViewModel:

- `feature/chat/src/main/java/com/mobilebot/chat/AgentExperienceModels.kt`
- `feature/chat/src/main/java/com/mobilebot/chat/AgentExperienceScreen.kt`
- `feature/chat/src/main/java/com/mobilebot/chat/AgentExperienceViewModel.kt`
- `feature/chat/src/main/res/drawable-nodpi/call_jessica.png`

Scenario policy:

- `scenarios/one-hour-flow/src/main/java/com/mobilebot/scenarios/onehour/OneHourScenarioFlow.kt`
- `scenarios/one-hour-flow/src/main/java/com/mobilebot/scenarios/onehour/OneHourScenarioPolicy.kt`
- `scenarios/family-shopping/src/main/java/com/mobilebot/scenarios/familyshopping/FamilyShoppingTaskSurface.kt`
- `scenarios/family-shopping/src/main/java/com/mobilebot/scenarios/familyshopping/FamilyShoppingUserTurn.kt`
- `scenarios/pet-grooming/src/main/java/com/mobilebot/scenarios/petgrooming/PetGroomingTaskSurface.kt`

Tests:

- `core/network/src/test/java/com/mobilebot/network/DashScopeSpeechModelsTest.kt`
- `core/systemruntime/src/test/java/com/mobilebot/systemruntime/VoiceCallSessionRuntimeTest.kt`
- `scenarios/one-hour-flow/src/test/java/com/mobilebot/scenarios/onehour/OneHourScenarioFlowTest.kt`
- `core/domain/src/test/java/com/mobilebot/domain/agent/ScenarioAgentTurnRunnerTest.kt`

## Last Validation

Previously passed before this handoff refresh:

```powershell
git diff --check
```

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :core:systemruntime:testDebugUnitTest :core:domain:testDebugUnitTest :core:network:testDebugUnitTest :scenarios:runtime:testDebugUnitTest :scenarios:one-hour-flow:testDebugUnitTest :feature:chat:compileDebugKotlin :app:assembleDebug --console=plain
```

Observed result: both passed.

This handoff-only change should still run `git diff --check` before commit/push.

## Next PC Setup

```powershell
git fetch origin
git switch demonstration-scenario-integration
git pull --ff-only origin demonstration-scenario-integration
git status --short --branch
```

Build:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug --console=plain
```

Install without clearing app data:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.mobilebot -c android.intent.category.LAUNCHER 1
```

If multiple Android devices are connected, explicitly pass `-s <serial>` to every `adb` command.

## Suggested Acceptance Test

PetSmart baseline:

1. Launch app and start the one-hour scenario.
2. Accept the PetSmart 14:00 slot.
3. Confirm Driver/reminder flow is created.
4. Continue through pickup, arrival, service started, and progress.
5. Confirm standby AI icon is dark when idle.

Ella refusal path:

1. Continue until Ella incoming call.
2. Answer with AI.
3. Wait for Ella TTS to finish.
4. Press and hold `Talk`, say a refusal such as `我不想买`, then release.
5. Confirm the user bubble contains only spoken text, not ASR prompt or scenario keywords.
6. Let Ella reply through TTS, then hang up.
7. Expected:
   - planner uses `familyShoppingCallTranscriptPolicy`,
   - family-shopping task is `DONE`,
   - subtitle is `通话中已确认暂不采购`,
   - no `BLOCKED` purchase decision remains,
   - later Ella/Ole shopping events do not revive the purchase flow.

Ella positive path:

1. Repeat Ella call.
2. Confirm purchase instead of refusing.
3. Hang up.
4. Expected:
   - family-shopping task is created from runtime transcript,
   - market delivery candidate can block on purchase confirmation,
   - confirming purchase sends Ole coordination and waits for order lock.

## Known Risks

- Voice path is half-duplex PTT. It does not implement full-duplex AEC or local VAD.
- Runtime call transcript storage is in memory only; app process death before hangup/planner handling can lose the transcript.
- TTS is non-streaming; Ella audio starts only after the full TTS response is ready.
- Device validation for the latest pushed commit should be repeated on the next PC.
