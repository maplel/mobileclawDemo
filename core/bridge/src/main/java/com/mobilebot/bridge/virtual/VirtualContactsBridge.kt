package com.mobilebot.bridge.virtual

import android.util.Log
import com.mobilebot.bridge.ContactsBridge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirtualContactsBridge
    @Inject
    constructor() : ContactsBridge {
        override suspend fun searchContacts(query: String, limit: Int): List<String> {
            Log.d(TAG, "[VIRTUAL] searchContacts(query=$query, limit=$limit)")
            val matches = CONTACTS.filter { it.contains(query, ignoreCase = true) }
            return matches.take(limit).ifEmpty {
                listOf("No matching contacts found for '$query'")
            }
        }

        private companion object {
            private const val TAG = "VirtualContacts"
            private val CONTACTS = listOf(
                "张三 - 13800001111",
                "张伟 - 13800002222",
                "李四 - 13900003333",
                "王五 - 13700004444",
                "赵六 - 15800005555",
                "刘明 - 18600006666",
                "陈芳 - 13500007777",
                "John Smith - 15012348888",
                "Alice Wang - 18698765432",
            )
        }
    }
