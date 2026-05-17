package com.mobilebot.systemruntime

import android.content.Context
import android.util.Log
import com.mobilebot.bridge.virtual.VirtualMockData
import com.mobilebot.domain.profile.UserProfileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemRuntime
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val userProfileStore: UserProfileStore,
    ) {
        private val smsOutbox = mutableListOf<Map<String, Any?>>()
        private val smsInbox = mutableListOf<Map<String, Any?>>()
        private val smsWatches = linkedMapOf<String, SmsWatch>()
        private val smsLock = Any()
        private val callLog = mutableListOf<Map<String, Any?>>()
        private val runtimeProfiles: List<SystemRuntimeProfile> by lazy { loadRuntimeProfiles() }
        private var selectedServiceName: String? = null
        private val _events = MutableSharedFlow<SystemRuntimeEvent>(extraBufferCapacity = 16)
        val events: SharedFlow<SystemRuntimeEvent> = _events.asSharedFlow()

        fun bootstrap() {
            runBlocking {
                loadUserMemoryAssets()
                seedStructuredProfile()
            }
            Log.i(TAG, "System runtime bootstrapped")
        }

        suspend fun execute(
            action: String,
            params: JSONObject,
        ): SystemRuntimeResult {
            val normalized = action.trim().lowercase()
            return when (normalized) {
                "sms", "text_message" -> routeSms(params)
                "send_sms" -> sendSms(params)
                "receive_sms" -> waitForSms(params)
                "dial_phone", "phone_call" -> dialPhone(params)
                "call_log" -> ok("Call log returned", "calls" to callLog.toList())
                "notification" -> notification(params)
                "reminder", "long_reminder" -> reminder(params, normalized)
                "location" -> location(params)
                "contacts" -> contacts(params)
                "social_graph" -> socialGraph()
                "device_state" -> deviceState()
                "memory_read" -> readMemory(params)
                "service_call", "mcp_call" -> serviceCall(params)
                "payment", "pay" -> payment(params)
                "accounting", "expense", "record_expense" -> recordExpense(params)
                else -> SystemRuntimeResult(
                    ok = false,
                    message = "System action not available: $action",
                    data = baseData("action" to action),
                )
            }
        }

        suspend fun sendSmsFromTool(params: JSONObject): SystemRuntimeResult = sendSms(params)

        fun searchContactsFromTool(params: JSONObject): SystemRuntimeResult = contacts(params)

        fun scenarioEvents(scenarioId: String): List<SystemRuntimeScriptEvent> =
            runtimeProfiles
                .flatMap { it.scenarioEvents }
                .filter { it.scenarioId == scenarioId }
                .sortedBy { it.time }

        suspend fun publishEvent(event: SystemRuntimeEvent) {
            _events.emit(event)
        }

        suspend fun waitForSms(params: JSONObject): SystemRuntimeResult {
            val watch = synchronized(smsLock) {
                findSmsWatch(params) ?: createSmsWatch(
                    contact = displayContactName(
                        firstText(params, "from", "contact", "sender", "recipient", "name"),
                        params.optString("context").ifBlank { params.optString("lastMessage") },
                    ),
                    outboundMessage = params.optString("context").ifBlank { params.optString("lastMessage") },
                )
            }
            delay(Random.nextLong(SMS_MIN_DELAY_MS, SMS_MAX_DELAY_MS + 1))
            val inboxSnapshot = synchronized(smsLock) { smsInbox.toList() }
            val reply = generateSmsReply(watch, inboxSnapshot)
            val item = linkedMapOf<String, Any?>(
                "id" to "sms-in-${System.currentTimeMillis()}",
                "from" to watch.contact,
                "displayName" to watch.contact,
                "message" to reply.message,
                "receivedAt" to System.currentTimeMillis(),
                "matchedWatchId" to watch.id,
            ).apply {
                if (!reply.eventType.isNullOrBlank()) put("eventType", reply.eventType)
            }
            synchronized(smsLock) {
                smsInbox += item
                smsWatches.remove(watch.id)
            }
            val extra = reply.decisionPrompt?.let { listOf("decisionPrompt" to it.toMap()) }.orEmpty()
            return ok(
                "Inbound SMS received from ${watch.contact}",
                *(
                    listOf(
                        "sms" to item,
                        "listener" to mapOf(
                            "id" to watch.id,
                            "status" to "matched",
                            "contact" to watch.contact,
                        ),
                    ) + extra
                ).toTypedArray(),
            )
        }

        private suspend fun routeSms(params: JSONObject): SystemRuntimeResult {
            val direction = params.optString("direction")
                .ifBlank { params.optString("type") }
                .ifBlank { params.optString("mode") }
                .ifBlank { params.optString("operation") }
                .lowercase()
            val looksInbound = direction in setOf("receive", "received", "inbound", "incoming") ||
                (params.has("from") && !params.has("to") && !params.has("phoneNumber") && !params.has("phone"))
            return if (looksInbound) waitForSms(params) else sendSms(params)
        }

        private suspend fun loadUserMemoryAssets() {
            val files = runCatching { context.assets.list(USER_MEMORY_DIR) }.getOrNull().orEmpty()
            for (file in files) {
                if (!file.endsWith(".md", ignoreCase = true) && !file.endsWith(".txt", ignoreCase = true)) continue
                val path = "$USER_MEMORY_DIR/$file"
                val body = runCatching {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                }.getOrNull()?.trim().orEmpty()
                if (body.isBlank()) continue
                val key = file.substringBeforeLast('.').lowercase()
                userProfileStore.set("user_memory", key, body)
            }
        }

        private suspend fun seedStructuredProfile() {
            for ((category, entries) in VirtualMockData.USER_PROFILE) {
                for ((key, value) in entries) {
                    userProfileStore.set(category, key, value)
                }
            }
        }

        private fun sendSms(params: JSONObject): SystemRuntimeResult {
            val rawTo = firstText(params, "to", "contact", "recipient", "name", "party", "phoneNumber", "phone", "number")
            val message = params.optString("message")
                .ifBlank { params.optString("text") }
                .ifBlank { "Message generated by AIOS." }
            val to = displayContactName(rawTo, message)
            val profileId = runtimeProfileId(params)
            val guard = synchronized(smsLock) {
                outboundSmsGuards(profileId).firstOrNull { it.appliesTo(to, message) && !it.isSatisfiedBy(smsInbox) }
            }
            if (guard != null) {
                if (guard.replacementMessage.isNotBlank()) {
                    val rewritten = JSONObject(params.toString())
                        .put("message", guard.replacementMessage)
                    return sendSms(rewritten)
                }
                return ok(
                    guard.message,
                    "sms" to mapOf(
                        "to" to to,
                        "displayName" to to,
                        "rawTo" to rawTo,
                        "message" to message,
                        "status" to "not_sent",
                    ),
                    "instruction" to guard.instruction,
                )
            }
            val item = mapOf(
                "id" to "sms-${System.currentTimeMillis()}",
                "to" to to,
                "displayName" to to,
                "rawTo" to rawTo,
                "message" to message,
                "status" to "sent",
                "sentAt" to System.currentTimeMillis(),
            )
            val watch = synchronized(smsLock) {
                smsOutbox += item
                createSmsWatch(contact = to, outboundMessage = message)
            }
            return ok(
                "SMS sent to $to",
                "sms" to item,
                "outboxSize" to smsOutbox.size,
                "listener" to mapOf(
                    "id" to watch.id,
                    "status" to "listening",
                    "contact" to watch.contact,
                ),
            )
        }

        private fun dialPhone(params: JSONObject): SystemRuntimeResult {
            val number = params.optString("number")
                .ifBlank { params.optString("phoneNumber") }
                .ifBlank { "110" }
            val item = mapOf(
                "id" to "call-${System.currentTimeMillis()}",
                "number" to number,
                "status" to "connected",
                "durationSeconds" to 38,
            )
            callLog += item
            return ok("Phone call connected: $number", "call" to item)
        }

        private fun notification(params: JSONObject): SystemRuntimeResult {
            val title = params.optString("title").ifBlank { "AIOS" }
            val body = params.optString("message").ifBlank { params.optString("body").ifBlank { "AIOS notification" } }
            return ok(
                "Notification posted: $title",
                "notification" to mapOf(
                    "title" to title,
                    "body" to body,
                    "postedAt" to System.currentTimeMillis(),
                ),
            )
        }

        private fun reminder(
            params: JSONObject,
            action: String,
        ): SystemRuntimeResult {
            val title = params.optString("title").ifBlank { params.optString("name").ifBlank { "提醒" } }
            val body = params.optString("message").ifBlank { params.optString("body") }
            val scheduledFor = reminderTimeText(params, title, body)
            val item = mapOf(
                "id" to "reminder-${System.currentTimeMillis()}",
                "type" to if (action == "long_reminder") "long_reminder" else "reminder",
                "title" to title,
                "body" to body,
                "scheduledFor" to scheduledFor,
                "status" to "scheduled",
                "createdAt" to System.currentTimeMillis(),
            )
            return ok("Long reminder created: $title", "reminder" to item)
        }

        private fun reminderTimeText(
            params: JSONObject,
            title: String,
            body: String,
        ): String {
            val explicit = firstText(
                params,
                "scheduledFor",
                "scheduledAt",
                "dateTime",
                "datetime",
                "time",
                "at",
                "triggerAt",
                "triggerTime",
            )
            if (explicit.isNotBlank()) return explicit
            val text = "$title $body"
            return Regex("""(?:\d{1,2}/\d{1,2}\s+)?\d{1,2}:\d{2}""")
                .find(text)
                ?.value
                ?: "按计划时间"
        }

        private fun payment(params: JSONObject): SystemRuntimeResult {
            val recipient = firstText(params, "recipient", "merchant", "to", "payee", "contact")
                .ifBlank { selectedServiceName ?: "Service Contact" }
            val amount = paymentAmountText(recipient, params).ifBlank { "amount not specified" }
            val item = mapOf(
                "id" to "payment-${System.currentTimeMillis()}",
                "recipient" to recipient,
                "amount" to amount,
                "status" to "completed",
                "paidAt" to System.currentTimeMillis(),
            )
            return ok("Payment completed: $amount to $recipient", "payment" to item)
        }

        private fun recordExpense(params: JSONObject): SystemRuntimeResult {
            val merchant = firstText(params, "merchant", "recipient", "payee", "contact")
                .ifBlank { selectedServiceName ?: "Service Contact" }
            val amount = paymentAmountText(merchant, params).ifBlank { "amount not specified" }
            val category = params.optString("category").ifBlank { "service" }
            val note = params.optString("note")
                .ifBlank { params.optString("description") }
                .ifBlank { "Service expense" }
            val item = mapOf(
                "id" to "expense-${System.currentTimeMillis()}",
                "merchant" to merchant,
                "amount" to amount,
                "category" to category,
                "note" to note,
                "status" to "recorded",
                "recordedAt" to System.currentTimeMillis(),
            )
            return ok("Expense recorded: $amount at $merchant", "expense" to item)
        }

        private fun amountText(params: JSONObject): String {
            val raw = firstText(
                params,
                "amount",
                "amountCny",
                "fee",
                "feeCny",
                "price",
                "priceCny",
                "cost",
                "costCny",
                "total",
                "totalCny",
            )
            if (raw.isNotBlank()) return raw
            val cents = params.optLong("amountCents", -1)
            if (cents >= 0) return "${cents / 100.0} yuan"
            return ""
        }

        private fun paymentAmountText(
            recipient: String,
            params: JSONObject,
        ): String {
            val raw = amountText(params)
            val inbox = synchronized(smsLock) { smsInbox.toList() }
            return paymentAmountRules()
                .firstOrNull { it.appliesTo(recipient, params, inbox) }
                ?.amount
                ?: raw
        }

        private fun location(params: JSONObject): SystemRuntimeResult {
            val label = params.optString("label").ifBlank { "home" }
            val place = knownPlaces()[label] ?: knownPlaces().getValue("home")
            return ok("Location resolved: $label", "location" to place)
        }

        private fun contacts(params: JSONObject): SystemRuntimeResult {
            val query = firstText(params, "query", "name", "contact", "recipient", "role", "capability").lowercase()
            val role = params.optString("role").lowercase()
            val capability = params.optString("capability").lowercase()
            val contacts = contactDirectory().filter { contact ->
                val searchable = contact.searchText()
                (query.isBlank() || searchable.contains(query)) &&
                    (role.isBlank() || searchable.contains(role)) &&
                    (capability.isBlank() || contact.capabilities.any { it.lowercase().contains(capability) })
            }
            return ok(
                "Contacts returned",
                "contacts" to contacts.map { it.toMap() },
                "count" to contacts.size,
            )
        }

        private fun socialGraph(): SystemRuntimeResult =
            ok(
                "Social graph returned",
                "people" to contactDirectory().map {
                    mapOf(
                        "name" to it.displayName,
                        "relation" to it.relation,
                        "trustLevel" to it.trustLevel,
                    )
                },
            )

        private fun deviceState(): SystemRuntimeResult =
            ok(
                "Device state returned",
                "device" to mapOf(
                    "batteryPct" to 82,
                    "connectivity" to "wifi",
                    "screenOn" to true,
                    "locale" to "zh-CN",
                    "timezone" to "Asia/Shanghai",
                    "foregroundApp" to "com.mobilebot",
                ),
            )

        private fun readMemory(params: JSONObject): SystemRuntimeResult {
            val key = params.optString("key").lowercase().ifBlank { "user" }
            val value = runBlocking {
                userProfileStore.get("user_memory", key) ?: userProfileStore.get(key)
            }
            return if (value.isNullOrBlank()) {
                SystemRuntimeResult(false, "Memory not found: $key", baseData("key" to key))
            } else {
                ok("Memory returned: $key", "key" to key, "content" to value)
            }
        }

        private suspend fun serviceCall(params: JSONObject): SystemRuntimeResult {
            val serviceId = params.optString("serviceId").ifBlank { params.optString("service") }
            val action = params.optString("action").ifBlank { params.optString("name") }
            val configuredService = serviceDirectory().firstOrNull { it.matches(serviceId) }
            if (configuredService != null) {
                return callConfiguredService(configuredService, action, params)
            }
            val data = VirtualMockData.lookup(serviceId, action, jsonObjectToMap(params.optJSONObject("params")))
            return if (data == null) {
                ok(
                    "Service gateway echo for $serviceId.$action",
                    "serviceId" to serviceId,
                    "action" to action,
                    "params" to jsonObjectToMap(params.optJSONObject("params")),
                )
            } else {
                ok("Service gateway response for $serviceId.$action", "result" to data)
            }
        }

        private fun knownPlaces(): Map<String, Map<String, Any?>> =
            buildMap {
                putAll(
                    mapOf(
                        "home" to mapOf("label" to "home", "address" to "Shanghai Pudong home", "lat" to 31.2304, "lng" to 121.4737),
                        "office" to mapOf("label" to "office", "address" to "AIOS Lab, Zhangjiang", "lat" to 31.207, "lng" to 121.599),
                    ),
                )
                for (profile in runtimeProfiles) {
                    putAll(profile.places)
                }
            }

        private suspend fun callConfiguredService(
            service: RuntimeService,
            action: String,
            params: JSONObject,
        ): SystemRuntimeResult {
            val toolName = service.normalizeAction(action)
            if (toolName.isBlank()) {
                return SystemRuntimeResult(
                    ok = false,
                    message = "Service action is required",
                    data = baseData("serviceId" to service.id),
                )
            }
            val arguments = params.optJSONObject("arguments")
                ?: params.optJSONObject("params")
                ?: JSONObject().also { target ->
                    for (key in params.keys()) {
                        if (key !in setOf("serviceId", "service", "action", "name")) {
                            target.put(key, params.opt(key))
                        }
                    }
                }
            val payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "tools/call")
                .put(
                    "params",
                    JSONObject()
                        .put("name", toolName)
                        .put("arguments", arguments),
                )
            return try {
                val response = postJsonWithRetry(service.endpoint, payload)
                val data = parseServiceToolResult(response)
                rememberSelectedService(data)
                ok(
                    "Service response for ${service.id}.$toolName",
                    "serviceId" to service.id,
                    "action" to toolName,
                    "result" to data,
                )
            } catch (e: Exception) {
                SystemRuntimeResult(
                    ok = false,
                    message = e.message ?: "Service call failed",
                    data = baseData("serviceId" to service.id, "action" to toolName),
                )
            }
        }

        private suspend fun postJson(
            url: String,
            payload: JSONObject,
        ): String =
            withContext(Dispatchers.IO) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 45_000
                    doOutput = true
                    setRequestProperty("Accept", "application/json, text/event-stream")
                    setRequestProperty("Content-Type", "application/json")
                }
                try {
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(payload.toString())
                    }
                    val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                    val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                    if (conn.responseCode !in 200..299) {
                        throw IllegalStateException("Service HTTP ${conn.responseCode}: ${body.take(180)}")
                    }
                    body
                } finally {
                    conn.disconnect()
                }
            }

        private suspend fun postJsonWithRetry(
            url: String,
            payload: JSONObject,
        ): String {
            var lastError: Exception? = null
            repeat(SERVICE_CALL_ATTEMPTS) { attempt ->
                try {
                    return postJson(url, payload)
                } catch (e: Exception) {
                    lastError = e
                    if (!isRetriableServiceError(e) || attempt == SERVICE_CALL_ATTEMPTS - 1) throw e
                    delay(SERVICE_CALL_RETRY_DELAY_MS)
                }
            }
            throw lastError ?: IllegalStateException("Service call failed")
        }

        private fun isRetriableServiceError(error: Exception): Boolean {
            val message = error.message.orEmpty().lowercase()
            return error is SocketTimeoutException ||
                message.contains("timed out") ||
                message.contains("timeout")
        }

        private fun parseServiceToolResult(response: String): Map<String, Any?> {
            val root = JSONObject(response)
            val error = root.optJSONObject("error")
            if (error != null) {
                throw IllegalStateException(error.optString("message").ifBlank { "Service error" })
            }
            val result = root.optJSONObject("result")
                ?: return mapOf("raw" to response)
            val content = result.optJSONArray("content")
            val text = content?.optJSONObject(0)?.optString("text").orEmpty()
            if (text.isBlank()) return jsonObjectToMap(result)
            val parsed = runCatching { JSONTokener(text).nextValue() }.getOrNull()
                ?: return mapOf("text" to text)
            return when (parsed) {
                is JSONObject -> jsonObjectToMap(parsed)
                is JSONArray -> mapOf("items" to jsonArrayToList(parsed))
                else -> mapOf("text" to parsed.toString())
            }
        }

        private fun rememberSelectedService(data: Map<String, Any?>) {
            selectedServiceName = findFirstName(data) ?: selectedServiceName
        }

        private fun findFirstName(value: Any?): String? =
            when (value) {
                is Map<*, *> -> {
                    value["name"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: value["displayName"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: value.values.firstNotNullOfOrNull { findFirstName(it) }
                }
                is List<*> -> value.firstNotNullOfOrNull { findFirstName(it) }
                else -> null
            }

        private fun ok(
            message: String,
            vararg pairs: Pair<String, Any?>,
        ): SystemRuntimeResult = SystemRuntimeResult(true, message, baseData(*pairs))

        private fun baseData(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
            linkedMapOf<String, Any?>(
                "source" to "device_system",
            ).apply { putAll(pairs) }

        private fun jsonObjectToMap(obj: JSONObject?): Map<String, Any?> {
            if (obj == null) return emptyMap()
            val out = linkedMapOf<String, Any?>()
            for (key in obj.keys()) out[key] = jsonValue(obj.opt(key))
            return out
        }

        private fun jsonArrayToList(arr: JSONArray): List<Any?> =
            (0 until arr.length()).map { index -> jsonValue(arr.opt(index)) }

        private fun jsonValue(value: Any?): Any? =
            when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }

        private fun createSmsWatch(
            contact: String,
            outboundMessage: String,
        ): SmsWatch {
            val id = "sms-watch-${System.currentTimeMillis()}-${smsWatches.size + 1}"
            val watch = SmsWatch(
                id = id,
                contact = contact,
                outboundMessage = outboundMessage,
                createdAt = System.currentTimeMillis(),
            )
            smsWatches[id] = watch
            return watch
        }

        private fun findSmsWatch(params: JSONObject): SmsWatch? {
            val watchId = params.optString("watchId").ifBlank { params.optString("listenerId") }
            val rawContact = firstText(params, "from", "contact", "sender", "recipient", "name")
            val requestedContact = displayContactName(
                rawContact,
                params.optString("context").ifBlank { params.optString("lastMessage") },
            )
            if (watchId.isNotBlank()) {
                val watch = smsWatches[watchId]
                if (watch != null && (isGenericContact(rawContact) || contactsMatch(watch.contact, requestedContact))) {
                    return watch
                }
            }
            if (isGenericContact(rawContact)) return smsWatches.values.lastOrNull()
            val contact = requestedContact.lowercase()
            return smsWatches.values.lastOrNull { watch ->
                contact.isBlank() || contactsMatch(watch.contact, contact)
            }
        }

        private fun contactsMatch(
            first: String,
            second: String,
        ): Boolean {
            val left = first.trim().lowercase()
            val right = second.trim().lowercase()
            return left.isNotBlank() && right.isNotBlank() && (left.contains(right) || right.contains(left))
        }

        private fun generateSmsReply(
            watch: SmsWatch,
            inboxSnapshot: List<Map<String, Any?>>,
        ): SmsReply {
            val contact = watch.contact.lowercase()
            val outbound = watch.outboundMessage.trim().lowercase()
            val rule = smsResponseRules()
                .mapNotNull { rule ->
                    val score = rule.matchScore(contact, outbound)
                    if (score > 0) score to rule else null
                }
                .maxByOrNull { it.first }
                ?.second
            if (rule != null && rule.isBlockedBy(inboxSnapshot)) {
                return SmsReply(
                    message = rule.blockedReply.ifBlank { "暂不能读取这条更新，需要先完成前置确认。" },
                    decisionPrompt = null,
                    eventType = null,
                )
            }
            return SmsReply(
                message = rule?.let { renderSmsReply(it, outbound) } ?: "收到，我会继续更新。",
                decisionPrompt = rule?.decisionPrompt,
                eventType = rule?.eventType,
            )
        }

        private fun renderSmsReply(
            rule: SmsResponseRule,
            outbound: String,
        ): String =
            rule.reply
                .replace("{pickup_time}", extractTimeNearPickup(outbound) ?: "按约定时间")
                .replace("{first_time}", extractFirstTime(outbound) ?: "40分钟后")

        private fun extractTimeNearPickup(text: String): String? {
            val pickupToken = Regex(
                """pick\s*up|pickup|come\s*get|接|楼下""",
                RegexOption.IGNORE_CASE,
            ).find(text)
            val timeBeforePickup = pickupToken?.range?.first?.let { pickupIndex ->
                extractLastTime(text.take(pickupIndex).takeLast(80))
            }
            if (timeBeforePickup != null) return timeBeforePickup

            val pickupPattern = Regex(
                """(?:pick\s*up|pickup|come\s*get|接|楼下).{0,120}?(\d{1,2}:\d{2}\s*(?:am|pm)?)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
            return pickupPattern.find(text)?.groupValues?.getOrNull(1)?.formatSmsTime()
                ?: extractFirstTime(text)
        }

        private fun extractLastTime(text: String): String? =
            Regex("""(?<!\d)\d{1,2}:\d{2}\s*(?:am|pm)?(?!\d)""", RegexOption.IGNORE_CASE)
                .findAll(text)
                .lastOrNull()
                ?.value
                ?.formatSmsTime()

        private fun extractFirstTime(text: String): String? =
            Regex("""(?<!\d)\d{1,2}:\d{2}\s*(?:am|pm)?(?!\d)""", RegexOption.IGNORE_CASE)
                .find(text)
                ?.value
                ?.formatSmsTime()

        private fun String.formatSmsTime(): String =
            trim()
                .replace(Regex("""\s+"""), " ")
                .uppercase()

        private fun firstText(
            obj: JSONObject,
            vararg keys: String,
        ): String =
            keys.firstNotNullOfOrNull { key ->
                if (!obj.has(key) || obj.isNull(key)) null
                else obj.optString(key).trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            }.orEmpty()

        private fun displayContactName(
            raw: String,
            message: String,
        ): String {
            val value = raw.trim()
            resolveContactDisplayName(value)?.let { return it }
            return when {
                isGenericContact(value) -> inferContactName(message)
                value.isBlank() -> inferContactName(message)
                else -> value
            }
        }

        private fun resolveContactDisplayName(query: String): String? {
            if (isGenericContact(query)) return null
            val lower = query.lowercase()
            return contactDirectory().firstOrNull { contact ->
                contact.displayName.lowercase() == lower ||
                    contact.phone == query ||
                    contact.aliases.any { alias ->
                        val aliasLower = alias.lowercase()
                        lower.contains(aliasLower) || aliasLower.contains(lower)
                    }
            }?.displayName
        }

        private fun inferContactName(message: String): String {
            val lower = message.lowercase()
            return contactDirectory()
                .map { contact -> contact to contact.matchScore(lower) }
                .filter { (_, score) -> score > 0 }
                .maxByOrNull { (_, score) -> score }
                ?.first
                ?.displayName
                ?: selectedServiceName
                ?: "Service Contact"
        }

        private fun isGenericContact(value: String): Boolean {
            val normalized = value.trim().lowercase()
            return normalized.isBlank() ||
                normalized == "contact" ||
                normalized == "recipient" ||
                normalized == "sender" ||
                normalized == "service contact" ||
                normalized == "service provider" ||
                normalized == "provider" ||
                normalized == "selected provider" ||
                normalized == "business" ||
                normalized == "merchant"
        }

        private fun contactDirectory(): List<SystemContact> =
            runtimeProfiles.flatMap { it.contacts }.ifEmpty {
                listOf(
                    SystemContact(
                        id = "service-contact",
                        displayName = "Service Contact",
                        relation = "service_provider",
                        phone = "",
                        aliases = listOf("service", "provider", "merchant"),
                        channels = listOf("sms", "phone"),
                        capabilities = listOf("service"),
                        messageKeywords = listOf("service", "provider", "merchant"),
                        trustLevel = "medium",
                        note = "General service contact.",
                    ),
                )
            }

        private fun serviceDirectory(): List<RuntimeService> =
            runtimeProfiles.flatMap { it.services }

        private fun smsResponseRules(): List<SmsResponseRule> =
            runtimeProfiles.flatMap { it.smsResponses }

        private fun outboundSmsGuards(profileId: String?): List<OutboundSmsGuard> =
            runtimeProfiles
                .filter { profileId.isNullOrBlank() || it.id == profileId }
                .flatMap { it.outboundSmsGuards }

        private fun runtimeProfileId(params: JSONObject): String? =
            params.optString("scenarioId")
                .ifBlank { params.optString("profileId") }
                .ifBlank { params.optString("runtimeProfileId") }
                .trim()
                .takeIf { it.isNotBlank() }

        private fun paymentAmountRules(): List<PaymentAmountRule> =
            runtimeProfiles.flatMap { it.paymentAmountRules }

        private data class SmsWatch(
            val id: String,
            val contact: String,
            val outboundMessage: String,
            val createdAt: Long,
        )

        private data class SystemContact(
            val id: String,
            val displayName: String,
            val relation: String,
            val phone: String,
            val aliases: List<String>,
            val channels: List<String>,
            val capabilities: List<String>,
            val messageKeywords: List<String>,
            val trustLevel: String,
            val note: String,
        ) {
            fun searchText(): String =
                (listOf(id, displayName, relation, phone, note) + aliases + channels + capabilities + messageKeywords)
                    .joinToString(" ")
                    .lowercase()

            fun matchScore(text: String): Int =
                (aliases + capabilities + messageKeywords + relation)
                    .map { it.trim().lowercase() }
                    .filter { it.length >= 2 }
                    .count { text.contains(it) }

            fun toMap(): Map<String, Any?> =
                mapOf(
                    "id" to id,
                    "displayName" to displayName,
                    "relation" to relation,
                    "phone" to phone,
                    "aliases" to aliases,
                    "channels" to channels,
                    "capabilities" to capabilities,
                    "messageKeywords" to messageKeywords,
                    "trustLevel" to trustLevel,
                    "note" to note,
                )
        }

        private data class RuntimeService(
            val id: String,
            val aliases: List<String>,
            val endpoint: String,
            val actionAliases: Map<String, String>,
        ) {
            fun matches(serviceId: String): Boolean {
                val normalized = serviceId.trim().lowercase()
                return normalized.isNotBlank() &&
                    (id.lowercase() == normalized || aliases.any { it.lowercase() == normalized })
            }

            fun normalizeAction(action: String): String {
                val trimmed = action.trim()
                return actionAliases[trimmed] ?: actionAliases[trimmed.lowercase()] ?: trimmed
            }
        }

        private data class SmsResponseRule(
            val contactAliases: List<String>,
            val keywords: List<String>,
            val excludeKeywords: List<String>,
            val blockedUntilContactAliases: List<String>,
            val blockedUntilKeywords: List<String>,
            val blockedReply: String,
            val priority: Int,
            val reply: String,
            val decisionPrompt: RuntimeDecisionPrompt?,
            val eventType: String?,
        ) {
            fun matchScore(
                contact: String,
                outbound: String,
            ): Int {
                val contactMatches = contactAliases.isEmpty() ||
                    contactAliases.any { alias ->
                        val normalized = alias.trim().lowercase()
                        normalized.isNotBlank() && (contact.contains(normalized) || normalized.contains(contact))
                    }
                if (!contactMatches) return 0

                val matchedKeywords = keywords.count {
                    val keyword = it.trim().lowercase()
                    keyword.isNotBlank() && outbound.contains(keyword)
                }
                val keywordMatches = keywords.isEmpty() || matchedKeywords > 0
                val excluded = excludeKeywords.any { outbound.contains(it.trim().lowercase()) }
                if (!keywordMatches || excluded) return 0
                return priority + if (keywords.isEmpty()) 1 else matchedKeywords * 10
            }

            fun isBlockedBy(inbox: List<Map<String, Any?>>): Boolean {
                if (blockedUntilContactAliases.isEmpty() && blockedUntilKeywords.isEmpty()) return false
                return inbox.none { item ->
                    val contact = listOf(
                        item["from"],
                        item["displayName"],
                        item["to"],
                    ).joinToString(" ") { it?.toString().orEmpty() }.lowercase()
                    val message = item["message"]?.toString().orEmpty().lowercase()
                    val contactMatches = blockedUntilContactAliases.isEmpty() ||
                        blockedUntilContactAliases.any { alias ->
                            val normalized = alias.trim().lowercase()
                            normalized.isNotBlank() && (contact.contains(normalized) || normalized.contains(contact))
                        }
                    val keywordMatches = blockedUntilKeywords.isEmpty() ||
                        blockedUntilKeywords.any { keyword ->
                            val normalized = keyword.trim().lowercase()
                            normalized.isNotBlank() && message.contains(normalized)
                        }
                    contactMatches && keywordMatches
                }
            }

        }

        private data class OutboundSmsGuard(
            val contactAliases: List<String>,
            val messageKeywords: List<String>,
            val untilContactAliases: List<String>,
            val untilKeywords: List<String>,
            val message: String,
            val instruction: String,
            val replacementMessage: String,
        ) {
            fun appliesTo(
                contact: String,
                outboundMessage: String,
            ): Boolean {
                val normalizedContact = contact.lowercase()
                val normalizedMessage = outboundMessage.lowercase()
                val contactMatches = contactAliases.isEmpty() ||
                    contactAliases.any { aliasMatches(normalizedContact, it) }
                val messageMatches = messageKeywords.isEmpty() ||
                    messageKeywords.any { normalizedMessage.contains(it.trim().lowercase()) }
                return contactMatches && messageMatches
            }

            fun isSatisfiedBy(inbox: List<Map<String, Any?>>): Boolean {
                if (untilContactAliases.isEmpty() && untilKeywords.isEmpty()) return false
                return inbox.any { item ->
                    val from = item["from"]?.toString().orEmpty().lowercase()
                    val body = item["message"]?.toString().orEmpty().lowercase()
                    val contactMatches = untilContactAliases.isEmpty() ||
                        untilContactAliases.any { aliasMatches(from, it) }
                    val keywordMatches = untilKeywords.isEmpty() ||
                        untilKeywords.any { body.contains(it.trim().lowercase()) }
                    contactMatches && keywordMatches
                }
            }

            private fun aliasMatches(
                value: String,
                alias: String,
            ): Boolean {
                val normalized = alias.trim().lowercase()
                return normalized.isNotBlank() && (value.contains(normalized) || normalized.contains(value))
            }
        }

        private data class PaymentAmountRule(
            val recipientAliases: List<String>,
            val smsKeywords: List<String>,
            val paramKeywords: List<String>,
            val amount: String,
        ) {
            fun appliesTo(
                recipient: String,
                params: JSONObject,
                inbox: List<Map<String, Any?>>,
            ): Boolean {
                val normalizedRecipient = recipient.lowercase()
                val recipientMatches = recipientAliases.isEmpty() ||
                    recipientAliases.any { aliasMatches(normalizedRecipient, it) }
                if (!recipientMatches) return false

                val paramText = params.toString().lowercase()
                val paramMatches = paramKeywords.isEmpty() ||
                    paramKeywords.any { paramText.contains(it.trim().lowercase()) }
                if (!paramMatches) return false

                return smsKeywords.isEmpty() ||
                    inbox.any { item ->
                        val body = item["message"]?.toString().orEmpty().lowercase()
                        smsKeywords.all { body.contains(it.trim().lowercase()) }
                    }
            }

            private fun aliasMatches(
                value: String,
                alias: String,
            ): Boolean {
                val normalized = alias.trim().lowercase()
                return normalized.isNotBlank() && (value.contains(normalized) || normalized.contains(value))
            }
        }

        private data class SmsReply(
            val message: String,
            val decisionPrompt: RuntimeDecisionPrompt?,
            val eventType: String?,
        )

        private data class RuntimeDecisionPrompt(
            val prompt: String,
            val actions: List<RuntimeDecisionAction>,
        ) {
            fun toMap(): Map<String, Any?> =
                mapOf(
                    "prompt" to prompt,
                    "actions" to actions.map { it.toMap() },
                )
        }

        private data class RuntimeDecisionAction(
            val label: String,
            val value: String,
        ) {
            fun toMap(): Map<String, Any?> =
                mapOf(
                    "label" to label,
                    "value" to value,
                )
        }

        private data class SystemRuntimeProfile(
            val id: String,
            val contacts: List<SystemContact>,
            val places: Map<String, Map<String, Any?>>,
            val services: List<RuntimeService>,
            val scenarioEvents: List<SystemRuntimeScriptEvent>,
            val smsResponses: List<SmsResponseRule>,
            val outboundSmsGuards: List<OutboundSmsGuard>,
            val paymentAmountRules: List<PaymentAmountRule>,
        )

        private fun loadRuntimeProfiles(): List<SystemRuntimeProfile> {
            val skillDirs = runCatching { context.assets.list(SKILL_MD_DIR) }.getOrNull().orEmpty()
            return skillDirs.mapNotNull { dir ->
                val path = "$SKILL_MD_DIR/$dir/$SYSTEM_RUNTIME_FILE"
                val text = runCatching {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                }.getOrNull() ?: return@mapNotNull null
                runCatching { parseRuntimeProfile(dir, JSONObject(text)) }
                    .onFailure { Log.w(TAG, "Failed to parse $path", it) }
                    .getOrNull()
            }
        }

        private fun parseRuntimeProfile(
            fallbackId: String,
            root: JSONObject,
        ): SystemRuntimeProfile {
            val scenarioEvents = parseScenarioEvents(root.optJSONArray("scenarioEvents"))
            val id = root.optString("scenarioId")
                .ifBlank { scenarioEvents.firstOrNull()?.scenarioId.orEmpty() }
                .ifBlank { fallbackId }
            return SystemRuntimeProfile(
                id = id,
                contacts = parseContacts(root.optJSONArray("contacts")),
                places = parsePlaces(root.optJSONObject("places")),
                services = parseServices(root.optJSONArray("services")),
                scenarioEvents = scenarioEvents,
                smsResponses = parseSmsResponses(root.optJSONArray("smsResponses")),
                outboundSmsGuards = parseOutboundSmsGuards(root.optJSONArray("outboundSmsGuards")),
                paymentAmountRules = parsePaymentAmountRules(root.optJSONArray("paymentAmountRules")),
            )
        }

        private fun parseContacts(arr: JSONArray?): List<SystemContact> =
            buildList {
                if (arr == null) return@buildList
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val displayName = obj.optString("displayName")
                        .ifBlank { obj.optString("name") }
                    if (displayName.isBlank()) continue
                    add(
                        SystemContact(
                            id = obj.optString("id").ifBlank { displayName.lowercase().replace(" ", "-") },
                            displayName = displayName,
                            relation = obj.optString("relation"),
                            phone = obj.optString("phone"),
                            aliases = obj.optStringList("aliases"),
                            channels = obj.optStringList("channels").ifEmpty { listOf("sms", "phone") },
                            capabilities = obj.optStringList("capabilities"),
                            messageKeywords = obj.optStringList("messageKeywords"),
                            trustLevel = obj.optString("trustLevel").ifBlank { "medium" },
                            note = obj.optString("note"),
                        ),
                    )
                }
            }

        private fun parsePlaces(obj: JSONObject?): Map<String, Map<String, Any?>> {
            if (obj == null) return emptyMap()
            val out = linkedMapOf<String, Map<String, Any?>>()
            for (key in obj.keys()) {
                val place = obj.optJSONObject(key) ?: continue
                out[key] = jsonObjectToMap(place)
            }
            return out
        }

        private fun parseServices(arr: JSONArray?): List<RuntimeService> =
            buildList {
                if (arr == null) return@buildList
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").ifBlank { obj.optString("serviceId") }
                    val endpoint = obj.optString("endpoint")
                    if (id.isBlank() || endpoint.isBlank()) continue
                    add(
                        RuntimeService(
                            id = id,
                            aliases = obj.optStringList("aliases"),
                            endpoint = endpoint,
                            actionAliases = obj.optJSONObject("actionAliases")?.toStringMap().orEmpty(),
                        ),
                    )
                }
            }

        private fun parseScenarioEvents(arr: JSONArray?): List<SystemRuntimeScriptEvent> =
            buildList {
                if (arr == null) return@buildList
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val time = obj.optString("time").trim()
                    val type = obj.optString("type").trim()
                    if (id.isBlank() || time.isBlank() || type.isBlank()) continue
                    add(
                        SystemRuntimeScriptEvent(
                            id = id,
                            time = time,
                            type = type,
                            source = obj.optString("source").trim(),
                            title = obj.optString("title").trim(),
                            body = obj.optString("body").trim(),
                            scenarioId = obj.optString("scenarioId").ifBlank { "one_hour_aio" },
                        ),
                    )
                }
            }

        private fun parseSmsResponses(arr: JSONArray?): List<SmsResponseRule> =
            buildList {
                if (arr == null) return@buildList
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val reply = obj.optString("reply")
                    if (reply.isBlank()) continue
                    add(
                        run {
                            val until = obj.optJSONObject("blockedUntil") ?: obj.optJSONObject("until")
                            SmsResponseRule(
                                contactAliases = obj.optStringList("contactAliases"),
                                keywords = obj.optStringList("keywords"),
                                excludeKeywords = obj.optStringList("excludeKeywords"),
                                blockedUntilContactAliases = until?.optStringList("contactAliases").orEmpty(),
                                blockedUntilKeywords = until?.optStringList("keywords").orEmpty(),
                                blockedReply = obj.optString("blockedReply").trim(),
                                priority = obj.optInt("priority", 0),
                                reply = reply,
                                decisionPrompt = parseDecisionPrompt(obj.optJSONObject("decisionPrompt")),
                                eventType = obj.optString("eventType").trim().takeIf { it.isNotBlank() },
                            )
                        },
                    )
                }
            }

        private fun parseDecisionPrompt(obj: JSONObject?): RuntimeDecisionPrompt? {
            if (obj == null) return null
            val prompt = obj.optString("prompt")
                .ifBlank { obj.optString("text") }
                .trim()
            if (prompt.isBlank()) return null
            val actions = buildList {
                val arr = obj.optJSONArray("actions") ?: obj.optJSONArray("options")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val label = item.optString("label")
                            .ifBlank { item.optString("text") }
                            .ifBlank { item.optString("title") }
                            .trim()
                        val value = item.optString("value")
                            .ifBlank { label }
                            .trim()
                        if (label.isNotBlank() && value.isNotBlank()) {
                            add(RuntimeDecisionAction(label, value))
                        }
                    }
                }
            }
            return RuntimeDecisionPrompt(prompt, actions)
        }

        private fun parseOutboundSmsGuards(arr: JSONArray?): List<OutboundSmsGuard> =
            buildList {
                if (arr == null) return@buildList
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val message = obj.optString("message").trim()
                    if (message.isBlank()) continue
                    val until = obj.optJSONObject("blockedUntil") ?: obj.optJSONObject("until")
                    add(
                        OutboundSmsGuard(
                            contactAliases = obj.optStringList("contactAliases"),
                            messageKeywords = obj.optStringList("messageKeywords"),
                            untilContactAliases = until?.optStringList("contactAliases").orEmpty(),
                            untilKeywords = until?.optStringList("keywords").orEmpty(),
                            message = message,
                            instruction = obj.optString("instruction").ifBlank { message },
                            replacementMessage = obj.optString("replacementMessage").trim(),
                        ),
                    )
                }
            }

        private fun parsePaymentAmountRules(arr: JSONArray?): List<PaymentAmountRule> =
            buildList {
                if (arr == null) return@buildList
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val amount = obj.optString("amount").ifBlank { obj.optString("amountCny") }.trim()
                    if (amount.isBlank()) continue
                    add(
                        PaymentAmountRule(
                            recipientAliases = obj.optStringList("recipientAliases"),
                            smsKeywords = obj.optStringList("smsKeywords"),
                            paramKeywords = obj.optStringList("paramKeywords"),
                            amount = amount,
                        ),
                    )
                }
            }

        private fun JSONObject.optStringList(key: String): List<String> {
            val arr = optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        }

        private fun JSONObject.toStringMap(): Map<String, String> =
            buildMap {
                for (key in keys()) {
                    val value = optString(key).trim()
                    if (value.isNotBlank()) {
                        put(key, value)
                        put(key.lowercase(), value)
                    }
                }
            }

        companion object {
            private const val TAG = "SystemRuntime"
            private const val USER_MEMORY_DIR = "user_memory"
            private const val SKILL_MD_DIR = "skills/md"
            private const val SYSTEM_RUNTIME_FILE = "SYSTEM_RUNTIME.json"
            private const val SMS_MIN_DELAY_MS = 30_000L
            private const val SMS_MAX_DELAY_MS = 60_000L
            private const val SERVICE_CALL_ATTEMPTS = 2
            private const val SERVICE_CALL_RETRY_DELAY_MS = 750L
        }
    }
