package com.mobilebot.domain.capabilities

data class CapabilitySnapshot(
    val smsAvailable: Boolean,
    val contactsAvailable: Boolean,
    val locationAvailable: Boolean,
    val browserAvailable: Boolean,
    val cameraAvailable: Boolean,
) {
    fun toPromptSection(): String =
        buildString {
            appendLine("## Device state (informational — do not refuse tools based only on this)")
            appendLine(
                "If a tool appears under Available tools, you may call it. The app requests Android permissions when needed (system dialog). " +
                    "These lines only describe the current snapshot, not whether the user will grant permission.",
            )
            appendLine(
                "- SMS / send_sms: SEND_SMS " +
                    if (smsAvailable) "already granted (direct send when OS allows)." else "not granted yet — still call send_sms; may open composer or prompt permission.",
            )
            appendLine(
                "- Contacts / search_contacts: READ_CONTACTS " +
                    if (contactsAvailable) "granted." else "not granted yet — still call when needed; app may prompt or return empty matches.",
            )
            appendLine(
                "- Location / get_current_location: " +
                    if (locationAvailable) "FINE/COARSE granted." else "not granted yet — still call when user asks; app may prompt.",
            )
            appendLine(
                "- Browser / open_url: " +
                    if (browserAvailable) "https intent resolvable." else "no handler seen yet — still try open_url if user gave a URL.",
            )
            appendLine(
                "- Camera / open_camera: " +
                    if (cameraAvailable) "camera feature reported." else "no camera feature reported — still try if user asks; may fail with a clear error.",
            )
        }
}
