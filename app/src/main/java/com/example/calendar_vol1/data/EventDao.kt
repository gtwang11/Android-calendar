package com.example.calendar_vol1.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEvent): Long


    @Update
    suspend fun updateEvent(event: CalendarEvent)

    // 删除日程
    @Delete
    suspend fun deleteEvent(event: CalendarEvent)


    @Query("SELECT * FROM events WHERE startTime >= :startRange AND startTime <= :endRange ORDER BY startTime ASC")
    fun getEventsInRange(startRange: Long, endRange: Long): Flow<List<CalendarEvent>>


    @Query("SELECT * FROM events")
    suspend fun getAllEventsSync(): List<CalendarEvent>

    @Query("SELECT * FROM events")
    suspend fun getAllEvents(): List<CalendarEvent>
}