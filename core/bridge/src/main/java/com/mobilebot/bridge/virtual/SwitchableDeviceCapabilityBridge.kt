package com.mobilebot.bridge.virtual

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
import com.mobilebot.bridge.impl.AndroidDeviceCapabilityBridge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-bridge switchable [DeviceCapabilityBridge] that delegates each sub-bridge
 * to either the real (Android) or virtual implementation based on
 * [VirtualBridgeManager] configuration.
 *
 * Uses [dagger.Lazy] so that the unused bridge set is never fully constructed.
 */
@Singleton
class SwitchableDeviceCapabilityBridge
    @Inject
    constructor(
        private val manager: VirtualBridgeManager,
        private val real: dagger.Lazy<AndroidDeviceCapabilityBridge>,
        private val virtual: dagger.Lazy<VirtualDeviceCapabilityBridge>,
    ) : DeviceCapabilityBridge {
        override val files: FileBridge
            get() = pick("files") { it.files }

        override val notifications: NotificationBridge
            get() = pick("notifications") { it.notifications }

        override val accessibility: AccessibilityActionBridge
            get() = pick("accessibility") { it.accessibility }

        override val appState: AppStateBridge
            get() = pick("appState") { it.appState }

        override val contacts: ContactsBridge
            get() = pick("contacts") { it.contacts }

        override val location: LocationBridge
            get() = pick("location") { it.location }

        override val media: MediaBridge
            get() = pick("media") { it.media }

        override val browser: BrowserBridge
            get() = pick("browser") { it.browser }

        override val maps: MapsBridge
            get() = pick("maps") { it.maps }

        override val clipboard: ClipboardBridge
            get() = pick("clipboard") { it.clipboard }

        override val share: ShareBridge
            get() = pick("share") { it.share }

        override val telephony: TelephonyBridge
            get() = pick("telephony") { it.telephony }

        override val system: SystemBridge
            get() = pick("system") { it.system }

        override val services: ServiceGateway
            get() = pick("services") { it.services }

        private inline fun <T> pick(
            bridgeName: String,
            selector: (DeviceCapabilityBridge) -> T,
        ): T = if (manager.isVirtual(bridgeName)) {
            selector(virtual.get())
        } else {
            selector(real.get())
        }
    }
