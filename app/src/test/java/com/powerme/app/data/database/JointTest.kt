package com.powerme.app.data.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JointTest {

    // ── fromJsonString ──────────────────────────────────────────────────────

    @Test
    fun fromJsonString_blank_returnsEmpty() {
        assertTrue(Joint.fromJsonString("").isEmpty())
        assertTrue(Joint.fromJsonString("   ").isEmpty())
    }

    @Test
    fun fromJsonString_validArray_returnsJoints() {
        val result = Joint.fromJsonString("[\"KNEE\",\"HIP\"]")
        assertEquals(listOf(Joint.KNEE, Joint.HIP), result)
    }

    @Test
    fun fromJsonString_singleElement_returnsSingleJoint() {
        val result = Joint.fromJsonString("[\"ANKLE\"]")
        assertEquals(listOf(Joint.ANKLE), result)
    }

    @Test
    fun fromJsonString_unknownName_isSkipped() {
        val result = Joint.fromJsonString("[\"KNEE\",\"UNKNOWN_JOINT\",\"HIP\"]")
        assertEquals(listOf(Joint.KNEE, Joint.HIP), result)
    }

    @Test
    fun fromJsonString_allTenJoints_roundTrip() {
        val all = Joint.entries
        val json = Joint.toJsonString(all)
        val parsed = Joint.fromJsonString(json)
        assertEquals(all, parsed)
    }

    // ── toJsonString ────────────────────────────────────────────────────────

    @Test
    fun toJsonString_emptyList_returnsEmptyString() {
        assertEquals("", Joint.toJsonString(emptyList()))
    }

    @Test
    fun toJsonString_singleJoint_correctFormat() {
        assertEquals("[\"SHOULDER\"]", Joint.toJsonString(listOf(Joint.SHOULDER)))
    }

    @Test
    fun toJsonString_multipleJoints_correctFormat() {
        val result = Joint.toJsonString(listOf(Joint.KNEE, Joint.HIP))
        assertEquals("[\"KNEE\",\"HIP\"]", result)
    }

    // ── displayName ─────────────────────────────────────────────────────────

    @Test
    fun allJoints_haveNonBlankDisplayName() {
        Joint.entries.forEach { joint ->
            assertTrue("${joint.name} has blank displayName", joint.displayName.isNotBlank())
        }
    }

    @Test
    fun lumbarSpine_displayName_isReadable() {
        assertEquals("Lumbar Spine", Joint.LUMBAR_SPINE.displayName)
    }

    // ── toAffectedJoints ────────────────────────────────────────────────────

    @Test
    fun toAffectedJoints_mildEntry_returnsEmpty() {
        val entry = makeEntry(bodyRegion = "knee", severity = HealthHistorySeverity.MILD.name)
        assertTrue(entry.toAffectedJoints().isEmpty())
    }

    @Test
    fun toAffectedJoints_resolvedEntry_returnsEmpty() {
        val entry = makeEntry(bodyRegion = "shoulder", severity = HealthHistorySeverity.RESOLVED.name)
        assertTrue(entry.toAffectedJoints().isEmpty())
    }

    @Test
    fun toAffectedJoints_moderateKnee_returnsKnee() {
        val entry = makeEntry(bodyRegion = "knee", severity = HealthHistorySeverity.MODERATE.name)
        assertEquals(setOf(Joint.KNEE), entry.toAffectedJoints())
    }

    @Test
    fun toAffectedJoints_severeShoulder_returnsShoulder() {
        val entry = makeEntry(bodyRegion = "shoulder", severity = HealthHistorySeverity.SEVERE.name)
        assertEquals(setOf(Joint.SHOULDER), entry.toAffectedJoints())
    }

    @Test
    fun toAffectedJoints_displayNameMatch_lumbarSpine() {
        val entry = makeEntry(bodyRegion = "lumbar spine", severity = HealthHistorySeverity.SEVERE.name)
        assertEquals(setOf(Joint.LUMBAR_SPINE), entry.toAffectedJoints())
    }

    @Test
    fun toAffectedJoints_nullBodyRegion_returnsEmpty() {
        val entry = makeEntry(bodyRegion = null, severity = HealthHistorySeverity.MODERATE.name)
        assertTrue(entry.toAffectedJoints().isEmpty())
    }

    @Test
    fun toAffectedJoints_blankSeverity_returnsEmpty() {
        val entry = makeEntry(bodyRegion = "knee", severity = "INVALID_SEVERITY")
        assertTrue(entry.toAffectedJoints().isEmpty())
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun makeEntry(bodyRegion: String?, severity: String) = HealthHistoryEntry(
        id = "test-id",
        userId = "user-1",
        title = "Test Injury",
        bodyRegion = bodyRegion,
        severity = severity
    )
}
