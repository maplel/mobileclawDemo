package com.mobilebot.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.mobilebot.data.capabilities.CapabilityPermissionMapper
import com.mobilebot.domain.permissions.AgentPermissionCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests Android runtime permissions using [PermissionRequestSession] (system dialog).
 *
 * Uses [ContextCompat.checkSelfPermission] — not [com.mobilebot.domain.tools.DeviceCapabilityProbe] —
 * so we still prompt when the user has not granted access, even though the probe may report tools as
 * "available" for planner exposure.
 */
@Singleton
class DefaultAgentPermissionCoordinator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val session: PermissionRequestSession,
    ) : AgentPermissionCoordinator {
        override suspend fun ensureRuntimePermissionsForCapabilities(capabilities: Set<String>): Boolean {
            if (capabilities.isEmpty()) return true
            val androidPerms = CapabilityPermissionMapper.permissionsForCapabilities(capabilities)
            if (androidPerms.isEmpty()) return true

            val need =
                androidPerms.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }.toTypedArray()
            if (need.isEmpty()) return true

            val granted = session.requestPermissions(need)
            if (!granted) return false

            return androidPerms.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
