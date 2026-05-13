package com.mobilebot.data.capabilities

import android.Manifest

/**
 * Maps domain capability tokens (as used by [com.mobilebot.domain.tools.Tool.requiredCapabilities])
 * to Android runtime [Manifest.permission] strings for [com.mobilebot.permissions.DefaultAgentPermissionCoordinator].
 *
 * Capabilities such as [notifications.read] (notification listener settings) have no runtime mapping here.
 */
object CapabilityPermissionMapper {
    fun permissionsForCapabilities(capabilities: Set<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (cap in capabilities) {
            when (cap) {
                "location.coarse" -> {
                    out.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    out.add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                "contacts.read" -> {
                    out.add(Manifest.permission.READ_CONTACTS)
                }
                "messaging.sms.send" -> {
                    out.add(Manifest.permission.SEND_SMS)
                }
                else -> Unit
            }
        }
        return out.toList()
    }
}
