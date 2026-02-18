package com.omerhedvat.powerme.data.database

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromSetType(value: SetType): String {
        return value.name
    }

    @TypeConverter
    fun toSetType(value: String): SetType {
        return SetType.valueOf(value)
    }

    @TypeConverter
    fun fromTargetJoint(value: TargetJoint): String {
        return value.name
    }

    @TypeConverter
    fun toTargetJoint(value: String): TargetJoint {
        return TargetJoint.valueOf(value)
    }

    @TypeConverter
    fun fromExerciseType(value: ExerciseType): String {
        return value.name
    }

    @TypeConverter
    fun toExerciseType(value: String): ExerciseType {
        return ExerciseType.valueOf(value)
    }

    @TypeConverter
    fun fromBarType(value: BarType): String {
        return value.name
    }

    @TypeConverter
    fun toBarType(value: String): BarType {
        return BarType.valueOf(value)
    }

    @TypeConverter
    fun fromLocalDate(value: LocalDate): String {
        return value.toString()
    }

    @TypeConverter
    fun toLocalDate(value: String): LocalDate {
        return LocalDate.parse(value)
    }
}
