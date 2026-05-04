package com.mobilebot.bridge

interface MediaBridge {
    /** Opens the system still-image camera; returns a short status message or error. */
    suspend fun launchStillCamera(): String
}
