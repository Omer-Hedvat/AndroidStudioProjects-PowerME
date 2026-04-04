package com.powerme.app.ui.settings

/**
 * Validates SQL queries for the SQL Console to prevent accidental data destruction.
 *
 * Security Rules:
 * 1. Only SELECT statements allowed
 * 2. Block dangerous keywords: DROP, DELETE, UPDATE, INSERT, ALTER, CREATE, PRAGMA
 * 3. Auto-append LIMIT 100 to prevent UI freezes
 * 4. Strict read-only enforcement
 *
 * Example Usage:
 * ```kotlin
 * val result = SQLSafetyValidator.validateQuery("SELECT * FROM workouts")
 * when (result) {
 *     is ValidationResult.Valid -> executeQuery(result.sanitizedQuery)
 *     is ValidationResult.Invalid -> showError(result.reason)
 * }
 * ```
 */
object SQLSafetyValidator {

    private const val DEFAULT_LIMIT = 100
    private const val MAX_LIMIT = 1000

    /**
     * List of SQL keywords that are forbidden for safety.
     * These operations could cause data loss or corruption.
     */
    private val DANGEROUS_KEYWORDS = setOf(
        "DROP",
        "DELETE",
        "UPDATE",
        "INSERT",
        "ALTER",
        "CREATE",
        "PRAGMA",
        "ATTACH",
        "DETACH",
        "VACUUM",
        "REPLACE",
        "TRUNCATE",
        "GRANT",
        "REVOKE"
    )

    /**
     * Regex pattern to match SELECT queries.
     * Must start with SELECT (case-insensitive).
     */
    private val SELECT_PATTERN = "^\\s*SELECT\\s+".toRegex(RegexOption.IGNORE_CASE)

    /**
     * Regex pattern to detect LIMIT clause.
     */
    private val LIMIT_PATTERN = "\\bLIMIT\\s+\\d+\\s*$".toRegex(RegexOption.IGNORE_CASE)

    /**
     * Validates and sanitizes a SQL query.
     *
     * @param query The raw SQL query to validate
     * @return ValidationResult indicating if query is safe and providing sanitized version
     */
    fun validateQuery(query: String): ValidationResult {
        val trimmedQuery = query.trim()

        // Rule 0: Empty query
        if (trimmedQuery.isEmpty()) {
            return ValidationResult.Invalid("Query cannot be empty")
        }

        // Rule 1: Must start with SELECT
        if (!SELECT_PATTERN.containsMatchIn(trimmedQuery)) {
            return ValidationResult.Invalid(
                "Only SELECT queries are allowed. Forbidden: ${DANGEROUS_KEYWORDS.joinToString(", ")}"
            )
        }

        // Rule 1.5: Check for injection patterns FIRST (before keyword check)
        val injectionCheck = performInjectionChecks(trimmedQuery)
        if (injectionCheck != null) {
            return ValidationResult.Invalid(injectionCheck)
        }

        // Rule 2: Block dangerous keywords
        val upperQuery = trimmedQuery.uppercase()
        val foundDangerousKeywords = DANGEROUS_KEYWORDS.filter { keyword ->
            upperQuery.contains("\\b$keyword\\b".toRegex())
        }

        if (foundDangerousKeywords.isNotEmpty()) {
            return ValidationResult.Invalid(
                "Query contains forbidden keyword(s): ${foundDangerousKeywords.joinToString(", ")}"
            )
        }

        // Rule 3: Auto-append LIMIT if not present
        val sanitizedQuery = if (!LIMIT_PATTERN.containsMatchIn(trimmedQuery)) {
            "$trimmedQuery LIMIT $DEFAULT_LIMIT"
        } else {
            // Validate existing LIMIT doesn't exceed max
            val existingLimit = extractLimit(trimmedQuery)
            if (existingLimit > MAX_LIMIT) {
                return ValidationResult.Invalid(
                    "LIMIT cannot exceed $MAX_LIMIT (found: $existingLimit)"
                )
            }
            trimmedQuery
        }

        // Rule 4: Additional safety checks
        val additionalCheck = performAdditionalSafetyChecks(sanitizedQuery)
        if (additionalCheck != null) {
            return ValidationResult.Invalid(additionalCheck)
        }

        return ValidationResult.Valid(
            sanitizedQuery = sanitizedQuery,
            wasLimitAdded = !LIMIT_PATTERN.containsMatchIn(trimmedQuery),
            appliedLimit = if (LIMIT_PATTERN.containsMatchIn(trimmedQuery)) extractLimit(trimmedQuery) else DEFAULT_LIMIT
        )
    }

    /**
     * Extracts the LIMIT value from a query.
     *
     * @param query The SQL query
     * @return The LIMIT value, or DEFAULT_LIMIT if not found
     */
    private fun extractLimit(query: String): Int {
        val match = LIMIT_PATTERN.find(query)
        return match?.value?.replace("LIMIT", "", ignoreCase = true)?.trim()?.toIntOrNull() ?: DEFAULT_LIMIT
    }

    /**
     * Checks for SQL injection patterns (comments and multiple statements).
     * This runs BEFORE dangerous keyword check to catch injection attempts.
     *
     * @param query The query to check
     * @return Error message if injection detected, null otherwise
     */
    private fun performInjectionChecks(query: String): String? {
        // Check for SQL comments (basic)
        if (query.contains("--")) {
            return "SQL comments (--) are not allowed in queries"
        }

        // Check for multiple statements (semicolon separator)
        val semicolonCount = query.count { it == ';' }
        if (semicolonCount > 0) {
            // Allow single trailing semicolon, but nothing after it (except whitespace)
            val trimmed = query.trim()
            if (!trimmed.endsWith(";") || semicolonCount > 1) {
                return "Multiple SQL statements not allowed"
            }
        }

        return null
    }

    /**
     * Performs additional safety checks beyond keyword blocking.
     *
     * @param query The sanitized query
     * @return Error message if check fails, null if all checks pass
     */
    private fun performAdditionalSafetyChecks(query: String): String? {
        val upperQuery = query.uppercase()

        // Check for subqueries with write operations (nested danger)
        if (upperQuery.contains("(SELECT") || upperQuery.contains("( SELECT")) {
            // Allow subqueries, but check they don't contain dangerous keywords
            DANGEROUS_KEYWORDS.forEach { keyword ->
                if (upperQuery.contains("\\b$keyword\\b".toRegex())) {
                    return "Subqueries cannot contain write operations ($keyword)"
                }
            }
        }

        return null // All checks passed
    }

    /**
     * Generates user-friendly examples of safe queries.
     */
    fun getSafeQueryExamples(): List<String> {
        return listOf(
            "SELECT * FROM workouts ORDER BY timestamp DESC",
            "SELECT COUNT(*) FROM exercises WHERE muscleGroup = 'Chest'",
            "SELECT w.*, e.name FROM workouts w JOIN workout_sets ws ON w.id = ws.workoutId JOIN exercises e ON ws.exerciseId = e.id",
            "SELECT muscleGroup, COUNT(*) as count FROM exercises GROUP BY muscleGroup",
            "SELECT * FROM health_stats WHERE date >= strftime('%s', 'now', '-30 days') * 1000"
        )
    }

    /**
     * Generates list of forbidden operations with explanations.
     */
    fun getForbiddenOperations(): List<ForbiddenOperation> {
        return listOf(
            ForbiddenOperation(
                keyword = "DROP",
                example = "DROP TABLE workouts",
                reason = "Permanently deletes entire tables and all data"
            ),
            ForbiddenOperation(
                keyword = "DELETE",
                example = "DELETE FROM workouts WHERE id = 1",
                reason = "Removes data rows permanently"
            ),
            ForbiddenOperation(
                keyword = "UPDATE",
                example = "UPDATE workouts SET totalVolume = 0",
                reason = "Modifies existing data"
            ),
            ForbiddenOperation(
                keyword = "INSERT",
                example = "INSERT INTO workouts (id) VALUES (999)",
                reason = "Adds new data, risking corruption"
            ),
            ForbiddenOperation(
                keyword = "ALTER",
                example = "ALTER TABLE workouts ADD COLUMN newCol TEXT",
                reason = "Modifies table structure"
            ),
            ForbiddenOperation(
                keyword = "CREATE",
                example = "CREATE TABLE temp (id INT)",
                reason = "Creates new database objects"
            ),
            ForbiddenOperation(
                keyword = "PRAGMA",
                example = "PRAGMA journal_mode=DELETE",
                reason = "Changes database configuration"
            )
        )
    }
}

/**
 * Result of SQL query validation.
 */
sealed class ValidationResult {
    /**
     * Query is safe to execute.
     *
     * @param sanitizedQuery The query with safety modifications applied (e.g., LIMIT added)
     * @param wasLimitAdded Whether LIMIT was automatically added
     * @param appliedLimit The LIMIT value that will be applied
     */
    data class Valid(
        val sanitizedQuery: String,
        val wasLimitAdded: Boolean,
        val appliedLimit: Int
    ) : ValidationResult()

    /**
     * Query is unsafe and should not be executed.
     *
     * @param reason Human-readable explanation of why query was rejected
     */
    data class Invalid(
        val reason: String
    ) : ValidationResult()
}

/**
 * Describes a forbidden SQL operation.
 */
data class ForbiddenOperation(
    val keyword: String,
    val example: String,
    val reason: String
)
