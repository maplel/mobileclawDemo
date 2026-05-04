package com.mobilebot.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges [suspend] permission requests from the agent layer to [MainActivity]'s
 * [ActivityResultLauncher]. Must be [bindLauncher] from the activity before use.
 */
@Singleton
class PermissionRequestSession
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val mutex = Mutex()
        private var launcher: ActivityResultLauncher<Array<String>>? = null
        private var pending: CompletableDeferred<Boolean>? = null

        fun bindLauncher(l: ActivityResultLauncher<Array<String>>) {
            launcher = l
        }

        fun unbindLauncher() {
            launcher = null
            pending?.cancel()
            pending = null
        }

        fun onRequestResult(results: Map<String, Boolean>) {
            val p = pending
            pending = null
            if (p != null) {
                val ok = results.isNotEmpty() && results.all { it.value }
                p.complete(ok)
            }
        }

        suspend fun requestPermissions(permissions: Array<String>): Boolean {
            if (permissions.isEmpty()) return true
            val need =
                permissions.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }.toTypedArray()
            if (need.isEmpty()) return true

            return withContext(Dispatchers.Main.immediate) {
                val l = launcher ?: return@withContext false
                val deferred = CompletableDeferred<Boolean>()
                mutex.withLock {
                    if (pending != null) return@withContext false
                    pending = deferred
                }
                l.launch(need)
                try {
                    deferred.await()
                } finally {
                    mutex.withLock {
                        if (pending === deferred) {
                            pending = null
                        }
                    }
                }
            }
        }
    }
