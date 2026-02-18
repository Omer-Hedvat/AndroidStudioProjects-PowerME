package com.omerhedvat.powerme.actions

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ActionParser.
 *
 * Tests all edge cases for extracting ActionBlock JSON from Gemini responses:
 * - Plain JSON
 * - Markdown code blocks
 * - Text + JSON combinations
 * - Multiple actions
 * - Malformed JSON
 * - Invalid action types
 * - Missing required fields
 * - Extra whitespace
 * - Unicode characters
 * - No JSON present
 *
 * Success Criteria: All 10 tests must pass before proceeding to Phase 1.2.
 */
class ActionParserTest {

    private lateinit var parser: ActionParser

    @Before
    fun setup() {
        parser = ActionParser()
    }

    /**
     * Test Case 1: Plain JSON
     * Parse simple JSON object: {"action": "update_weight", "weightKg": 92.0}
     */
    @Test
    fun testPlainJSON() {
        val response = """{"action": "update_weight", "weightKg": 92.0}"""

        val actions = parser.extractActions(response)

        assertEquals("Should extract 1 action", 1, actions.size)
        assertTrue("Should be UpdateWeight", actions[0] is ActionBlock.UpdateWeight)

        val updateWeight = actions[0] as ActionBlock.UpdateWeight
        assertEquals("Weight should be 92.0", 92.0, updateWeight.weightKg, 0.001)
        assertNull("SetIndex should be null", updateWeight.setIndex)
    }

    /**
     * Test Case 2: Markdown Code Block
     * Parse JSON wrapped in ```json ... ```
     */
    @Test
    fun testMarkdownCodeBlock() {
        val response = """
            ```json
            {"action": "update_weight", "weightKg": 100.0, "setIndex": 2}
            ```
        """.trimIndent()

        val actions = parser.extractActions(response)

        assertEquals("Should extract 1 action", 1, actions.size)
        assertTrue("Should be UpdateWeight", actions[0] is ActionBlock.UpdateWeight)

        val updateWeight = actions[0] as ActionBlock.UpdateWeight
        assertEquals("Weight should be 100.0", 100.0, updateWeight.weightKg, 0.001)
        assertEquals("SetIndex should be 2", 2, updateWeight.setIndex)
    }

    /**
     * Test Case 3: Text + JSON
     * Parse "Sure! I'll update that.\n```json\n{...}\n```"
     */
    @Test
    fun testTextPlusJSON() {
        val response = """
            Sure! I'll update that for you.

            ```json
            {"action": "update_injury", "joint": "LOWER_BACK", "severity": 7, "notes": "Sharp pain during deadlifts"}
            ```

            I've recorded your lower back pain at level 7.
        """.trimIndent()

        val actions = parser.extractActions(response)

        assertEquals("Should extract 1 action", 1, actions.size)
        assertTrue("Should be UpdateInjury", actions[0] is ActionBlock.UpdateInjury)

        val updateInjury = actions[0] as ActionBlock.UpdateInjury
        assertEquals("Joint should be LOWER_BACK", "LOWER_BACK", updateInjury.joint)
        assertEquals("Severity should be 7", 7, updateInjury.severity)
        assertEquals("Notes should match", "Sharp pain during deadlifts", updateInjury.notes)
    }

    /**
     * Test Case 4: Multiple Actions
     * Parse two actions in a single response
     */
    @Test
    fun testMultipleActions() {
        val response = """
            I'll update both of those for you.

            ```json
            {"action": "update_weight", "weightKg": 95.0}
            ```

            ```json
            {"action": "switch_gym", "gymProfileName": "Home"}
            ```
        """.trimIndent()

        val actions = parser.extractActions(response)

        assertEquals("Should extract 2 actions", 2, actions.size)
        assertTrue("First should be UpdateWeight", actions[0] is ActionBlock.UpdateWeight)
        assertTrue("Second should be SwitchGym", actions[1] is ActionBlock.SwitchGym)

        val updateWeight = actions[0] as ActionBlock.UpdateWeight
        assertEquals("Weight should be 95.0", 95.0, updateWeight.weightKg, 0.001)

        val switchGym = actions[1] as ActionBlock.SwitchGym
        assertEquals("Gym should be Home", "Home", switchGym.gymProfileName)
    }

    /**
     * Test Case 5: Malformed JSON
     * Return empty list for {"action": "update_weight", "weightKg": 92.0 (missing closing brace)
     */
    @Test
    fun testMalformedJSON() {
        val response = """{"action": "update_weight", "weightKg": 92.0"""

        val actions = parser.extractActions(response)

        assertEquals("Should return empty list for malformed JSON", 0, actions.size)
    }

    /**
     * Test Case 6: Invalid Action Type
     * Return empty list for {"action": "invalid_action"}
     */
    @Test
    fun testInvalidActionType() {
        val response = """{"action": "invalid_action", "someField": "value"}"""

        val actions = parser.extractActions(response)

        assertEquals("Should return empty list for invalid action type", 0, actions.size)
    }

    /**
     * Test Case 7: Missing Required Field
     * Handle UpdateWeight without weightKg (should fail gracefully)
     */
    @Test
    fun testMissingRequiredField() {
        val response = """
            ```json
            {"action": "update_weight", "setIndex": 3}
            ```
        """.trimIndent()

        val actions = parser.extractActions(response)

        assertEquals("Should return empty list when required field missing", 0, actions.size)
    }

    /**
     * Test Case 8: Extra Whitespace
     * Handle {"action": "update_weight"} with leading/trailing whitespace
     */
    @Test
    fun testExtraWhitespace() {
        val response = """


                {"action": "update_weight", "weightKg": 88.5}


        """.trimIndent()

        val actions = parser.extractActions(response)

        assertEquals("Should extract 1 action despite whitespace", 1, actions.size)
        assertTrue("Should be UpdateWeight", actions[0] is ActionBlock.UpdateWeight)

        val updateWeight = actions[0] as ActionBlock.UpdateWeight
        assertEquals("Weight should be 88.5", 88.5, updateWeight.weightKg, 0.001)
    }

    /**
     * Test Case 9: Unicode Characters
     * Handle exercise names with accents and special characters
     */
    @Test
    fun testUnicodeCharacters() {
        val response = """
            ```json
            {"action": "update_equipment", "gymProfileName": "Café Gym", "equipment": ["Dumbbells", "Résistance Bands"]}
            ```
        """.trimIndent()

        val actions = parser.extractActions(response)

        assertEquals("Should extract 1 action", 1, actions.size)
        assertTrue("Should be UpdateEquipment", actions[0] is ActionBlock.UpdateEquipment)

        val updateEquipment = actions[0] as ActionBlock.UpdateEquipment
        assertEquals("Gym name should preserve unicode", "Café Gym", updateEquipment.gymProfileName)
        assertTrue("Equipment should contain unicode", updateEquipment.equipment.contains("Résistance Bands"))
    }

    /**
     * Test Case 10: No JSON Present
     * Return empty list for plain text with no JSON
     */
    @Test
    fun testNoJSONPresent() {
        val response = """
            I don't have enough information to update anything right now.
            Please provide the weight value you'd like to set.
        """.trimIndent()

        val actions = parser.extractActions(response)

        assertEquals("Should return empty list when no JSON present", 0, actions.size)
    }

    /**
     * Bonus Test: Complex Real-World Response
     * Simulate a typical Gemini response with mixed content
     */
    @Test
    fun testComplexRealWorldResponse() {
        val response = """
            Absolutely! I'll update your weight for the current set.

            Given your L4-L5 history and the fact that you're working at 181.5cm height,
            remember to maintain a neutral spine and avoid excessive forward lean.

            ```json
            {"action": "update_weight", "weightKg": 92.5, "setIndex": null}
            ```

            Your current set is now recorded at 92.5kg. How did that feel?
        """.trimIndent()

        val actions = parser.extractActions(response)

        assertEquals("Should extract 1 action from complex response", 1, actions.size)
        assertTrue("Should be UpdateWeight", actions[0] is ActionBlock.UpdateWeight)

        val updateWeight = actions[0] as ActionBlock.UpdateWeight
        assertEquals("Weight should be 92.5", 92.5, updateWeight.weightKg, 0.001)
        assertNull("SetIndex should be null", updateWeight.setIndex)
    }
}
