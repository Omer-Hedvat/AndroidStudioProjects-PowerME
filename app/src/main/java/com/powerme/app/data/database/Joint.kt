package com.powerme.app.data.database

import com.powerme.app.data.database.HealthHistorySeverity

enum class Joint(val displayName: String) {
    CERVICAL_SPINE("Cervical Spine"),
    THORACIC_SPINE("Thoracic Spine"),
    LUMBAR_SPINE("Lumbar Spine"),
    SHOULDER("Shoulder"),
    ELBOW("Elbow"),
    WRIST("Wrist"),
    HIP("Hip"),
    KNEE("Knee"),
    ANKLE("Ankle"),
    FOOT("Foot");

    companion object {
        /**
         * Parses a JSON array string like ["KNEE","HIP"] into a list of Joint enum values.
         * Returns an empty list for blank/empty/invalid input.
         */
        fun fromJsonString(json: String): List<Joint> {
            if (json.isBlank()) return emptyList()
            return try {
                json.trim()
                    .removePrefix("[").removeSuffix("]")
                    .split(",")
                    .mapNotNull { token ->
                        val name = token.trim().removeSurrounding("\"")
                        runCatching { valueOf(name) }.getOrNull()
                    }
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * Serialises a list of joints to a JSON array string like ["KNEE","HIP"].
         */
        fun toJsonString(joints: List<Joint>): String {
            if (joints.isEmpty()) return ""
            return "[${joints.joinToString(",") { "\"${it.name}\"" }}]"
        }
    }
}

/**
 * Maps a HealthHistoryEntry to the set of joints its bodyRegion references.
 * Only MODERATE and SEVERE non-archived entries produce a non-empty set.
 */
fun HealthHistoryEntry.toAffectedJoints(): Set<Joint> {
    val severity = runCatching { HealthHistorySeverity.valueOf(severity) }.getOrNull()
        ?: return emptySet()
    if (severity == HealthHistorySeverity.MILD || severity == HealthHistorySeverity.RESOLVED) {
        return emptySet()
    }
    val region = bodyRegion?.lowercase() ?: return emptySet()
    return Joint.entries.filter { joint ->
        region.contains(joint.displayName.lowercase()) ||
            region.contains(joint.name.lowercase().replace("_", " "))
    }.toSet()
}
