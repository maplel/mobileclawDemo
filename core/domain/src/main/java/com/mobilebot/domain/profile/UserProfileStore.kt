package com.mobilebot.domain.profile

interface UserProfileStore {
    suspend fun get(category: String, key: String? = null): String?
    suspend fun set(category: String, key: String, value: String)
    fun listCategories(): List<String>
}
