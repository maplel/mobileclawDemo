# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MobileBot is an Android multi-module Agent application. It runs a local AI agent on Android devices that communicates with LLMs via the OpenAI `tool_calls` protocol. The agent can invoke tools (browser, maps, contacts, SMS, etc.) and execute skills (SKILL.md-based task definitions) to accomplish complex multi-step tasks on the device.

## Build System & Configuration

- **Gradle Kotlin DSL** (`settings.gradle.kts`, `build.gradle.kts`)
- **JDK 17** required; use Android Studio's embedded JBR 17 (standalone JDK 21 can cause SSL issues)
- **SDK**: compileSdk/targetSdk 35, minSdk 26
- **Kotlin 2.0.21**, AGP 8.7.2, Compose BOM 2024.10.01
- **Gradle** 8.9 via wrapper
- **gradle.properties**: `-Xmx2048m` heap, TLS 1.2/1.3 enforced, 120s connect/read timeouts for slow networks
- **Mirrors**: Aliyun mirrors configured in `settings.gradle.kts` for restricted networks
- **Windows**: Use `.\gradlew.bat` instead of `./gradlew`

### Essential commands

```bash
# Full build
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Run all JVM unit tests
./gradlew test

# Module-specific tests
./gradlew :core:domain:testDebugUnitTest
./gradlew :core:bridge:testDebugUnitTest
./gradlew :core:data:testDebugUnitTest

# Android Lint
./gradlew lintDebug

# Single test class
./gradlew :core:domain:testDebugUnitTest --tests "com.mobilebot.domain.skill.EnhancedSkillParserTest" --no-daemon

# Memory-constrained (common on Windows):
$env:GRADLE_OPTS="-Xmx1g -XX:+UseSerialGC"
./gradlew test --no-daemon
```

### CI (GitHub Actions)

Three jobs: `assembleDebug`, `test` (all JVM unit tests), `lintDebug`. Local equivalent:
```bash
./gradlew assembleDebug && ./gradlew test && ./gradlew lintDebug
```

## Module Structure

| Module | Responsibility | Key files |
|---|---|---|
| `:app` | Application entry, Hilt app-level DI, foreground service, notification listener | `MobileBotApplication`, `MainActivity`, `AgentForegroundService` |
| `:feature:chat` | Compose UI - chat screen, settings screen, navigation | `ChatScreen`, `ChatViewModel`, `SettingsScreen`, `MobileBotNavHost` |
| `:core:model` | Shared DTOs - messages, tools, stream events | `Messages.kt`, `ToolModels.kt`, `StreamEvents.kt` |
| `:core:bus` | Agent-to-UI message bus (`OutboundMessage`/`InboundMessage`) | `MessageBus.kt` |
| `:core:network` | OpenAI-compatible LLM client, SSE streaming | `OpenAiCompatibleClient.kt`, `NanobotStreamClient.kt` |
| `:core:bridge` | Android system capability abstraction + virtual bridge for testing | `DeviceCapabilityBridge`, `Android*Bridge`, `Virtual*Bridge`, `SwitchableDeviceCapabilityBridge` |
| `:core:domain` | Agent loop, tool registry, skill system, plan mode, subtask execution, memory | `AgentLoop.kt`, `ToolCallAgentLoop.kt`, `ToolRegistry.kt`, `SkillExecutor.kt`, `PlanManager.kt`, `MemoryFacade.kt` |
| `:core:data` | Room database, settings storage, skill asset loading, WorkManager heartbeats, virtual data bootstrapper | `SessionRepositoryImpl`, `UserSettingsRepository`, `SkillAssetLoader`, `UserProfileStoreImpl` |

### Dependency direction

```
app → feature:chat, core:data, core:domain, core:bridge
feature:chat → core:model, core:bus, core:domain, core:data, core:network
core:domain → core:model, core:bus, core:bridge, core:network
core:data → core:model, core:bridge, core:domain, core:network
core:bridge, core:model — bottom-level, cross-cutting
```

**Do not** add UI logic to `:app` or Android-specific implementations to `:core:domain`.

## Agent Architecture

### Core execution flow

1. `ChatViewModel.send()` → `ForegroundController` → `AgentLoop.processUserMessage()` (per-chat mutex, delegates to `ToolCallAgentLoop`)
2. `ToolCallAgentLoop` builds system prompt (with skill catalog from `SkillRegistry`) + tool definitions from `ToolRegistry`
3. `LlmConfigurator.beforeRequest()` syncs API key / base URL / model to the LLM client
4. Sends to LLM via OpenAI `tool_calls` protocol (`LlmClient.chat()` blocking or `.chatStream()` SSE)
5. If LLM returns `tool_calls`: execute each tool (through `ToolPolicyEngine` capability/foreground/connectivity checks + `ToolPermissionGate` user approval) → append results → loop
6. If LLM calls `use_skill`: `SkillTool` routes to `SkillExecutor` → load SKILL.md → inline (inject guidance) or fork (spawn SubAgentRunner)
7. If LLM calls `create_plan`: `CreatePlanTool` stores in `PlanManager`, loop pauses, sends `TodoListCard` + `ActionPrompt` to UI, waits for user approve/edit/cancel
8. Tool results flow back to UI via `MessageBus.outbound` SharedFlow

### Key components

| Component | File | Role |
|---|---|---|
| `AgentLoop` | `core/domain/AgentLoop.kt` | Top-level entry with per-session mutex; delegates to ToolCallAgentLoop |
| `ToolCallAgentLoop` | `core/domain/agent/ToolCallAgentLoop.kt` | Main tool_calls iteration: build prompt → LLM → execute tools → loop |
| `ToolRegistry` | `core/domain/tools/ToolRegistry.kt` | Collects all tools via Hilt `@IntoSet`, filters by device capabilities, executes |
| `ToolPolicyEngine` | `core/domain/tools/ToolPolicyEngine.kt` | Checks capability, foreground, and connectivity constraints before execution |
| `ToolPermissionGate` | `core/domain/tools/ToolPermissionGate.kt` | User approval gate for tools with `requiresUserApproval: true` |
| `SkillTool` | `core/domain/tools/SkillTool.kt` | The `use_skill` tool — LLM routes to skills through this |
| `SkillExecutor` | `core/domain/skill/SkillExecutor.kt` | Executes SKILL.md skills: inline (context injection) or fork (sub-agent) |
| `SkillRegistry` | `core/domain/skill/SkillRegistry.kt` | Multi-source skill registry with priority override (Bundled < Cloud < User) |
| `PlanManager` | `core/domain/agent/PlanManager.kt` | Plan state machine: NONE → PENDING → EXECUTING → DONE |
| `SubtaskExecutor` | `core/domain/subtask/SubtaskExecutor.kt` | Creates/manages subtasks with independent sessions and shared facts |
| `MessageBus` | `core/bus/MessageBus.kt` | SharedFlow-based Agent→UI channel (`inbound`/`outbound` flows) |
| `MemoryDigestBuilder` | `core/domain/memory/MemoryDigestBuilder.kt` | Builds working memory digest for system prompt injection |
| `PersistentMemoryManager` | `core/domain/memory/PersistentMemoryManager.kt` | Interface for long-term memory persistence |
| `WorkspaceContextManager` | `core/domain/memory/WorkspaceContextManager.kt` | Manages workspace-scoped context for sessions |
| `EventRouter` | `core/domain/event/EventRouter.kt` | Routes external events (collision, calendar, health, smart home) into the agent |
| `ServiceManager` | `core/domain/service/ServiceManager.kt` | Manages third-party service registration, authorization, and invocation |
| `UserProfileStore` | `core/domain/profile/UserProfileStore.kt` | Encrypted user data store (insurance, membership, preferences, etc.) |
| `SystemPromptBuilder` | `core/domain/agent/SystemPromptBuilder.kt` | Builds system prompt with skill catalog, memory digest, and tool definitions |
| `LlmConfigurator` | `core/domain/LlmConfigurator.kt` | Syncs API key / base URL / model to LLM client before each request |

### Memory System

- `MemoryDigestBuilder` constructs a working memory digest injected into each system prompt turn
- `PersistentMemoryManager` interface for long-term fact/memory storage (Room-backed via `MemoryFileRepository`)
- `WorkspaceContextManager` manages workspace-scoped context for agent sessions
- Facts can be published/shared between subtasks via `publish_fact` / `publishFact`

### Plan Mode

For complex multi-step tasks, LLM calls `create_plan` to generate a structured plan with title + steps. The loop pauses, UI shows `TodoListCard` + `ActionPrompt` (execute/edit/cancel). On approval, executes steps sequentially, updating `TodoStatus` (PENDING → RUNNING → COMPLETED). User can edit via free text or cancel.

### Event System

External events (Tesla collision, calendar reminders, health alerts, smart home, geofence) are generated by `EventSource` implementations in `core:bridge`, routed through `EventRouter` which publishes `InboundMessage` to the `MessageBus`, waking the agent loop for proactive handling.

### Data persistence

- Chat messages in **Room** database (`AppDatabase` with `SessionDao`, `MessageDao`, `MemoryDao`)
- Primary chat session: `chatId = "main"` → session key `mobile:main`
- User settings (API key, base URL, model) via DataStore/SharedPreferences with fallback chain
- Working memory and facts persisted in Room (`MemoryEntity` table via `MemoryFileRepository` + `PersistentMemoryManager`)
- User profile data encrypted at rest via `UserProfileStoreImpl`
- DB sync utility: `scripts/pull-mobilebot-db.ps1` pulls `mobilebot.db` from device

### Android System Integration Points

- `MainActivity` handles `ACTION_SEND` text shares → writes to `filesDir/workspace/shared/incoming.txt`
- `MobileBotNotificationListenerService` mirrors system notifications to `NotificationHistoryStore`
- `AgentForegroundService` keeps agent alive during execution with a persistent notification
- `HeartbeatScheduler` uses WorkManager for optional periodic heartbeat tasks

## Key Patterns

### Hilt DI / Module Binding

| Module | File | Pattern |
|---|---|---|
| `DomainToolModule` | `core/domain/DomainModule.kt` | `@Binds @IntoSet Tool` — 30+ tool bindings |
| `BridgeModule` | `core/bridge/di/BridgeModule.kt` | `@Provides @Singleton DeviceCapabilityBridge` — switches between real/virtual |
| `NetworkModule` | `core/network/NetworkModule.kt` | Binds `LlmClient` to `OpenAiCompatibleClient` |

The `BridgeModule` companion decides at startup whether to use `SwitchableDeviceCapabilityBridge` (if any bridge is virtual) or `AndroidDeviceCapabilityBridge` directly.

### Tool definition pattern

Tools implement the `Tool` interface and bind via Hilt `@IntoSet`. Each declares:
- `name` — unique string identifier the LLM uses to call it
- `definition: ToolDefinition` — JSON schema (`name`, `description`, `parametersSchema` as raw JSON string)
- `requiredCapabilities` — set of Android permissions/features needed
- `executionPolicy: ToolExecutionPolicy` — `requiresUserApproval`, `requiresForeground`, `requiresConnectivity`, `hasSideEffects`
- `risk: ToolRisk` — `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `suspend fun execute(argumentsJson: String): ToolResult` — returns `ToolResult(ok, message, dataJson?)`

**Critical rule**: Tools access Android APIs through `DeviceCapabilityBridge` interface (`.files`, `.contacts`, `.browser`, etc.), never directly through Android SDK calls.

### Available tools (30+)

| Tool | Purpose |
|---|---|
| `use_skill` | Invoke skill system (LLM routing entry) |
| `create_plan` | Generate structured plan for complex tasks |
| `open_url` / `open_map` | Browser / Maps |
| `search_contacts` / `dial_number` / `send_sms` | Contacts & telephony |
| `get_current_location` | GPS location |
| `open_camera` | Camera |
| `copy_to_clipboard` / `share_text` | Clipboard & sharing |
| `read_sandbox_file` / `write_sandbox_file` | Sandbox file I/O |
| `list_notifications` / `create_notification` | Notifications |
| `create_calendar_event` / `query_calendar` | Calendar |
| `deep_link_app` / `open_app` | App launching |
| `get_device_state` | Device status |
| `play_media` | Media playback |
| `call_service` | Third-party service invocation |
| `spawn_subtask` / `check_subtask` / `publish_fact` | Subtask orchestration |
| `read_user_profile` | User preferences |
| `open_settings` / `set_alarm` / `set_timer` / `toggle_flashlight` | System operations |
| `save_memory` / `recall_memories` / `delete_memory` | Memory operations |
| `remember_fact` / `recall_facts` | Fact persistence |
| `DeviceCapabilityProbe` / `ForegroundStateReader` | Internal probes |

### Skill system

Skills defined as SKILL.md files (YAML frontmatter + Markdown body) in `core/data/src/main/assets/skills/md/`. Legacy JSON format in `skills/bundled/` and `skills/scenarios/`. Three sources with priority: Bundled < Cloud < User.

Key frontmatter fields: `name`, `description`, `category`, `allowed-tools`, `context` (inline/fork), `effort`, `risk`, `requires` (permissions, connectivity, apps, minApi), `composes-skills`, `always`, `disable-model-invocation`.

**Eligibility filtering**: `SkillEligibilityChecker` dynamically filters skills based on `requires` and `conditions` at runtime. **Composition**: `composes-skills` allows recursive combination of sub-skills. **Skill sources**: 27 built-in skills across 4 categories (basic, dining/transport, health/shopping, scene orchestration).

27 built-in skills across categories: basic (browser, maps, messaging, contacts, etc.), dining, food-delivery, ride-hailing, navigation, hotel-booking, hospital-appointment, medication-reminder, product-search, schedule-management, expense-tracking, express-delivery, and scenario orchestrations (road-accident-response, travel-abroad).

### Service Gateway

Third-party service abstraction with 6 registered services: `geico`, `tesla_fleet`, `aaa_roadside`, `ctrip`, `opentable`, `marriott`. Each service defines actions with parameter schemas. `VirtualServiceGateway` provides realistic mock responses for all services. `HttpServiceGateway` is the real implementation path (not fully completed yet).

### Virtual Bridge System (for emulator testing)

Config file: `app/src/main/assets/virtual_bridge_config.json`. Each bridge independently switchable (`"virtual"` / `"real"`):

| Bridge | Default | Impact |
|---|---|---|
| `telephony` / `contacts` / `location` | virtual | SMS, dial, contacts, GPS tools |
| `notifications` / `files` / `services` | virtual | Notification, file, service tools |
| `accessibility` | virtual | Stub implementation |
| `browser` / `maps` / `clipboard` / `share` / `media` / `system` / `appState` | real | Work on emulators via Intent |

Virtual mode provides realistic mock data (9 contacts, Shanghai Bund location, 4 notifications, pre-filled files, 6 services with detailed responses). `UserProfileStore` gets auto-seeded with test data (insurance, AAA membership, Tesla vehicle, Yellowstone trip) via `VirtualDataBootstrapper`.

## Testing

- **JUnit 4** for JVM unit tests, placed in `<module>/src/test/java/`
- **No instrumented tests** — all tests run on JVM without Android SDK
- **13 test files** across `core:domain` (9), `core:bridge` (1), `core:data` (1), plus integration tests in `core:domain`
- **`org.json:json:20231013`** dependency for JVM `JSONObject` (replaces Android SDK stub)

### Test Doubles

Located in `core/domain/src/test/java/com/mobilebot/domain/testdoubles/`:
- `Recording*Bridge` — record calls/params for each bridge capability
- `FakeDeviceCapabilityBridge` — assembles all Recording bridges into one test double
- `TestInfra.kt` — `AllCapabilitiesProbe`, `NoCapabilitiesProbe`, `AlwaysForegroundReader`, etc.

### Integration Tests (Recording Bridge pattern)

`ToolRegistryIntegrationTest` (20 cases across 11 tools) uses `FakeDeviceCapabilityBridge` to verify complete tool execution without a device. `ToolPolicyEngineTest` (5 cases) verifies policy decisions.

### Maestro UI Automation

`maestro/flows/` contains 6 YAML flows (basic chat, settings, open_url, smart contact, location, clipboard). testTags: `chat_input`, `send_button`, `settings_button`, `message_list`, `settings_api_key`, `settings_base_url`, `settings_model`, `settings_save`.

## Adding new code

### New tool
1. Implement `Tool` in `core:domain/tools/`
2. Add Android API in `core:bridge/` if needed
3. Bind in `DomainModule` (`@Binds @IntoSet`)
4. Add unit tests (direct + Recording Bridge integration test)
5. Access Android APIs exclusively through `DeviceCapabilityBridge`

### New skill
1. Create `core/data/src/main/assets/skills/md/{name}/SKILL.md` with YAML frontmatter + Markdown body
2. Auto-registered on app startup via `SkillAssetLoader`

### New bridge
1. Define interface in `core/bridge/`
2. Implement for Android in `core/bridge/impl/` and virtual in `core/bridge/virtual/`
3. Register in `BridgeModule`, configure `virtual_bridge_config.json`

### New service
1. Create JSON in `core/data/src/main/assets/services/{serviceId}.json`
2. Implement stub response in `VirtualServiceGateway`
3. Auto-registered on app startup

## `claude_code_src/` directory

Contains embedded Claude Code source files (TypeScript/TSX). Treat as external/bundled code, not primary development target.
