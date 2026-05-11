package com.mobilebot.bridge.impl

import com.mobilebot.bridge.ContactsBridge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StubContactsBridge
    @Inject
    constructor() : ContactsBridge {
        override suspend fun searchContacts(
            query: String,
            limit: Int,
        ): List<String> = emptyList()
    }
