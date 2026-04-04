package com.powerme.app.ui.settings

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SQLSafetyValidator.
 *
 * Tests SQL Console security:
 * - SELECT-only enforcement
 * - Dangerous keyword blocking
 * - Auto-LIMIT application
 * - SQL injection prevention
 *
 * Success Criteria: All 15 tests must pass before Phase 1.3 is complete.
 */
class SQLSafetyValidatorTest {

    /**
     * Test Case 1: Valid Simple SELECT
     */
    @Test
    fun testValidSimpleSelect() {
        val result = SQLSafetyValidator.validateQuery("SELECT * FROM workouts")

        assertTrue("Should be valid", result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("Should auto-append LIMIT 100", "SELECT * FROM workouts LIMIT 100", valid.sanitizedQuery)
        assertTrue("LIMIT should be added", valid.wasLimitAdded)
        assertEquals("Applied limit should be 100", 100, valid.appliedLimit)
    }

    /**
     * Test Case 2: Valid SELECT with Existing LIMIT
     */
    @Test
    fun testValidSelectWithLimit() {
        val result = SQLSafetyValidator.validateQuery("SELECT * FROM workouts LIMIT 50")

        assertTrue("Should be valid", result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("Should preserve existing LIMIT", "SELECT * FROM workouts LIMIT 50", valid.sanitizedQuery)
        assertFalse("LIMIT should not be added", valid.wasLimitAdded)
        assertEquals("Applied limit should be 50", 50, valid.appliedLimit)
    }

    /**
     * Test Case 3: Valid SELECT with WHERE Clause
     */
    @Test
    fun testValidSelectWithWhere() {
        val query = "SELECT * FROM exercises WHERE muscleGroup = 'Chest'"
        val result = SQLSafetyValidator.validateQuery(query)

        assertTrue("Should be valid", result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertTrue("Should contain original WHERE clause", valid.sanitizedQuery.contains("WHERE muscleGroup = 'Chest'"))
        assertTrue("Should append LIMIT", valid.sanitizedQuery.endsWith("LIMIT 100"))
    }

    /**
     * Test Case 4: Valid SELECT with JOIN
     */
    @Test
    fun testValidSelectWithJoin() {
        val query = "SELECT w.*, e.name FROM workouts w JOIN workout_sets ws ON w.id = ws.workoutId"
        val result = SQLSafetyValidator.validateQuery(query)

        assertTrue("Should be valid", result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertTrue("Should preserve JOIN", valid.sanitizedQuery.contains("JOIN workout_sets"))
    }

    /**
     * Test Case 5: Valid SELECT with Subquery
     */
    @Test
    fun testValidSelectWithSubquery() {
        val query = "SELECT * FROM workouts WHERE id IN (SELECT workoutId FROM workout_sets WHERE weight > 100)"
        val result = SQLSafetyValidator.validateQuery(query)

        assertTrue("Should be valid", result is ValidationResult.Valid)
    }

    /**
     * Test Case 6: Block DROP TABLE
     */
    @Test
    fun testBlockDropTable() {
        val result = SQLSafetyValidator.validateQuery("DROP TABLE workouts")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue("Should mention SELECT-only", invalid.reason.contains("Only SELECT queries"))
    }

    /**
     * Test Case 7: Block DELETE
     */
    @Test
    fun testBlockDelete() {
        val result = SQLSafetyValidator.validateQuery("DELETE FROM workouts WHERE id = 1")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue("Should mention SELECT-only", invalid.reason.contains("Only SELECT queries"))
    }

    /**
     * Test Case 8: Block UPDATE
     */
    @Test
    fun testBlockUpdate() {
        val result = SQLSafetyValidator.validateQuery("UPDATE workouts SET totalVolume = 0")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
    }

    /**
     * Test Case 9: Block INSERT
     */
    @Test
    fun testBlockInsert() {
        val result = SQLSafetyValidator.validateQuery("INSERT INTO workouts (id) VALUES (999)")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
    }

    /**
     * Test Case 10: Block ALTER TABLE
     */
    @Test
    fun testBlockAlter() {
        val result = SQLSafetyValidator.validateQuery("ALTER TABLE workouts ADD COLUMN newCol TEXT")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
    }

    /**
     * Test Case 11: Block CREATE TABLE
     */
    @Test
    fun testBlockCreate() {
        val result = SQLSafetyValidator.validateQuery("CREATE TABLE temp (id INT)")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
    }

    /**
     * Test Case 12: Block PRAGMA
     */
    @Test
    fun testBlockPragma() {
        val result = SQLSafetyValidator.validateQuery("PRAGMA table_info(workouts)")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
    }

    /**
     * Test Case 13: Block SQL Injection with Comments
     */
    @Test
    fun testBlockSQLInjectionComments() {
        val result = SQLSafetyValidator.validateQuery("SELECT * FROM workouts WHERE id = 1; -- DROP TABLE workouts")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue("Should block SQL comments", invalid.reason.contains("comments") || invalid.reason.contains("statements"))
    }

    /**
     * Test Case 14: Block Multiple Statements
     */
    @Test
    fun testBlockMultipleStatements() {
        val result = SQLSafetyValidator.validateQuery("SELECT * FROM workouts; DROP TABLE exercises;")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue("Should block multiple statements", invalid.reason.contains("Multiple SQL statements"))
    }

    /**
     * Test Case 15: Block Excessive LIMIT
     */
    @Test
    fun testBlockExcessiveLimit() {
        val result = SQLSafetyValidator.validateQuery("SELECT * FROM workouts LIMIT 10000")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue("Should block excessive LIMIT", invalid.reason.contains("cannot exceed"))
    }

    /**
     * Test Case 16: Empty Query
     */
    @Test
    fun testEmptyQuery() {
        val result = SQLSafetyValidator.validateQuery("")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertEquals("Should have empty query message", "Query cannot be empty", invalid.reason)
    }

    /**
     * Test Case 17: Whitespace Query
     */
    @Test
    fun testWhitespaceQuery() {
        val result = SQLSafetyValidator.validateQuery("   \n\t  ")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
    }

    /**
     * Test Case 18: Case Insensitive SELECT
     */
    @Test
    fun testCaseInsensitiveSelect() {
        val queries = listOf(
            "select * from workouts",
            "SeLeCt * FrOm workouts",
            "  SELECT  *  FROM  workouts  "
        )

        queries.forEach { query ->
            val result = SQLSafetyValidator.validateQuery(query)
            assertTrue("$query should be valid", result is ValidationResult.Valid)
        }
    }

    /**
     * Test Case 19: Block Dangerous Keywords in Subquery
     */
    @Test
    fun testBlockDangerousKeywordsInSubquery() {
        val query = "SELECT * FROM workouts WHERE id IN (DELETE FROM workout_sets)"
        val result = SQLSafetyValidator.validateQuery(query)

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue("Should detect dangerous keyword in subquery",
            invalid.reason.contains("DELETE") || invalid.reason.contains("forbidden"))
    }

    /**
     * Test Case 20: Safe Query Examples are Valid
     */
    @Test
    fun testSafeQueryExamplesAreValid() {
        val examples = SQLSafetyValidator.getSafeQueryExamples()

        assertTrue("Should have examples", examples.isNotEmpty())

        examples.forEach { example ->
            val result = SQLSafetyValidator.validateQuery(example)
            assertTrue("Example '$example' should be valid", result is ValidationResult.Valid)
        }
    }

    /**
     * Test Case 21: Forbidden Operations List
     */
    @Test
    fun testForbiddenOperationsList() {
        val forbidden = SQLSafetyValidator.getForbiddenOperations()

        assertTrue("Should have forbidden operations", forbidden.isNotEmpty())
        assertTrue("Should include DROP", forbidden.any { it.keyword == "DROP" })
        assertTrue("Should include DELETE", forbidden.any { it.keyword == "DELETE" })
        assertTrue("Should include UPDATE", forbidden.any { it.keyword == "UPDATE" })

        // Verify all forbidden examples are actually blocked
        forbidden.forEach { operation ->
            val result = SQLSafetyValidator.validateQuery(operation.example)
            assertTrue("Example for ${operation.keyword} should be invalid", result is ValidationResult.Invalid)
        }
    }

    /**
     * Test Case 22: SELECT with Complex WHERE
     */
    @Test
    fun testSelectWithComplexWhere() {
        val query = """
            SELECT w.*, COUNT(ws.id) as set_count
            FROM workouts w
            LEFT JOIN workout_sets ws ON w.id = ws.workoutId
            WHERE w.timestamp >= strftime('%s', 'now', '-30 days') * 1000
            GROUP BY w.id
            ORDER BY w.timestamp DESC
        """.trimIndent()

        val result = SQLSafetyValidator.validateQuery(query)

        assertTrue("Complex query should be valid", result is ValidationResult.Valid)
    }

    /**
     * Test Case 23: Block REPLACE (masked INSERT)
     */
    @Test
    fun testBlockReplace() {
        val result = SQLSafetyValidator.validateQuery("SELECT * FROM workouts; REPLACE INTO workouts VALUES (1)")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
    }

    /**
     * Test Case 24: LIMIT at Max Boundary
     */
    @Test
    fun testLimitAtMaxBoundary() {
        val result = SQLSafetyValidator.validateQuery("SELECT * FROM workouts LIMIT 1000")

        assertTrue("Should be valid at max boundary", result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("Limit should be 1000", 1000, valid.appliedLimit)
    }

    /**
     * Test Case 25: LIMIT Just Above Max
     */
    @Test
    fun testLimitJustAboveMax() {
        val result = SQLSafetyValidator.validateQuery("SELECT * FROM workouts LIMIT 1001")

        assertTrue("Should be invalid", result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue("Should mention max limit", invalid.reason.contains("1000"))
    }
}
