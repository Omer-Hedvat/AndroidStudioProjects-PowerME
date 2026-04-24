package com.powerme.app.data

/**
 * User preference that gates the "Add" UX in the Template Builder.
 * Default: [HYBRID].
 *
 * PURE_GYM        — unchanged legacy flow (straight to exercise library)
 * PURE_FUNCTIONAL — opens the Functional Block Wizard directly
 * HYBRID          — shows a chooser sheet ("Add Strength Exercise" / "Add Functional Block")
 */
enum class WorkoutStyle {
    PURE_GYM,
    PURE_FUNCTIONAL,
    HYBRID
}
