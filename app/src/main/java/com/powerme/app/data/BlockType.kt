package com.powerme.app.data

enum class BlockType(val displayName: String, val description: String) {
    STRENGTH("Strength", "Traditional sets and reps"),
    AMRAP("AMRAP", "As Many Rounds As Possible"),
    RFT("RFT", "Rounds For Time"),
    EMOM("EMOM", "Every Minute On the Minute"),
    TABATA("Tabata", "High-intensity interval training")
}

/**
 * Generates a default block name from the given parameters.
 * All duration/cap values are in minutes; [emomIntervalSec] is in seconds.
 */
fun autoBlockName(
    type: BlockType,
    durationMinutes: Int? = null,
    rounds: Int? = null,
    emomIntervalSec: Int? = null,
    capMinutes: Int? = null
): String = when (type) {
    BlockType.STRENGTH -> "Strength"
    BlockType.AMRAP -> {
        val mins = durationMinutes ?: 12
        "AMRAP ${mins}min"
    }
    BlockType.RFT -> {
        val r = rounds ?: 5
        val cap = capMinutes
        if (cap != null && cap > 0) "$r RFT / ${cap}min cap" else "$r RFT"
    }
    BlockType.EMOM -> {
        val mins = durationMinutes ?: 10
        val interval = emomIntervalSec ?: 60
        val prefix = when {
            interval <= 60 -> "EMOM"
            interval % 60 == 0 -> "E${interval / 60}MOM"
            else -> "E${interval}sMOM"
        }
        "$prefix ${mins}min"
    }
    BlockType.TABATA -> {
        val r = rounds ?: 8
        "Tabata ${r}rds"
    }
}
