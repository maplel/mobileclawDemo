package com.mobilebot.model

sealed class StreamEvent {
    data class Delta(val text: String) : StreamEvent()

    data class Progress(val content: String, val toolHint: Boolean) : StreamEvent()

    data class StreamEnd(val resuming: Boolean) : StreamEvent()

    data class Done(val fullText: String) : StreamEvent()

    data class Error(val message: String) : StreamEvent()
}
