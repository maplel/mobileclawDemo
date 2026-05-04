package com.mobilebot.data.skills

import android.content.Context
import android.util.Log
import com.mobilebot.bridge.ServiceActionDescriptor
import com.mobilebot.bridge.ServiceDescriptor
import com.mobilebot.bridge.ServiceGateway
import com.mobilebot.domain.skill.BundledSkillJsonParser
import com.mobilebot.domain.skill.SkillBootstrapper
import com.mobilebot.domain.skill.SkillEntry
import com.mobilebot.domain.skill.SkillMdParser
import com.mobilebot.domain.skill.SkillSource
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bootstrapper: SkillBootstrapper,
    private val serviceGateway: ServiceGateway,
) {

    fun loadAllSkills() {
        runCatching {
            loadSkillMdFromDir(BUNDLED_MD_DIR)
            loadJsonFromDir(BUNDLED_DIR)
            loadJsonFromDir(SCENARIOS_DIR)
        }.onFailure { e ->
            Log.e(TAG, "loadAllSkills failed", e)
        }
    }

    fun loadServiceConfigs() {
        runCatching {
            val names = context.assets.list(SERVICES_DIR) ?: return
            for (name in names) {
                if (!name.endsWith(".json", ignoreCase = true)) continue
                val path = "$SERVICES_DIR/$name"
                val text = context.assets.open(path).bufferedReader().use { it.readText() }
                val descriptor = parseServiceConfig(text) ?: continue
                serviceGateway.registerService(descriptor)
                Log.d(TAG, "Registered service: ${descriptor.id}")
            }
        }.onFailure { e ->
            Log.e(TAG, "loadServiceConfigs failed", e)
        }
    }

    private fun loadSkillMdFromDir(dir: String) {
        val items = runCatching { context.assets.list(dir) }.getOrNull() ?: return
        for (item in items) {
            val skillMdPath = "$dir/$item/SKILL.md"
            val content = runCatching {
                context.assets.open(skillMdPath).bufferedReader().use { it.readText() }
            }.getOrNull() ?: continue

            val skill = SkillMdParser.parse(content, SkillSource.BUNDLED_ASSET) ?: continue
            val entry = SkillEntry(
                manifest = skill.manifest,
                contentPath = "assets://$skillMdPath",
                source = SkillSource.BUNDLED_ASSET,
            )
            bootstrapper.registerSkillEntries(listOf(entry))
            Log.d(TAG, "Registered SKILL.md: ${skill.manifest.id} from $dir/$item")
        }
    }

    private fun loadJsonFromDir(dir: String) {
        val names = runCatching { context.assets.list(dir) }.getOrNull() ?: return
        for (name in names) {
            if (!name.endsWith(".json", ignoreCase = true)) continue
            val path = "$dir/$name"
            val text = runCatching {
                context.assets.open(path).bufferedReader().use { it.readText() }
            }.getOrNull() ?: continue

            val skill = BundledSkillJsonParser.parse(text) ?: continue
            val entry = skill.toEntry(contentPath = "assets://$path")
            bootstrapper.registerSkillEntries(listOf(entry))
            Log.d(TAG, "Registered JSON skill: ${skill.id} from $path")
        }
    }

    private fun parseServiceConfig(json: String): ServiceDescriptor? {
        return runCatching {
            val o = JSONObject(json)
            val id = o.getString("id").trim()
            val name = o.getString("name").trim()
            if (id.isEmpty() || name.isEmpty()) return null

            val category = o.optString("category", "").trim()
            val baseUrl = o.optString("baseUrl", "").trim()
            val authType = o.optString("authType", "none").trim()

            val actionsArr = o.optJSONArray("actions") ?: return null
            val actions = buildList {
                for (i in 0 until actionsArr.length()) {
                    val a = actionsArr.getJSONObject(i)
                    add(
                        ServiceActionDescriptor(
                            name = a.getString("name").trim(),
                            method = a.optString("method", "GET").trim(),
                            path = a.optString("path", "").trim(),
                            description = a.optString("description", "").trim(),
                            requiresUserApproval = a.optBoolean("requiresUserApproval", false),
                        ),
                    )
                }
            }
            ServiceDescriptor(
                id = id,
                name = name,
                category = category,
                baseUrl = baseUrl,
                authType = authType,
                actions = actions,
            )
        }.getOrNull()
    }

    companion object {
        private const val TAG = "SkillAssetLoader"
        private const val BUNDLED_DIR = "skills/bundled"
        private const val BUNDLED_MD_DIR = "skills/md"
        private const val SCENARIOS_DIR = "skills/scenarios"
        private const val SERVICES_DIR = "services"
    }
}
