package com.omerhedvat.powerme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val lastPerformed: Long? = null,
    val isCustom: Boolean = false
)
