package com.mobilebot.domain.testdoubles

import com.mobilebot.bridge.AccessibilityActionBridge
import com.mobilebot.bridge.AppStateBridge
import com.mobilebot.bridge.BrowserBridge
import com.mobilebot.bridge.ClipboardBridge
import com.mobilebot.bridge.ConnectivityState
import com.mobilebot.bridge.ContactsBridge
import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.bridge.DeviceContextSnapshot
import com.mobilebot.bridge.FileBridge
import com.mobilebot.bridge.LocationBridge
import com.mobilebot.bridge.MapsBridge
import com.mobilebot.bridge.MediaBridge
import com.mobilebot.bridge.NotificationBridge
import com.mobilebot.bridge.ServiceDescriptor
import com.mobilebot.bridge.ServiceGateway
import com.mobilebot.bridge.ServiceRequest
import com.mobilebot.bridge.ServiceResponse
import com.mobilebot.bridge.AppInfo
import com.mobilebot.bridge.ShareBridge
import com.mobilebot.bridge.SystemBridge
import com.mobilebot.bridge.TelephonyBridge
import com.mobilebot.bridge.UiAction
import com.mobilebot.bridge.UiActionResult
import com.mobilebot.bridge.UiTreeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class RecordingDeviceCapabilityBridge(
    val recordingBrowser: RecordingBrowserBridge = RecordingBrowserBridge(),
    val recordingMaps: RecordingMapsBridge = RecordingMapsBridge(),
    val recordingContacts: RecordingContactsBridge = RecordingContactsBridge(),
    val recordingTelephony: RecordingTelephonyBridge = RecordingTelephonyBridge(),
    val recordingClipboard: RecordingClipboardBridge = RecordingClipboardBridge(),
    val recordingShare: RecordingShareBridge = RecordingShareBridge(),
    val recordingMedia: RecordingMediaBridge = RecordingMediaBridge(),
    val recordingLocation: RecordingLocationBridge = RecordingLocationBridge(),
    val recordingFiles: RecordingFileBridge = RecordingFileBridge(),
    val recordingNotifications: RecordingNotificationBridge = RecordingNotificationBridge(),
    val stubAppState: StubAppStateBridge = StubAppStateBridge(),
    val stubAccessibility: StubAccessibilityBridge = StubAccessibilityBridge(),
    val stubSystem: StubSystemBridge = StubSystemBridge(),
    val stubServices: StubServiceGatewayForTest = StubServiceGatewayForTest(),
) : DeviceCapabilityBridge {
    override val browser: BrowserBridge get() = recordingBrowser
    override val maps: MapsBridge get() = recordingMaps
    override val contacts: ContactsBridge get() = recordingContacts
    override val telephony: TelephonyBridge get() = recordingTelephony
    override val clipboard: ClipboardBridge get() = recordingClipboard
    override val share: ShareBridge get() = recordingShare
    override val media: MediaBridge get() = recordingMedia
    override val location: LocationBridge get() = recordingLocation
    override val files: FileBridge get() = recordingFiles
    override val notifications: NotificationBridge get() = recordingNotifications
    override val appState: AppStateBridge get() = stubAppState
    override val accessibility: AccessibilityActionBridge get() = stubAccessibility
    override val system: SystemBridge get() = stubSystem
    override val services: ServiceGateway get() = stubServices
}

class StubAppStateBridge(
    var snapshotValue: DeviceContextSnapshot = DeviceContextSnapshot(
        connectivity = ConnectivityState.WIFI,
        screenOn = true,
    ),
) : AppStateBridge {
    override suspend fun snapshot(): DeviceContextSnapshot = snapshotValue
    override fun observe(): Flow<DeviceContextSnapshot> = MutableStateFlow(snapshotValue)
}

class StubAccessibilityBridge : AccessibilityActionBridge {
    override suspend fun snapshot(): UiTreeSnapshot =
        UiTreeSnapshot(packageName = "com.mobilebot", serializedSummary = "")

    override suspend fun perform(action: UiAction): UiActionResult =
        UiActionResult.Success
}

class StubSystemBridge : SystemBridge {
    override fun openSettings(page: String?): Boolean = true
    override fun setAlarm(hour: Int, minute: Int, label: String?): Boolean = true
    override fun setTimer(lengthSeconds: Int, label: String?): Boolean = true
    override fun setFlashlight(on: Boolean): Boolean = true
    override fun openApp(packageName: String): Boolean = true
    override fun resolveAppPackage(query: String): List<AppInfo> = listOf(
        AppInfo(packageName = "com.test.app", label = query),
    )
}

class StubServiceGatewayForTest : ServiceGateway {
    private val registered = mutableMapOf<String, ServiceDescriptor>()
    private val authorized = mutableSetOf<String>()

    override fun registerService(descriptor: ServiceDescriptor) {
        registered[descriptor.id] = descriptor
        authorized += descriptor.id
    }

    override fun listAvailableServices(): List<ServiceDescriptor> = registered.values.toList()

    override fun isServiceAuthorized(serviceId: String): Boolean = serviceId in authorized

    override suspend fun call(request: ServiceRequest): ServiceResponse {
        val desc = registered[request.serviceId]
            ?: return ServiceResponse(ok = false, message = "Unknown service: ${request.serviceId}")
        val action = desc.actions.find { it.name == request.action }
            ?: return ServiceResponse(ok = false, message = "Unknown action: ${request.action}")
        return ServiceResponse(
            ok = true,
            message = "Stub response for ${request.serviceId}/${request.action}",
            data = mapOf("serviceId" to request.serviceId, "action" to request.action),
        )
    }
}
