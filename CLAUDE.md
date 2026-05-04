# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MobileBot is an Android multi-module Agent application. It runs a local AI agent on Android devices that communicates with LLMs via the OpenAI `tool_calls` protocol. The agent can invoke tools (browser, maps, contacts, SMS, etc.) and execute skills (SKILL.md-based task definitions) to accomplish complex multi-step tasks on the device.

## Build System & Commands

- **Gradle Kotlin DSL** (`settings.gradle.kts`, `build.gradle.kts`)
- **JDK 17** required
- **SDK**: compileSdk/targetSdk 35, minSdk 26
- **Kotlin 2.0.21**, AGP 8.7.2, Compose BOM 2024.10.01

### Essential commands

```bash
# Full build (all platforms)
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Run JVM unit tests (no Android device needed)
./gradlew test

# Run unit tests for a specific module
./gradlew :core:domain:testDebugUnitTest
./gradlew :core:bridge:testDebugUnitTest
./gradlew :core:data:testDebugUnitTest

# Run Android Lint
./gradlew lintDebug

# Run a single test class
./gradlew :core:domain:testDebugUnitTest --tests "com.mobilebot.domain.skill.EnhancedSkillParserTest" --no-daemon

# Run all tests without daemon (helps on memory-constrained machines)
$env:GRADLE_OPTS="-Xmx1g -XX:+UseSerialGC"
./gradlew test --no-daemon
```

## Module Structure

| Module | Responsibility | Key files |
|---|---|---|
| `:app` | Application entry, Hilt app-level DI, foreground service, notification listener | `MobileBotApplication`, `MainActivity`, `AgentForegroundService` |
| `:feature:chat` | Compose UI - chat screen, settings screen, navigation | `ChatScreen`, `ChatViewModel`, `SettingsScreen`, `MobileBotNavHost` |
| `:core:model` | Shared DTOs - messages, tools, stream events | `Messages.kt`, `ToolModels.kt`, `StreamEvents.kt` |
| `:core:bus` | Agent-to-UI message bus (`OutboundMessage`/`InboundMessage`) | `MessageBus.kt` |
| `:core:network` | OpenAI-compatible LLM client, SSE streaming | `OpenAiCompatibleClient.kt`, `NanobotStreamClient.kt` |
| `:core:bridge` | Android system capability abstraction (browser, maps, contacts, SMS, location, notifications, files, etc.) + virtual bridge for testing | `DeviceCapabilityBridge`, `Android*Bridge`, `Virtual*Bridge`, `SwitchableDeviceCapabilityBridge` |
| `:core:domain` | **Agent loop** (`ToolCallAgentLoop`), tool registry, skill system, plan mode, subtask execution | `AgentLoop.kt`, `ToolCallAgentLoop.kt`, `ToolRegistry.kt`, `SkillExecutor.kt`, `PlanManager.kt`, `SubtaskExecutor.kt`, all tools in `tools/` |
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

1. `ChatViewModel.send()` → `ForegroundController` → `AgentLoop.processUserMessage()`
2. `ToolCallAgentLoop` builds system prompt (with skill directory) + tool definitions
3. Sends to LLM via OpenAI `tool_calls` protocol
4. If LLM returns `tool_calls`: execute each tool (through `ToolPolicyEngine` + `ToolPermissionGate`) → append results → loop
5. If LLM calls `use_skill`: load SKILL.md and execute (inline or fork mode)
6. If LLM calls `create_plan`: create structured plan, wait for user approval
7. Tool results flow back to UI via `MessageBus`

### Key components

- **ToolCallAgentLoop** (`core/domain`): Main agent loop implementing tool_calls iteration
- **ToolRegistry** (`core/domain/tools/ToolRegistry.kt`): Collects, filters, and executes tools based on device capabilities
- **SkillExecutor** (`core/domain/skill/SkillExecutor.kt`): Executes SKILL.md skills in inline or fork mode
- **PlanManager** (`core/domain/agent/PlanManager.kt`): State machine for plan mode (NONE/PENDING/EXECUTING/DONE)
- **SubtaskExecutor** (`core/domain/subtask/SubtaskExecutor.kt`): Creates/manages subtasks with shared facts
- **MessageBus** (`core/bus`): Real-time Agent-to-UI messaging channel

### Data persistence

- Chat messages stored in **Room** database (`AppDatabase` with SessionDao, MessageDao)
- Primary chat session: `chatId = "main"` → session key `mobile:main`
- User settings (API key, base URL, model) stored via DataStore/SharedPreferences
- Working memory and facts persisted in Room tables

## Key Patterns

### Virtual Bridge System (for emulator testing)

Many tools depend on real Android hardware (contacts, SMS, location). The **Virtual Bridge** system provides mock data for testing on emulators.

- Config file: `app/src/main/assets/virtual_bridge_config.json`
- Each bridge can be independently set to `"virtual"` or `"real"`
- `SwitchableDeviceCapabilityBridge` routes calls based on config
- Virtual mock data: `VirtualMockData.kt` (contacts, location, notifications, etc.)

### Tool definition pattern

Tools implement the `DomainToolModule` Hilt multi-binding pattern. Each tool declares:
- `name` (e.g., "open_url")
- `ToolDefinition` (JSON schema for LLM)
- `requiredCapabilities` (device capabilities needed)
- `executionPolicy` (permission requirements)

Tools access Android APIs through `DeviceCapabilityBridge` interface, never directly.

### Skill system

Skills are defined as SKILL.md files (YAML frontmatter + Markdown body) in `core/data/src/main/assets/skills/md/`. Additional bundled skills use JSON format in `skills/bundled/` and `skills/scenarios/`. At startup, `SkillAssetLoader.loadAllSkills()` loads and registers all skills into `SkillRegistry`.

## Testing

- **JUnit 4** for JVM unit tests, placed in `<module>/src/test/java/`
- 11 test files across `core:domain` (9), `core:bridge` (1), `core:data` (1)
- Integration tests use `FakeDeviceCapabilityBridge` + `Recording*Bridge` test doubles in `core/domain/src/test/java/com/mobilebot/domain/testdoubles/`
- **Maestro** UI automation in `maestro/flows/` (requires separate installation)
- No instrumented tests currently

## Adding new code

### New tool: Create implementation in `core:domain/tools/`, add Android API in `core:bridge/` if needed, bind in `DomainModule`, add tests.

### New skill: Create `core/data/src/main/assets/skills/md/{name}/SKILL.md` with YAML frontmatter. Auto-registered on app startup.

### New bridge: Define interface in `core/bridge/`, implement in `core/bridge/impl/`, register in `BridgeModule`, configure `virtual_bridge_config.json`.

## `claude_code_src/` directory

Contains embedded Claude Code source files (TypeScript/TSX). This is the CLI tool source bundled into the repo for some purpose — treat as external/bundled code, not primary development target.
