package com.mobilebot.bridge.impl

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.mobilebot.bridge.ContactsBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidContactsBridge
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ContactsBridge {
        override suspend fun searchContacts(
            query: String,
            limit: Int,
        ): List<String> =
            withContext(Dispatchers.IO) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    return@withContext emptyList()
                }
                val q = query.trim()
                if (q.isEmpty()) return@withContext emptyList()
                val resolver: ContentResolver = context.contentResolver
                val like = "%${q.replace("%", "\\%")}%"
                val out = LinkedHashSet<String>()
                val projection =
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                    )
                val sel =
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
                        "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                val args = arrayOf(like, like)
                resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    sel,
                    args,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
                )?.use { c ->
                    val nameIx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (c.moveToNext() && out.size < limit) {
                        val name = if (nameIx >= 0) c.getString(nameIx)?.trim().orEmpty() else ""
                        val num = if (numIx >= 0) c.getString(numIx)?.trim().orEmpty() else ""
                        if (name.isNotEmpty() || num.isNotEmpty()) {
                            out.add("$name|$num")
                        }
                    }
                }
                out.toList()
            }
    }
