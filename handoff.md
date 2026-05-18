# MobileClaw Handoff

## Current State

- Workspace used for this handoff: `E:\MyCodeSpace\mobileclaw`
- Branch: `demonstration-scenario-integration`
- Remote to pull on the next PC: `origin/demonstration-scenario-integration`
- App package: `com.mobilebot`
- Last device seen here: `481QFGDR222MJ`
- Do not clear app data during device validation. API keys live in app settings.

Latest branch base before this handoff work:

```text
29b5bfd Keep scenario progress strip persistent
47283eb Clarify empty scenario runtime status
3c5d4c7 Stabilize scenario progress status copy
8e323f6 Integrate system runtime scenario scheduler
651e51c Prefer official repositories on CI
```

## What Was Just Implemented

This handoff contains the first implementation of the Ella semi-duplex call plan.

Implemented:

- Added `VoiceCallSessionRuntime` in `core/systemruntime`.
  - Manages call session state.
  - Records user/caller turns.
  - Produces runtime audio refs like `runtime-call:<sessionId>`.
  - Stores generated call transcripts in memory.
- Extended runtime call events:
  - `IncomingCallEvent` now carries optional `callSessionId` and `personaId`.
  - `CallEndedEvent` now carries optional `callSessionId`.
  - Scheduled `call_ended` can be marked delivered so manual hangup does not duplicate the scripted call-end event.
- Updated transcript lookup:
  - `AssetCallTranscriptRepository` first checks runtime-generated transcripts.
  - Static `AGENT_CONTEXT.json` transcripts remain as fallback/test oracle.
- Added role-call model boundary in `core/network`.
  - `RoleCallModel`
  - `SpeechRecognizer`
  - `SpeechSynthesizer`
  - `QwenRoleCallModel`
  - Current role model uses the existing OpenAI-compatible client with `qwen-turbo`.
- Added Ella persona policy in `scenarios/one-hour-flow`.
  - Persona and family-shopping fallback copy stay in scenario policy.
  - `feature/chat` does not contain concrete scenario names after cleanup.
- Updated call UI and ViewModel.
  - Answering no longer auto-advances to call end.
  - Active call overlay now shows turn-by-turn transcript.
  - User can type one turn, submit it, wait for caller reply, then hang up.
  - Hangup publishes a runtime `CallEndedEvent`, which re-enters the existing planner path through `transcribe_call`.
- Added `RECORD_AUDIO` permission for the future real voice path.

Important limitation:

- Real mic ASR and TTS playback are not implemented yet.
- Current v1 is a typed fallback that proves the call-session, role-model, transcript-store, and post-hangup task-card pipeline.
- Next step is to bind `SpeechRecognizer` / `SpeechSynthesizer` to DashScope realtime ASR/TTS WebSocket.

## Architecture Boundaries To Preserve

SystemRuntime:

- Emits already-observed system facts.
- Manages call session lifecycle and raw/generated transcript storage.
- Does not decide scenario meaning or write task-card business logic.

Feature Chat:

- Renders UI.
- Owns gesture handling and screen state.
- Calls runtime and planner interfaces.
- Must not contain scenario entities such as PetSmart, Kylin, Ella, Driver, etc.

Core Domain:

- Generic LLM/tool/protocol layer only.
- Must not contain scenario-specific entities or one-hour-flow copy.

Scenarios / skill assets:

- Own concrete scenario policy, persona prompts, fallback scenario copy, task surfaces, and deterministic oracle code.

## Key Files Changed

Runtime/session:

- `core/systemruntime/src/main/java/com/mobilebot/systemruntime/VoiceCallSessionRuntime.kt`
- `core/systemruntime/src/main/java/com/mobilebot/systemruntime/SystemRuntimeEvent.kt`
- `core/systemruntime/src/main/java/com/mobilebot/systemruntime/SystemRuntime.kt`
- `core/systemruntime/src/main/java/com/mobilebot/systemruntime/SystemRuntimeScheduler.kt`

Transcript lookup:

- `core/data/src/main/java/com/mobilebot/data/context/AssetCallTranscriptRepository.kt`
- `core/data/build.gradle.kts`

Role model and future voice interfaces:

- `core/network/src/main/java/com/mobilebot/network/RoleCallModel.kt`
- `core/network/src/main/java/com/mobilebot/network/NetworkModule.kt`

UI and ViewModel:

- `feature/chat/src/main/java/com/mobilebot/chat/AgentExperienceModels.kt`
- `feature/chat/src/main/java/com/mobilebot/chat/AgentExperienceScreen.kt`
- `feature/chat/src/main/java/com/mobilebot/chat/AgentExperienceViewModel.kt`

Scenario policy:

- `scenarios/one-hour-flow/src/main/java/com/mobilebot/scenarios/onehour/OneHourScenarioFlow.kt`
- `scenarios/one-hour-flow/src/main/java/com/mobilebot/scenarios/onehour/OneHourScenarioPolicy.kt`

Tests:

- `core/systemruntime/src/test/java/com/mobilebot/systemruntime/VoiceCallSessionRuntimeTest.kt`
- `core/systemruntime/src/test/java/com/mobilebot/systemruntime/SystemRuntimeSchedulerTest.kt`
- `core/network/src/test/java/com/mobilebot/network/QwenRoleCallModelTest.kt`
- `scenarios/one-hour-flow/src/test/java/com/mobilebot/scenarios/onehour/OneHourScenarioFlowTest.kt`

## Validation Already Run

Static boundary scans:

```powershell
rg -n "timelineQueue|timelineDigest|upcoming:|当前事件 id|时间队列" core feature scenarios -S
rg -n "f[a]ke|F[a]ke|d[e]mo|D[e]mo" core feature scenarios app -S
rg -n "Kylin|PetSmart|Ella|Driver|冷链|健康补给|麒麟" feature\chat\src\main\java core\domain\src\main\java -S
```

Observed result:

- `timelineQueue/upcoming` only appears in negative test assertions.
- `fake/demo` scan has no matches.
- `feature/chat/src/main/java` and `core/domain/src/main/java` have no scenario-entity matches after cleanup.

Gradle validation:

```powershell
.\gradlew :core:systemruntime:testDebugUnitTest :core:domain:testDebugUnitTest :core:network:testDebugUnitTest :scenarios:runtime:testDebugUnitTest :scenarios:one-hour-flow:testDebugUnitTest assembleDebug
```

Observed result:

- Build successful.

Device validation:

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.mobilebot 1
adb exec-out uiautomator dump /dev/tty
```

Observed result:

- Device `481QFGDR222MJ` was online.
- APK installed successfully.
- App launched.
- Initial UI showed `13:00` and bottom progress strip `待机中`.
- PetSmart 13:05 accept path still appears and can send PetSmart/Driver logs.

Device validation not yet completed:

- Full Ella call typed-turn flow on device.
- Post-hangup task-card generation from `runtime-call:<sessionId>`.
- Real ASR/TTS path, because it is not implemented yet.

## Next PC Setup

On the other PC:

```powershell
git fetch origin
git switch demonstration-scenario-integration
git pull --ff-only origin demonstration-scenario-integration
git status --short --branch
```

Then run:

```powershell
.\gradlew :core:systemruntime:testDebugUnitTest :core:domain:testDebugUnitTest :core:network:testDebugUnitTest :scenarios:runtime:testDebugUnitTest :scenarios:one-hour-flow:testDebugUnitTest assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Keep app data during install.

## Suggested Next Steps

1. Finish device validation for the typed Ella call flow.
   - Start one-hour scenario.
   - Fast-forward to 13:05.
   - Accept PetSmart slot.
   - Continue until Ella incoming call.
   - Tap `接听`.
   - Submit one typed turn in the call overlay.
   - Confirm Qwen/fallback caller reply appears.
   - Tap `挂断`.
   - Confirm family shopping task card is created from runtime transcript.

2. If typed flow is stable, implement real voice I/O behind the existing interfaces.
   - `SpeechRecognizer`: DashScope `qwen3-asr-flash-realtime`.
   - `SpeechSynthesizer`: DashScope `qwen3-tts-flash-realtime` or `qwen3-tts-instruct-flash-realtime`.
   - Keep v1 half-duplex: record while user speaks, stop recording while caller audio plays.
   - Do not add full-duplex AEC/VAD complexity yet.

3. Preserve black-box script boundary.
   - Local clock + SystemRuntime remain the demo script/fact source.
   - LLM caller and AIOS planner only see current observed facts/session transcript.
   - Do not expose future queue, script directory, or pending event list to planner.

4. Add focused tests after ASR/TTS integration.
   - ASR failure -> typed fallback still works.
   - TTS failure -> text transcript still works.
   - Hangup stores transcript once.
   - Scheduled `ella-call-ended` does not fire again after manual hangup.

## Known Risks

- Current `QwenRoleCallModel` always asks `qwen-turbo`; if the user-selected provider is not DashScope/Qwen, the model call may fail. This is acceptable for this step because the target explicitly asks for Qwen. Next step can add a provider check or a dedicated Qwen settings surface.
- Runtime transcript store is in memory only. It is fine for the demo session; persistence is unnecessary unless the app process is killed before `transcribe_call`.
- `RECORD_AUDIO` permission is declared but no runtime permission UX has been implemented yet.
- Current device check was partial. Do not treat this as full end-to-end voice validation.
