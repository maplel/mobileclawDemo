package com.mobilebot.bridge.impl

import com.mobilebot.bridge.AccessibilityActionBridge
import com.mobilebot.bridge.AppStateBridge
import com.mobilebot.bridge.BrowserBridge
import com.mobilebot.bridge.ClipboardBridge
import com.mobilebot.bridge.ContactsBridge
import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.bridge.FileBridge
import com.mobilebot.bridge.LocationBridge
import com.mobilebot.bridge.MapsBridge
import com.mobilebot.bridge.MediaBridge
import com.mobilebot.bridge.NotificationBridge
import com.mobilebot.bridge.ServiceGateway
import com.mobilebot.bridge.ShareBridge
import com.mobilebot.bridge.SystemBridge
import com.mobilebot.bridge.TelephonyBridge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDeviceCapabilityBridge
    @Inject
    constructor(
        fileBridge: AndroidFileBridge,
        notificationBridge: CachingNotificationBridge,
        accessibilityBridge: StubAccessibilityBridge,
        appStateBridge: AndroidAppStateBridge,
        contactsBridge: AndroidContactsBridge,
        locationBridge: AndroidLocationBridge,
        mediaBridge: AndroidMediaBridge,
        browserBridge: AndroidBrowserBridge,
        mapsBridge: AndroidMapsBridge,
        clipboardBridge: AndroidClipboardBridge,
        shareBridge: AndroidShareBridge,
        telephonyBridge: AndroidTelephonyBridge,
        systemBridge: AndroidSystemBridge,
        serviceGateway: HttpServiceGateway,
    ) : DeviceCapabilityBridge {
        override val files: FileBridge = fileBridge
        override val notifications: NotificationBridge = notificationBridge
        override val accessibility: AccessibilityActionBridge = accessibilityBridge
        override val appState: AppStateBridge = appStateBridge
        override val contacts: ContactsBridge = contactsBridge
        override val location: LocationBridge = locationBridge
        override val media: MediaBridge = mediaBridge
        override val browser: BrowserBridge = browserBridge
        override val maps: MapsBridge = mapsBridge
        override val clipboard: ClipboardBridge = clipboardBridge
        override val share: ShareBridge = shareBridge
        override val telephony: TelephonyBridge = telephonyBridge
        override val system: SystemBridge = systemBridge
        override val services: ServiceGateway = serviceGateway
    }
