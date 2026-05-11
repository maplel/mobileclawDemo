package com.mobilebot.domain.tools

import com.mobilebot.domain.capabilities.CapabilitySnapshot
import com.mobilebot.domain.capabilities.ToolCapabilityFilter
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry
    @Inject
    constructor(
        initialTools: Set<@JvmSuppressWildcards Tool>,
        private val probe: DeviceCapabilityProbe,
        private val policyEngine: ToolPolicyEngine,
    ) {
        private val byName = ConcurrentHashMap<String, Tool>().apply { initialTools.forEach { put(it.name, it) } }

        fun register(tool: Tool) {
            byName[tool.name] = tool
        }

        fun get(name: String): Tool? = byName[name]

        fun all(): List<Tool> = byName.values.toList()

        fun definitions(): List<ToolDefinition> = all().map { it.definition }

        /** Tools exposed to the LLM for this turn (subset of registered tools). */
        fun definitionsForLlm(subset: List<Tool>? = null): List<ToolDefinition> {
            val source = subset ?: all()
            return source
                .filter { probe.hasCapabilities(it.requiredCapabilities) }
                .map { it.definition }
        }

        fun toolsMatching(applicableNames: Set<String>): List<Tool> {
            if (applicableNames.isEmpty()) return emptyList()
            return all()
                .filter { it.name in applicableNames && probe.hasCapabilities(it.requiredCapabilities) }
        }

        fun definitionsForSkill(allowedTools: Set<String>): List<ToolDefinition> {
            if (allowedTools.isEmpty()) return definitionsForLlm()
            return all()
                .filter { it.name in allowedTools && probe.hasCapabilities(it.requiredCapabilities) }
                .map { it.definition }
        }

        /**
         * Further restricts tools using a [CapabilitySnapshot] (planner / prompt visibility).
         * Still requires [DeviceCapabilityProbe] for each tool.
         */
        fun toolsMatching(
            applicableNames: Set<String>,
            snapshot: CapabilitySnapshot,
        ): List<Tool> =
            toolsMatching(applicableNames).filter { ToolCapabilityFilter.visible(it, snapshot) }

        suspend fun execute(
            name: String,
            argumentsJson: String,
        ): ToolResult {
            val t = byName[name] ?: return ToolResult(ok = false, message = "unknown tool: $name")
            if (!probe.hasCapabilities(t.requiredCapabilities)) {
                return ToolResult(ok = false, message = "tool capability not available on this device")
            }
            val decision = policyEngine.check(t)
            if (!decision.allowed) {
                return ToolResult(ok = false, message = decision.reason ?: "policy blocked")
            }
            return t.execute(argumentsJson)
        }
    }
