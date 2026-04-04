package com.powerme.app.data.database

import kotlinx.serialization.Serializable

@Serializable
enum class ExerciseType {
    STRENGTH,
    CARDIO,
    TIMED,
    PLYOMETRIC,
    STRETCH
}
