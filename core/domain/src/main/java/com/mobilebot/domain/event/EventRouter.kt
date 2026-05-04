package com.mobilebot.domain.event

import android.util.Log
import com.mobilebot.bridge.EventSource
import com.mobilebot.bridge.ExternalEvent
import com.mobilebot.bus.MessageBus
import com.mobilebot.model.InboundMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subscribes to all registered [EventSource]s and routes their
 * [ExternalEvent]s into the agent system via [MessageBus.publishInbound].
 */
@Singleton
class EventRouter
    @Inject
    constructor(
        private val bus: MessageBus,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val sources = CopyOnWriteArrayList<EventSource>()

        fun registerSource(source: EventSource) {
            sources.add(source)
            scope.launch {
                source.events.collect { event ->
                    handleEvent(event)
                }
            }
            source.start()
            Log.d(TAG, "Registered and started EventSource: ${source.sourceId}")
        }

        fun unregisterSource(sourceId: String) {
            sources.removeIf { src ->
                if (src.sourceId == sourceId) {
                    src.stop()
                    true
                } else {
                    false
                }
            }
        }

        private suspend fun handleEvent(event: ExternalEvent) {
            Log.d(TAG, "Received event: type=${event.type}, source=${event.source}, priority=${event.priority}")
            val text = buildEventText(event)
            bus.publishInbound(
                InboundMessage(
                    channel = CHANNEL,
                    senderId = "event_router",
                    chatId = "event:${event.source}:${event.type}",
                    content = text,
                    metadata = mapOf(
                        "_event_type" to event.type,
                        "_event_source" to event.source,
                        "_event_priority" to event.priority.name,
                    ),
                ),
            )
        }

        private fun buildEventText(event: ExternalEvent): String {
            val payloadSummary = event.payload.entries.joinToString(", ") { "${it.key}=${it.value}" }
            return "[EVENT: ${event.type}] source=${event.source}, priority=${event.priority}, $payloadSummary"
        }

        private companion object {
            private const val TAG = "EventRouter"
            private const val CHANNEL = "event"
        }
    }
