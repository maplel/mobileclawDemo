# MobileClaw Handoff

## Current State

- Workspace used for this handoff: `D:\MyCodeSpace\mobileclaw`
- Branch: `demonstration-scenario-integration`
- Remote to pull on the next PC: `origin/demonstration-scenario-integration`
- App package: `com.mobilebot`
- Last device used here: `481QFGDR222MJ`
- Do not clear app data during validation. API keys live in app settings.

## What Is Implemented

The current branch contains the PetSmart grooming flow plus the first real Ella semi-duplex voice-call path.

Implemented voice path:

- Ella role call uses Qwen LLM through the existing OpenAI-compatible client.
- User voice turns use DashScope/Qwen ASR.
- Ella replies use DashScope/Qwen TTS.
- Call mode is half-duplex push-to-talk:
  - wait for Ella to finish speaking,
  - press and hold `Talk` / `按住说话`,
  - release to submit the recorded turn.
- `VoiceCallSessionRuntime` stores turn-by-turn runtime transcripts and publishes `CallEndedEvent` with the runtime transcript on hangup.
- Post-hangup planner path can create or update the family-shopping task from the runtime transcript.

Recent fixes included in this handoff:

- Fixed the push-to-talk gesture lifecycle. The record button no longer cancels its own press when `recording` changes.
- Hardened ASR transcript normalization:
  - removed scenario keyword bias from the ASR prompt,
  - drops leaked ASR instruction text if Qwen returns the prompt itself,
  - strips leaked scenario suffixes after clear purchase refusal.
- Removed local deterministic Ella refusal reply from `feature/chat`; Ella replies continue through the role-call LLM persona.
- Fixed the family-shopping refusal boundary:
  - Ella call-end task surfaces are planner-owned, matching the PetSmart pattern where LLM semantics are normalized before agent commands are applied,
  - `plannerPolicy.familyShoppingCallTranscriptPolicy` now requires the LLM planner to normalize the transcript into `purchaseDisposition = accepted | declined | needs_clarification`,
  - if the normalized disposition is `declined`, the planner must create/update the family-shopping task as `DONE`, omit purchase decisions, and avoid `BLOCKED` shopping candidates,
  - local explicit refusal handling remains only as a narrow fallback/state-machine guard, not as the semantic source of truth.

## Architecture Boundaries To Preserve

SystemRuntime:

- Emits already-observed system facts.
- Manages call session lifecycle and runtime transcript storage.
- Does not decide scenario meaning or write task-card business logic.

Feature Chat:

- Renders UI and owns gesture/screen state.
- Calls runtime, ASR/TTS, role-call model, and planner interfaces.
- Must not contain scenario entities such as PetSmart, Kylin, Ella, Driver, Ole, etc.

Core Domain:

- Generic LLM/tool/protocol layer only.
- Must not contain scenario-specific entities or one-hour-flow copy.

Scenarios / skill assets:

- Own concrete scenario policy, persona prompts, fallback copy, deterministic oracle code, and task surfaces.

## Key Files

Voice/runtime:

- `core/systemruntime/src/main/java/com/mobilebot/systemruntime/VoiceCallSessionRuntime.kt`
- `core/systemruntime/src/main/java/com/mobilebot/systemruntime/SystemRuntimeEvent.kt`

Qwen role call / ASR / TTS:

- `core/network/src/main/java/com/mobilebot/network/RoleCallModel.kt`
- `core/network/src/main/java/com/mobilebot/network/DashScopeSpeechModels.kt`
- `core/network/src/main/java/com/mobilebot/network/NetworkModule.kt`

UI / ViewModel:

- `feature/chat/src/main/java/com/mobilebot/chat/AgentExperienceModels.kt`
- `feature/chat/src/main/java/com/mobilebot/chat/AgentExperienceScreen.kt`
- `feature/chat/src/main/java/com/mobilebot/chat/AgentExperienceViewModel.kt`

Scenario policy:

- `scenarios/one-hour-flow/src/main/java/com/mobilebot/scenarios/onehour/OneHourScenarioFlow.kt`
- `scenarios/one-hour-flow/src/main/java/com/mobilebot/scenarios/onehour/OneHourScenarioPolicy.kt`
- `scenarios/family-shopping/src/main/java/com/mobilebot/scenarios/familyshopping/FamilyShoppingTaskSurface.kt`
- `scenarios/family-shopping/src/main/java/com/mobilebot/scenarios/familyshopping/FamilyShoppingUserTurn.kt`

Tests:

- `core/network/src/test/java/com/mobilebot/network/DashScopeSpeechModelsTest.kt`
- `core/systemruntime/src/test/java/com/mobilebot/systemruntime/VoiceCallSessionRuntimeTest.kt`
- `scenarios/one-hour-flow/src/test/java/com/mobilebot/scenarios/onehour/OneHourScenarioFlowTest.kt`

## Validation Run Before Handoff

```powershell
git diff --check
```

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :core:systemruntime:testDebugUnitTest :core:domain:testDebugUnitTest :core:network:testDebugUnitTest :scenarios:runtime:testDebugUnitTest :scenarios:one-hour-flow:testDebugUnitTest :feature:chat:compileDebugKotlin :app:assembleDebug --console=plain
```

Observed result:

- `git diff --check` passed.
- Gradle validation passed.

## Next PC Setup

```powershell
git fetch origin
git switch demonstration-scenario-integration
git pull --ff-only origin demonstration-scenario-integration
git status --short --branch
```

Build and install without clearing app data:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug --console=plain
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.mobilebot -c android.intent.category.LAUNCHER 1
```

If multiple Android devices are connected, explicitly pass `-s <serial>` to every `adb` command.

## Suggested Device Test

1. Launch the app and start the one-hour scenario.
2. Accept the PetSmart 14:00 slot.
3. Continue until Ella incoming call.
4. Answer with AI.
5. Wait for Ella TTS to finish.
6. Press and hold `Talk`, say a refusal such as `我不想买`, then release.
7. Confirm the user bubble contains only the spoken refusal, not ASR prompt text or scenario keywords.
8. Let Ella reply through TTS, then hang up.
9. Expected post-hangup result:
   - planner creates/updates the family-shopping task from the normalized call transcript,
   - family-shopping task is `DONE`,
   - subtitle is `通话中已确认暂不采购`,
   - no `BLOCKED` purchase decision is created,
   - later Ella/Ole shopping follow-up events do not revive the purchase flow.

Also test a positive path:

1. Repeat Ella call.
2. Confirm purchase instead of refusing.
3. Hang up.
4. Expected result: family-shopping task is created from runtime transcript and can continue to market delivery candidate / purchase confirmation.

## Known Risks

- Current voice path is half-duplex PTT. It does not implement full-duplex AEC or local VAD.
- Runtime call transcript storage is in memory only. It is acceptable for the demo flow unless the app process is killed before hangup/planner handling.
- TTS is non-streaming in the current implementation, so Ella audio starts only after the full TTS response is ready.
- Device validation for this exact final commit should be repeated on the next PC after pulling.
