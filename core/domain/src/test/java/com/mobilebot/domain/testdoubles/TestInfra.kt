package com.mobilebot.domain.testdoubles

import com.mobilebot.domain.tools.DeviceCapabilityProbe
import com.mobilebot.domain.tools.ForegroundStateReader

class AllCapabilitiesProbe : DeviceCapabilityProbe {
    override fun hasCapabilities(capabilities: Set<String>): Boolean = true
}

class NoCapabilitiesProbe : DeviceCapabilityProbe {
    override fun hasCapabilities(capabilities: Set<String>): Boolean = capabilities.isEmpty()
}

class SelectiveCapabilitiesProbe(
    private val available: Set<String> = emptySet(),
) : DeviceCapabilityProbe {
    override fun hasCapabilities(capabilities: Set<String>): Boolean =
        available.containsAll(capabilities)
}

class AlwaysForegroundReader : ForegroundStateReader {
    override fun isInteractiveForeground(): Boolean = true
}

class NeverForegroundReader : ForegroundStateReader {
    override fun isInteractiveForeground(): Boolean = false
}
