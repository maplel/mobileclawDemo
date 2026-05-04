package com.mobilebot.domain.skill

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates and registers [SkillEntry] objects into the [SkillRegistry].
 * Each entry is checked for runtime eligibility before registration.
 */
@Singleton
class SkillBootstrapper @Inject constructor(
    private val skillRegistry: SkillRegistry,
    private val eligibilityChecker: SkillEligibilityChecker,
) {

    fun registerSkillEntries(entries: List<SkillEntry>) {
        for (entry in entries) {
            val eligResult = eligibilityChecker.check(entry)
            val checkedEntry = entry.copy(
                eligible = eligResult.eligible,
                ineligibleReason = eligResult.reason,
            )
            skillRegistry.register(checkedEntry)
        }
    }
}
