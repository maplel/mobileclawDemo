package com.mobilebot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.mobilebot.chat.MobileBotApp
import com.mobilebot.permissions.PermissionRequestSession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var permissionRequestSession: PermissionRequestSession

    private val runtimePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            permissionRequestSession.onRequestResult(results)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionRequestSession.bindLauncher(runtimePermissionLauncher)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            MobileBotApp()
        }
    }

    override fun onDestroy() {
        permissionRequestSession.unbindLauncher()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type ?: return
        if (!type.startsWith("text/")) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val dir = File(filesDir, "workspace/shared").apply { mkdirs() }
            File(dir, "incoming.txt").writeText(text)
        }
    }
}
