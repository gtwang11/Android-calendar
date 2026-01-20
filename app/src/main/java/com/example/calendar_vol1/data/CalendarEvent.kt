package com.example.calendar_vol1.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "events")
data class CalendarEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val location: String = "",
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean = false,
    val remindMinutes: Int = -1
) : Serializable