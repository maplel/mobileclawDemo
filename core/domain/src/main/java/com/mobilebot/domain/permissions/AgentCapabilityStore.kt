package com.mobilebot.domain.permissions

/**
 * All capabilities the agent may require from the user.
 * Each entry maps to one or more capability-ID strings that tools declare in
 * [com.mobilebot.domain.tools.Tool.requiredCapabilities].
 *
 * Defaults to OFF — the agent must obtain user consent before first use.
 */
enum class AgentCapability(
    val displayName: String,
    val description: String,
    val capabilityIds: Set<String>,
) {
    LOCATION("Location", "Access GPS for location-based tasks", setOf("location.coarse")),
    CONTACTS("Contacts", "Read contacts for communication", setOf("contacts.read")),
    SMS("SMS", "Send text messages on your behalf", setOf("messaging.sms", "messaging.sms.send")),
    NOTIFICATIONS("Notification Reader", "Read and mirror system notifications", setOf("notifications.read")),
    FILES("File Access", "Read and write workspace files", setOf("files.workspace")),
    BROWSER("Browser", "Open URLs in external browser", setOf("browser.view")),
    MAPS("Maps", "Open map navigation", setOf("maps.external")),
    CAMERA("Camera", "Capture photos and videos", setOf("media.camera")),
    PHONE("Phone", "Initiate phone calls", setOf("telephony.dial")),
    CLIPBOARD("Clipboard", "Copy text to clipboard", setOf("clipboard.write")),
    SHARE("Share", "Share content to other apps", setOf("share.generic")),
    ;

    companion object {
        /** Resolve the human-readable names for a set of raw capability-ID tokens. */
        fun displayNamesFor(capabilityIds: Set<String>): List<String> =
            entries
                .filter { cap -> cap.capabilityIds.any { it in capabilityIds } }
                .map { it.displayName }
                .distinct()
    }
}

/**
 * Persistent store for agent-level capability grants.
 *
 * This is the **first** permission layer the agent checks: does the user *intend*
 * to let the agent use a given capability?  Android runtime permissions are a
 * separate, **second** layer handled by [AgentPermissionCoordinator].
 */
interface AgentCapabilityStore {
    fun isGranted(capabilityId: String): Boolean

    fun grant(capabilityId: String)

    fun revoke(capabilityId: String)

    fun grantedSet(): Set<String>
}
