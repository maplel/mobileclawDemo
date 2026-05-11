package com.mobilebot.data.skills

import android.content.Context
import android.util.Log
import com.mobilebot.domain.skill.SkillContentLoader
import com.mobilebot.domain.skill.SkillEntry
import com.mobilebot.domain.skill.SkillSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillContentLoaderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SkillContentLoader {

    override suspend fun loadContent(entry: SkillEntry): String? {
        return loadContent(entry.contentPath)
    }

    override suspend fun loadContent(path: String): String? {
        if (path.isBlank()) return null

        return try {
            if (path.startsWith("assets://")) {
                val assetPath = path.removePrefix("assets://")
                context.assets.open(assetPath).bufferedReader().use { it.readText() }
            } else {
                val file = File(context.filesDir, path)
                if (file.exists()) file.readText() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load skill content from $path", e)
            null
        }
    }

    companion object {
        private const val TAG = "SkillContentLoader"
    }
}
