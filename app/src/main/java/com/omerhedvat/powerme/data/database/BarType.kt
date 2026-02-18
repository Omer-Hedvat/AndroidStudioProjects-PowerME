package com.omerhedvat.powerme.data.database

import kotlinx.serialization.Serializable

@Serializable
enum class BarType(val weightKg: Double) {
    STANDARD(20.0),      // Standard Olympic bar
    WOMENS(15.0),        // Women's Olympic bar
    EZ_CURL(7.5),        // EZ curl bar (alias for EZ_BAR_75)
    EZ_BAR_75(7.5),      // EZ curl bar 7.5kg
    EZ_BAR_10(10.0),     // EZ curl bar 10kg
    TRAP_BAR(25.0),      // Trap/Hex bar
    SAFETY_SQUAT(30.0),  // Safety squat bar
    CUSTOM(0.0)          // Custom weight, user will specify
}
