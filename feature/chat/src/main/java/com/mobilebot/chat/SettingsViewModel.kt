package com.mobilebot.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilebot.data.settings.LlmEndpointDefaults
import com.mobilebot.data.settings.UserSettingsRepository
import com.mobilebot.data.work.HeartbeatScheduler
import com.mobilebot.domain.permissions.AgentCapability
import com.mobilebot.domain.permissions.AgentCapabilityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LlmProvider(
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val models: List<Pair<String, String>>,
) {
    GEMINI(
        displayName = "Gemini",
        baseUrl = LlmEndpointDefaults.GEMINI_OPENAI_COMPAT_BASE,
        defaultModel = LlmEndpointDefaults.DEFAULT_GEMINI_MODEL,
        models = listOf(
            LlmEndpointDefaults.DEFAULT_GEMINI_MODEL to "2.0 Flash",
        ),
    ),
    ZHIPU_GLM(
        displayName = "智谱 GLM",
        baseUrl = LlmEndpointDefaults.ZHIPU_OPENAI_COMPAT_BASE,
        defaultModel = LlmEndpointDefaults.DEFAULT_GLM_MODEL,
        models = listOf(
            LlmEndpointDefaults.DEFAULT_GLM_MODEL to "4.7 Flash",
        ),
    ),
    BITEXING(
        displayName = "Bitexing",
        baseUrl = LlmEndpointDefaults.BITEXING_OPENAI_COMPAT_BASE,
        defaultModel = LlmEndpointDefaults.BITEXING_DEFAULT_MODEL,
        models = listOf(
            LlmEndpointDefaults.BITEXING_GEMINI_25_FLASH to "Gemini 2.5 Flash",
            LlmEndpointDefaults.BITEXING_GEMINI_25_PRO to "Gemini 2.5 Pro",
            LlmEndpointDefaults.BITEXING_GEMINI_31_PRO_PREVIEW to "Gemini 3.1 Preview",
        ),
    ),
    MINIMAX(
        displayName = "MiniMax",
        baseUrl = LlmEndpointDefaults.MINIMAX_OPENAI_COMPAT_BASE,
        defaultModel = LlmEndpointDefaults.MINIMAX_DEFAULT_MODEL,
        models = listOf(
            LlmEndpointDefaults.MINIMAX_M2_7 to "M2.7",
            LlmEndpointDefaults.MINIMAX_M2_7_HIGHSPEED to "M2.7 Fast",
            LlmEndpointDefaults.MINIMAX_M2_5 to "M2.5",
            LlmEndpointDefaults.MINIMAX_M2_5_HIGHSPEED to "M2.5 Fast",
            LlmEndpointDefaults.MINIMAX_M2_1 to "M2.1",
            LlmEndpointDefaults.MINIMAX_M2_1_HIGHSPEED to "M2.1 Fast",
            LlmEndpointDefaults.MINIMAX_M2 to "M2",
        ),
    ),
    QWEN(
        displayName = "通义千问",
        baseUrl = LlmEndpointDefaults.DASHSCOPE_OPENAI_COMPAT_BASE,
        defaultModel = LlmEndpointDefaults.DEFAULT_QWEN_MODEL,
        models = listOf(
            LlmEndpointDefaults.QWEN_PLUS to "Plus",
            LlmEndpointDefaults.QWEN_MAX to "Max",
            LlmEndpointDefaults.QWEN_TURBO to "Turbo",
        ),
    ),
    ;

    companion object {
        fun fromId(id: String): LlmProvider? =
            entries.firstOrNull { it.name.equals(id.trim(), ignoreCase = true) }

        fun fromBaseUrl(url: String): LlmProvider? =
            entries.firstOrNull { url.startsWith(it.baseUrl, ignoreCase = true) }
    }
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settings: UserSettingsRepository,
        private val heartbeatScheduler: HeartbeatScheduler,
        private val capabilityStore: AgentCapabilityStore,
    ) : ViewModel() {
        private val _apiKey = MutableStateFlow("")
        val apiKey: StateFlow<String> = _apiKey.asStateFlow()

        private val _baseUrl = MutableStateFlow("")
        val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

        private val _model = MutableStateFlow("")
        val model: StateFlow<String> = _model.asStateFlow()

        private val _deviceId = MutableStateFlow("")
        val deviceId: StateFlow<String> = _deviceId.asStateFlow()

        private val _heartbeat = MutableStateFlow(false)
        val heartbeat: StateFlow<Boolean> = _heartbeat.asStateFlow()

        private val _selectedProvider = MutableStateFlow<LlmProvider?>(null)
        val selectedProvider: StateFlow<LlmProvider?> = _selectedProvider.asStateFlow()

        private val _capabilityVersion = MutableStateFlow(0)
        val capabilityVersion: StateFlow<Int> = _capabilityVersion.asStateFlow()

        fun isCapabilityGranted(capability: AgentCapability): Boolean =
            capability.capabilityIds.all { capabilityStore.isGranted(it) }

        fun grantCapability(capability: AgentCapability) {
            capability.capabilityIds.forEach { capabilityStore.grant(it) }
            _capabilityVersion.value++
        }

        fun revokeCapability(capability: AgentCapability) {
            capability.capabilityIds.forEach { capabilityStore.revoke(it) }
            _capabilityVersion.value++
        }

        init {
            viewModelScope.launch { reload() }
        }

        private suspend fun reload() {
            _apiKey.value = settings.getApiKeyStoredOnly()
            _baseUrl.value = settings.getBaseUrl()
            _model.value = settings.getModel()
            _deviceId.value = settings.getDeviceId()
            _heartbeat.value = settings.getHeartbeatEnabled()
            _selectedProvider.value =
                LlmProvider.fromId(settings.getProviderId()) ?: LlmProvider.fromBaseUrl(_baseUrl.value)
        }

        fun updateApiKey(v: String) {
            _apiKey.value = v
        }

        fun updateBaseUrl(v: String) {
            _baseUrl.value = v
            LlmProvider.fromBaseUrl(v)?.let { _selectedProvider.value = it }
        }

        fun updateModel(v: String) {
            _model.value = v
        }

        fun updateDeviceId(v: String) {
            _deviceId.value = v
        }

        fun updateHeartbeat(v: Boolean) {
            _heartbeat.value = v
        }

        fun selectProvider(provider: LlmProvider) {
            _selectedProvider.value = provider
            _baseUrl.value = provider.baseUrl
            _model.value = provider.defaultModel
        }

        fun save() {
            viewModelScope.launch {
                settings.setApiKey(_apiKey.value.trim())
                settings.setBaseUrl(_baseUrl.value.trim())
                settings.setModel(_model.value.trim())
                settings.setProviderId(_selectedProvider.value?.name.orEmpty())
                settings.setDeviceId(_deviceId.value.trim().ifEmpty { "android-device-1" })
                settings.setHeartbeatEnabled(_heartbeat.value)
                heartbeatScheduler.applyHeartbeat(_heartbeat.value)
                reload()
            }
        }
    }
