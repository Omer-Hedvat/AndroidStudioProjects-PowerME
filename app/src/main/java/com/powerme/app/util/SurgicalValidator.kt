package com.powerme.app.util

/**
 * PowerME v3.0 — Data Integrity Validator (ProjectMap.md §3)
 *
 * All real-time numeric input (Weight, Reps, Height) must pass through
 * this validator. No inline try-catch blocks in ViewModels or Composables.
 */
object SurgicalValidator {

    /** Typed result returned by every parse call. */
    sealed class ValidationResult {
        /** Input parsed successfully. [value] is the canonical Double. */
        data class Valid(val value: Double) : ValidationResult()
        /** Input is present but unparseable (e.g. "abc", "--"). */
        object Invalid : ValidationResult()
        /** Input is blank or null — field is empty. */
        object Empty : ValidationResult()
    }

    /**
     * Parse a decimal weight/height string.
     * Accepts both '.' and ',' as decimal separators (locale-aware).
     *
     * Examples:
     *   "80.5"  → Valid(80.5)
     *   "80,5"  → Valid(80.5)
     *   "80"    → Valid(80.0)
     *   ""      → Empty
     *   "abc"   → Invalid
     *   "-"     → Invalid
     */
    fun parseDecimal(raw: String): ValidationResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ValidationResult.Empty
        val normalised = trimmed.replace(',', '.')
        // Guard against bare separator (e.g. ".", ",")
        if (normalised == ".") return ValidationResult.Invalid
        return try {
            val value = normalised.toDouble()
            when {
                value.isNaN() || value.isInfinite() -> ValidationResult.Invalid
                else -> ValidationResult.Valid(value)
            }
        } catch (e: NumberFormatException) {
            ValidationResult.Invalid
        }
    }

    /**
     * Parse an integer reps field.
     * Rejects decimals — reps must be whole numbers.
     *
     * Examples:
     *   "10"  → Valid(10.0)
     *   "10.5"→ Invalid
     *   ""    → Empty
     */
    fun parseReps(raw: String): ValidationResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ValidationResult.Empty
        return try {
            val value = trimmed.toInt()
            ValidationResult.Valid(value.toDouble())
        } catch (e: NumberFormatException) {
            ValidationResult.Invalid
        }
    }

    /**
     * Returns true if [notes] looks like a leaked profile metric (e.g. "180cm", "72 kg").
     * Used for runtime cleanup; the SQL equivalent is [MIGRATION_SQL].
     */
    fun isLeakedMetric(notes: String?): Boolean {
        if (notes.isNullOrBlank()) return false
        val trimmed = notes.trim()
        if (trimmed.length > 20) return false
        val lower = trimmed.lowercase()
        return lower.endsWith("cm") || lower.endsWith("kg") ||
               lower.endsWith("lbs") || lower.endsWith("lb")
    }

    /**
     * Compile-time constant SQL for Room Migration 17→18 and @Query.
     *
     * SURGICAL: only clears setupNotes that are ≤ 20 chars AND end with a unit suffix.
     * Protects legitimate cues like "Keep chest up, brace core, drive through heels" (> 20 chars).
     */
    const val MIGRATION_SQL: String =
        "UPDATE exercises SET setupNotes = NULL " +
        "WHERE setupNotes IS NOT NULL " +
        "AND LENGTH(setupNotes) <= 20 " +
        "AND (" +
            "setupNotes LIKE '%cm' OR setupNotes LIKE '% cm' " +
            "OR setupNotes LIKE '%kg' OR setupNotes LIKE '% kg' " +
            "OR setupNotes LIKE '%lbs' OR setupNotes LIKE '% lbs' " +
            "OR setupNotes LIKE '%lb' OR setupNotes LIKE '% lb'" +
        ")"

    /**
     * Targeted SQL for Migration 18→19.
     * Strips the literal "181.5 cm:" prefix that bypassed the v17→18 cleanse
     * (it's longer than 20 chars when combined with coaching cues).
     * NULLIF ensures empty remainder becomes NULL.
     */
    const val MIGRATION_SQL_V19: String =
        "UPDATE exercises SET setupNotes = NULLIF(TRIM(REPLACE(setupNotes, '181.5 cm:', '')), '') " +
        "WHERE setupNotes LIKE '%181.5 cm:%'"
}
