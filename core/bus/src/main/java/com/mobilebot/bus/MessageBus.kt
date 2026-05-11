package com.mobilebot.bus

import com.mobilebot.model.InboundMessage
import com.mobilebot.model.OutboundMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageBus @Inject constructor() {
    private val _inbound = MutableSharedFlow<InboundMessage>(extraBufferCapacity = 64)
    private val _outbound = MutableSharedFlow<OutboundMessage>(extraBufferCapacity = 128)

    val inbound: SharedFlow<InboundMessage> = _inbound.asSharedFlow()
    val outbound: SharedFlow<OutboundMessage> = _outbound.asSharedFlow()

    suspend fun publishInbound(msg: InboundMessage) {
        _inbound.emit(msg)
    }

    suspend fun publishOutbound(msg: OutboundMessage) {
        _outbound.emit(msg)
    }

    fun tryPublishOutbound(msg: OutboundMessage): Boolean = _outbound.tryEmit(msg)
}
