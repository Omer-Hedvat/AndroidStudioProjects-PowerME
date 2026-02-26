package com.omerhedvat.powerme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Represents a gym location with its available equipment.
 *
 * Use Cases:
 * - Home gym: Limited equipment (dumbbells, resistance bands, pull-up bar)
 * - Work gym: Full commercial gym equipment
 * - Travel gym: Hotel gym with basic machines
 *
 * Equipment-based filtering ensures exercise recommendations match available tools.
 *
 * Example:
 * ```kotlin
 * GymProfile(
 *     name = "Home",
 *     equipment = "Dumbbells,Resistance Bands,Pull-up Bar",
 *     isActive = true
 * )
 * ```
 */
@Serializable
@Entity(tableName = "gym_profiles")
data class GymProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Name of gym location (e.g., "Home", "Work", "24 Hour Fitness")
     */
    val name: String,

    /**
     * Comma-separated list of available equipment types.
     *
     * Standard equipment types:
     * - Barbell
     * - Dumbbells
     * - Cable (Cable machine/pulley system)
     * - Machine (Leg press, chest press, etc.)
     * - Bodyweight (Always available)
     * - Resistance Bands
     * - Pull-up Bar
     * - Kettlebell
     * - Sled
     * - Battle Ropes
     * - Medicine Ball
     * - Plyometric Box
     * - TRX/Suspension Trainer
     */
    val equipment: String,

    /**
     * Whether this gym is currently active.
     * Only one gym should be active at a time.
     */
    val isActive: Boolean = false,

    /**
     * Optional notes about the gym (e.g., "Open 24/7", "Squat rack usually busy")
     */
    val notes: String? = null,

    val dumbbellMinKg: Float? = null,
    val dumbbellMaxKg: Float? = null
) {
    /**
     * Returns equipment as a list for filtering.
     */
    fun getEquipmentList(): List<String> {
        return equipment.split(",").map { it.trim() }
    }

    /**
     * Checks if this gym has specific equipment.
     */
    fun hasEquipment(equipmentType: String): Boolean {
        return getEquipmentList().any {
            it.equals(equipmentType, ignoreCase = true)
        }
    }
}
