package com.mobilebot.domain.skill

import com.mobilebot.domain.tools.DeviceCapabilityProbe
import javax.inject.Inject
import javax.inject.Singleton

data class EligibilityResult(
    val eligible: Boolean,
    val reason: String? = null,
) {
    companion object {
        fun ok() = EligibilityResult(eligible = true)
        fun fail(reason: String) = EligibilityResult(eligible = false, reason = reason)
    }
}

/**
 * Checks whether a skill meets all runtime requirements on the current device.
 */
@Singleton
class SkillEligibilityChecker @Inject constructor(
    private val capabilityProbe: DeviceCapabilityProbe,
) {

    fun check(entry: SkillEntry): EligibilityResult {
        return check(entry.manifest)
    }

    fun check(manifest: SkillManifest): EligibilityResult {
        val req = manifest.requires

        for (perm in req.permissions) {
            if (!capabilityProbe.hasPermission(perm)) {
                return EligibilityResult.fail("Missing permission: $perm")
            }
        }

        if (req.connectivity && !capabilityProbe.isConnected()) {
            return EligibilityResult.fail("No network connection")
        }

        for (app in req.apps) {
            if (!capabilityProbe.isAppInstalled(app)) {
                return EligibilityResult.fail("App not installed: $app")
            }
        }

        if (req.minApi > 0 && !capabilityProbe.meetsMinApi(req.minApi)) {
            return EligibilityResult.fail("Requires API level ${req.minApi}")
        }

        manifest.conditions?.let { cond ->
            val condResult = evaluateConditions(cond)
            if (!condResult.eligible) return condResult
        }

        return EligibilityResult.ok()
    }

    private fun evaluateConditions(conditions: SkillConditions): EligibilityResult {
        conditions.time?.let { timeSpec ->
            if (!capabilityProbe.isWithinTimeRange(timeSpec)) {
                return EligibilityResult.fail("Outside allowed time range: $timeSpec")
            }
        }
        return EligibilityResult.ok()
    }
}
