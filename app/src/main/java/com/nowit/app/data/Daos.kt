package com.nowit.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, dueAt")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE status = 'PENDING'")
    suspend fun pending(): List<Reminder>

    @Insert suspend fun insert(reminder: Reminder): Long
    @Update suspend fun update(reminder: Reminder)
    @Delete suspend fun delete(reminder: Reminder)
}

@Dao
interface EventDao {
    @Insert suspend fun insert(event: ReminderEvent): Long

    @Query("SELECT * FROM reminder_events ORDER BY at DESC LIMIT 500")
    fun observeRecent(): Flow<List<ReminderEvent>>
}
