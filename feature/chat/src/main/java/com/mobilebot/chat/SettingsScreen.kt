@file:OptIn(ExperimentalLayoutApi::class)

package com.mobilebot.chat

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilebot.domain.permissions.AgentCapability

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val apiKey by viewModel.apiKey.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val model by viewModel.model.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val heartbeat by viewModel.heartbeat.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val capVersion by viewModel.capabilityVersion.collectAsState()
    val context = LocalContext.current

    val capabilityStates = remember(capVersion) {
        AgentCapability.entries.associateWith { viewModel.isCapabilityGranted(it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* Android permission result; capability is already granted in store */ }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProviderSection(
                selectedProvider = selectedProvider,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                onSelectProvider = viewModel::selectProvider,
                onApiKeyChange = viewModel::updateApiKey,
                onBaseUrlChange = viewModel::updateBaseUrl,
                onModelChange = viewModel::updateModel,
            )

            CapabilitiesSection(
                states = capabilityStates,
                onToggle = { capability, enabled ->
                    if (enabled) {
                        viewModel.grantCapability(capability)
                        val androidPerms = androidPermissionsFor(capability)
                        if (androidPerms.isNotEmpty()) {
                            permissionLauncher.launch(androidPerms)
                        }
                        if (capability == AgentCapability.NOTIFICATIONS) {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                            )
                        }
                    } else {
                        viewModel.revokeCapability(capability)
                    }
                },
            )

            DeviceSection(
                deviceId = deviceId,
                heartbeat = heartbeat,
                onDeviceIdChange = viewModel::updateDeviceId,
                onHeartbeatChange = viewModel::updateHeartbeat,
            )

            Button(
                onClick = viewModel::save,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("settings_save"),
            ) {
                Text("Save Settings")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProviderSection(
    selectedProvider: LlmProvider?,
    apiKey: String,
    baseUrl: String,
    model: String,
    onSelectProvider: (LlmProvider) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
) {
    SectionCard(title = "AI Model Provider") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LlmProvider.entries.forEach { provider ->
                FilterChip(
                    selected = selectedProvider == provider,
                    onClick = { onSelectProvider(provider) },
                    label = { Text(provider.displayName) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            placeholder = { Text("Leave empty to use build-time key") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("settings_api_key"),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("Base URL") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("settings_base_url"),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            label = { Text("Model") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("settings_model"),
            singleLine = true,
        )

        val models = selectedProvider?.models.orEmpty()
        if (models.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Quick Select",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                models.forEach { (id, label) ->
                    FilterChip(
                        selected = model == id,
                        onClick = { onModelChange(id) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilitiesSection(
    states: Map<AgentCapability, Boolean>,
    onToggle: (AgentCapability, Boolean) -> Unit,
) {
    SectionCard(title = "App Permissions") {
        Text(
            "Controls what the agent is allowed to do. All default to OFF.\n" +
                "The agent will ask before first use — you can choose:\n" +
                "始终 · 仅在使用该应用时允许 · 每次都询问 · 不允许",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val capabilities = states.keys.toList()
        capabilities.forEachIndexed { index, cap ->
            val granted = states[cap] ?: false
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        cap.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        cap.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = granted,
                    onCheckedChange = { checked -> onToggle(cap, checked) },
                )
            }
            if (index < capabilities.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun DeviceSection(
    deviceId: String,
    heartbeat: Boolean,
    onDeviceIdChange: (String) -> Unit,
    onHeartbeatChange: (Boolean) -> Unit,
) {
    SectionCard(title = "Device") {
        OutlinedTextField(
            value = deviceId,
            onValueChange = onDeviceIdChange,
            label = { Text("Device / Session ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Heartbeat",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Background check-in every ~15 min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = heartbeat, onCheckedChange = onHeartbeatChange)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * Maps an [AgentCapability] to Android runtime permissions that should be
 * requested when the capability is toggled ON.  Returns empty for capabilities
 * that have no corresponding runtime permission (e.g. clipboard, share).
 */
private fun androidPermissionsFor(capability: AgentCapability): Array<String> =
    when (capability) {
        AgentCapability.LOCATION ->
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        AgentCapability.CONTACTS -> arrayOf(Manifest.permission.READ_CONTACTS)
        AgentCapability.SMS -> arrayOf(Manifest.permission.SEND_SMS)
        AgentCapability.CAMERA -> arrayOf(Manifest.permission.CAMERA)
        else -> emptyArray()
    }
