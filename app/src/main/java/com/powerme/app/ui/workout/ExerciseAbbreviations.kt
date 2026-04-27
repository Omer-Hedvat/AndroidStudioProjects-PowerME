package com.powerme.app.ui.workout

/**
 * Hardcoded map of canonical exercise names → common CrossFit / functional training abbreviations.
 * Used to display a short label alongside exercise names in functional block rows and runner overlays.
 */
object ExerciseAbbreviations {

    /** Returns the abbreviation for [exerciseName], or null if none is defined. */
    fun get(exerciseName: String): String? = ABBREVS[exerciseName.lowercase().trim()]

    private val ABBREVS: Map<String, String> = mapOf(
        // Kettlebell
        "kettlebell swing"               to "KBS",
        "russian kettlebell swing"       to "KBS",
        "turkish get-up"                 to "TGU",
        "kettlebell snatch"              to "KSN",
        "kettlebell clean"               to "KC",
        "kettlebell press"               to "KP",
        "kettlebell thruster"            to "KT",
        "goblet squat"                   to "GS",
        // Barbell — Olympic lifts
        "power clean"                    to "PC",
        "hang power clean"               to "HPC",
        "squat clean"                    to "SC",
        "power snatch"                   to "PS",
        "hang power snatch"              to "HPS",
        "clean and jerk"                 to "C&J",
        "push jerk"                      to "PJ",
        "split jerk"                     to "SJ",
        "thruster"                       to "T",
        // Barbell — strength
        "deadlift"                       to "DL",
        "romanian deadlift"              to "RDL",
        "sumo deadlift high pull"        to "SDHP",
        "overhead press"                 to "OHP",
        "push press"                     to "PP",
        "back squat"                     to "BS",
        "front squat"                    to "FS",
        "overhead squat"                 to "OHS",
        "good morning"                   to "GM",
        // Gymnastics / bodyweight
        "pull-up"                        to "PU",
        "chest-to-bar pull-up"           to "C2B",
        "toes to bar"                    to "T2B",
        "knees to elbows"                to "K2E",
        "handstand push-up"              to "HSPU",
        "handstand walk"                 to "HSW",
        "muscle-up"                      to "MU",
        "bar muscle-up"                  to "BMU",
        "ring muscle-up"                 to "RMU",
        "ring dip"                       to "RD",
        "push-up"                        to "PU",
        "burpee box jump over"           to "BBJO",
        "box jump over"                  to "BJO",
        "box jump"                       to "BJ",
        "ghd sit-up"                     to "GHD",
        "glute ham raise"                to "GHR",
        // Cardio / erg machines
        "double under"                   to "DU",
        "single under"                   to "SU",
        "wall ball"                      to "WB",
        "assault bike"                   to "AB",
        "ski erg"                        to "Ski",
        // Dumbbell
        "dumbbell snatch"                to "DB Snatch",
        "dumbbell thruster"              to "DB T",
        "dumbbell clean and jerk"        to "DB C&J",
        // Carries / odd objects
        "farmers carry"                  to "FC",
        "farmers walk"                   to "FC",
        "sandbag carry"                  to "SB Carry",
    )
}
