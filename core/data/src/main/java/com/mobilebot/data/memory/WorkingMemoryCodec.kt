package com.mobilebot.data.memory

import com.mobilebot.domain.memory.WorkingMemory
import org.json.JSONArray
import org.json.JSONObject

internal object WorkingMemoryCodec {
    fun encode(m: WorkingMemory): String {
        val o = JSONObject()
        o.put("currentGoal", m.currentGoal)
        o.put("constraints", JSONArray(m.constraints))
        val entities = JSONObject()
        for ((k, v) in m.selectedEntities) entities.put(k, v)
        o.put("selectedEntities", entities)
        o.put("stepObservations", JSONArray(m.stepObservations))
        o.put("lastError", m.lastError)
        o.put("pendingApprovalId", m.pendingApprovalId)
        o.put("activeArtifactRefs", JSONArray(m.activeArtifactRefs))
        return o.toString()
    }

    fun decode(json: String?): WorkingMemory {
        if (json.isNullOrBlank()) return WorkingMemory()
        return runCatching {
            val o = JSONObject(json)
            val constraints = mutableListOf<String>()
            o.optJSONArray("constraints")?.let { arr ->
                for (i in 0 until arr.length()) constraints.add(arr.getString(i))
            }
            val entities = mutableMapOf<String, String>()
            o.optJSONObject("selectedEntities")?.let { eo ->
                val it = eo.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    entities[k] = eo.getString(k)
                }
            }
            val observations = mutableListOf<String>()
            o.optJSONArray("stepObservations")?.let { arr ->
                for (i in 0 until arr.length()) observations.add(arr.getString(i))
            }
            val refs = mutableListOf<String>()
            o.optJSONArray("activeArtifactRefs")?.let { arr ->
                for (i in 0 until arr.length()) refs.add(arr.getString(i))
            }
            WorkingMemory(
                currentGoal = o.optString("currentGoal").takeIf { it.isNotEmpty() },
                constraints = constraints,
                selectedEntities = entities,
                stepObservations = observations,
                lastError = o.optString("lastError").takeIf { it.isNotEmpty() },
                pendingApprovalId = o.optString("pendingApprovalId").takeIf { it.isNotEmpty() },
                activeArtifactRefs = refs,
            )
        }.getOrElse { WorkingMemory() }
    }
}
