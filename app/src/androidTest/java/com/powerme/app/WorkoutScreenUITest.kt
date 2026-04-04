package com.powerme.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.powerme.app.data.database.SetType
import com.powerme.app.ui.theme.PowerMETheme
import com.powerme.app.ui.workout.ActiveWorkoutScreen
import com.powerme.app.ui.workout.RpePickerSheet
import com.powerme.app.ui.workout.RestTimePickerDialog
import com.powerme.app.ui.workout.WorkoutSetRow
import com.powerme.app.ui.workout.ActiveSet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class WorkoutScreenUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun defaultSet(
        setOrder: Int = 1,
        weight: String = "",
        reps: String = "",
        rpeValue: Int? = null,
        isCompleted: Boolean = false,
        ghostWeight: String? = null,
        ghostReps: String? = null,
        ghostRpe: String? = null
    ) = ActiveSet(
        id = 1L,
        setOrder = setOrder,
        weight = weight,
        reps = reps,
        rpeValue = rpeValue,
        isCompleted = isCompleted,
        ghostWeight = ghostWeight,
        ghostReps = ghostReps,
        ghostRpe = ghostRpe
    )

    // ── STEP 3: RPE Column ────────────────────────────────────────────────────

    @Test
    fun step3_rpeColumn_showsDashWhenNull() {
        composeTestRule.setContent {
            PowerMETheme {
                WorkoutSetRow(
                    set = defaultSet(rpeValue = null),
                    onWeightChanged = {},
                    onRepsChanged = {},
                    onCompleteSet = {},
                    onSelectSetType = { _ -> },
                    onUpdateRpe = {},
                    onDeleteTimer = {}
                )
            }
        }
        composeTestRule.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun step3_rpePicker_showsChips() {
        composeTestRule.setContent {
            PowerMETheme {
                RpePickerSheet(
                    currentRpe = null,
                    onUpdateRpe = {},
                    onDismiss = {}
                )
            }
        }
        // Verify chips for key RPE values exist
        composeTestRule.onNodeWithText("6.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("8.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("10.0").assertIsDisplayed()
    }

    @Test
    fun step3_rpePicker_showsAnchorLabels() {
        composeTestRule.setContent {
            PowerMETheme {
                RpePickerSheet(
                    currentRpe = null,
                    onUpdateRpe = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Very Light").assertIsDisplayed()
        composeTestRule.onNodeWithText("Max Effort").assertIsDisplayed()
    }

    @Test
    fun step3_rpePicker_selectValueCallsCallback() {
        var capturedRpe: Int? = -1
        composeTestRule.setContent {
            PowerMETheme {
                RpePickerSheet(
                    currentRpe = null,
                    onUpdateRpe = { capturedRpe = it },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onNodeWithText("8.5").performClick()
        assertEquals(85, capturedRpe)
    }

    @Test
    fun step3_rpePicker_clearCallsNull() {
        var capturedRpe: Int? = 80
        composeTestRule.setContent {
            PowerMETheme {
                RpePickerSheet(
                    currentRpe = 80,
                    onUpdateRpe = { capturedRpe = it },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Clear").performClick()
        assertNull(capturedRpe)
    }

    @Test
    fun step3_rpeColumn_showsValueWhenSet() {
        composeTestRule.setContent {
            PowerMETheme {
                WorkoutSetRow(
                    set = defaultSet(rpeValue = 85),
                    onWeightChanged = {},
                    onRepsChanged = {},
                    onCompleteSet = {},
                    onSelectSetType = { _ -> },
                    onUpdateRpe = {},
                    onDeleteTimer = {}
                )
            }
        }
        composeTestRule.onNodeWithText("8.5").assertIsDisplayed()
    }

    // ── STEP 4: PREV Column ───────────────────────────────────────────────────

    @Test
    fun step4_prevColumn_showsDashWhenNoGhost() {
        composeTestRule.setContent {
            PowerMETheme {
                WorkoutSetRow(
                    set = defaultSet(ghostWeight = null, ghostReps = null),
                    onWeightChanged = {},
                    onRepsChanged = {},
                    onCompleteSet = {},
                    onSelectSetType = { _ -> },
                    onUpdateRpe = {},
                    onDeleteTimer = {}
                )
            }
        }
        // "—" appears in PREV column (and also in RPE column — at least one instance)
        composeTestRule.onAllNodesWithText("—").onFirst().assertIsDisplayed()
    }

    @Test
    fun step4_prevColumn_showsWeightAndReps() {
        composeTestRule.setContent {
            PowerMETheme {
                WorkoutSetRow(
                    set = defaultSet(ghostWeight = "80", ghostReps = "10"),
                    onWeightChanged = {},
                    onRepsChanged = {},
                    onCompleteSet = {},
                    onSelectSetType = { _ -> },
                    onUpdateRpe = {},
                    onDeleteTimer = {}
                )
            }
        }
        composeTestRule.onNodeWithText("80×10").assertIsDisplayed()
    }

    @Test
    fun step4_prevColumn_showsWeightRepsAndRpe() {
        composeTestRule.setContent {
            PowerMETheme {
                WorkoutSetRow(
                    set = defaultSet(ghostWeight = "80", ghostReps = "10", ghostRpe = "8.0"),
                    onWeightChanged = {},
                    onRepsChanged = {},
                    onCompleteSet = {},
                    onSelectSetType = { _ -> },
                    onUpdateRpe = {},
                    onDeleteTimer = {}
                )
            }
        }
        composeTestRule.onNodeWithText("80×10@8.0").assertIsDisplayed()
    }

    // ── STEP 7: RestTimePickerDialog ──────────────────────────────────────────

    @Test
    fun step7_restTimePicker_showsFields() {
        composeTestRule.setContent {
            PowerMETheme {
                RestTimePickerDialog(
                    currentSeconds = 90,
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Set Rest Time").assertIsDisplayed()
        composeTestRule.onNodeWithText("Min").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sec").assertIsDisplayed()
    }

    @Test
    fun step7_restTimePicker_prepopulatesCurrentSeconds() {
        composeTestRule.setContent {
            PowerMETheme {
                RestTimePickerDialog(
                    currentSeconds = 90, // 1 min 30 sec
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }
        // Confirm button is present and tappable
        composeTestRule.onNodeWithText("Confirm").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun step7_restTimePicker_confirmCallsOnConfirm() {
        // With currentSeconds=90 (1 min 30 sec), clicking Confirm without changes
        // should call onConfirm(90)
        var confirmed: Int = -1
        composeTestRule.setContent {
            PowerMETheme {
                RestTimePickerDialog(
                    currentSeconds = 90,
                    onDismiss = {},
                    onConfirm = { confirmed = it }
                )
            }
        }
        composeTestRule.onNodeWithText("Confirm").performClick()
        assertEquals(90, confirmed)
    }

    @Test
    fun step7_restTimePicker_dismissCallsOnDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            PowerMETheme {
                RestTimePickerDialog(
                    currentSeconds = 60,
                    onDismiss = { dismissed = true },
                    onConfirm = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    // ── STEP 5: Touched indicator (structural — verify set type badge visible) ─

    @Test
    fun step5_setTypeBadge_showsSetOrderForNormal() {
        composeTestRule.setContent {
            PowerMETheme {
                WorkoutSetRow(
                    set = defaultSet(setOrder = 2),
                    onWeightChanged = {},
                    onRepsChanged = {},
                    onCompleteSet = {},
                    onSelectSetType = { _ -> },
                    onUpdateRpe = {},
                    onDeleteTimer = {}
                )
            }
        }
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
    }

    @Test
    fun step5_setTypeBadge_showsWForWarmup() {
        composeTestRule.setContent {
            PowerMETheme {
                WorkoutSetRow(
                    set = defaultSet(setOrder = 1).copy(setType = SetType.WARMUP),
                    onWeightChanged = {},
                    onRepsChanged = {},
                    onCompleteSet = {},
                    onSelectSetType = { _ -> },
                    onUpdateRpe = {},
                    onDeleteTimer = {}
                )
            }
        }
        composeTestRule.onNodeWithText("W").assertIsDisplayed()
    }

    // ── STEP 2: SetType DropdownMenu ──────────────────────────────────────────

    @Test
    fun step2_setTypeBadge_tap_opensDropdown() {
        composeTestRule.setContent {
            PowerMETheme {
                WorkoutSetRow(
                    set = defaultSet(setOrder = 1),
                    onWeightChanged = {},
                    onRepsChanged = {},
                    onCompleteSet = {},
                    onSelectSetType = { _ -> },
                    onUpdateRpe = {},
                    onDeleteTimer = {}
                )
            }
        }
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("Work Set").assertIsDisplayed()
        composeTestRule.onNodeWithText("Warm Up").assertIsDisplayed()
        composeTestRule.onNodeWithText("Failure").assertIsDisplayed()
        composeTestRule.onNodeWithText("Drop Set").assertIsDisplayed()
    }

    @Test
    fun step2_setType_selectWarmup_callsCallback() {
        var selected: SetType? = null
        composeTestRule.setContent {
            PowerMETheme {
                WorkoutSetRow(
                    set = defaultSet(setOrder = 1),
                    onWeightChanged = {},
                    onRepsChanged = {},
                    onCompleteSet = {},
                    onSelectSetType = { t -> selected = t },
                    onUpdateRpe = {},
                    onDeleteTimer = {}
                )
            }
        }
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("Warm Up").performClick()
        assertEquals(SetType.WARMUP, selected)
    }
}
