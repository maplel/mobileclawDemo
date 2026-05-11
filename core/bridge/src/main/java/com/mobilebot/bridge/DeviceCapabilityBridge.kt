package com.mobilebot.bridge

interface DeviceCapabilityBridge {
    val files: FileBridge
    val notifications: NotificationBridge
    val accessibility: AccessibilityActionBridge
    val appState: AppStateBridge
    val contacts: ContactsBridge
    val location: LocationBridge
    val media: MediaBridge
    val browser: BrowserBridge
    val maps: MapsBridge
    val clipboard: ClipboardBridge
    val share: ShareBridge
    val telephony: TelephonyBridge
    val system: SystemBridge
    val services: ServiceGateway
}
