package com.mobilebot.bridge

interface ContactsBridge {
    suspend fun searchContacts(
        query: String,
        limit: Int = 10,
    ): List<String>
}
