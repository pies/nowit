package com.nowit.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReminderStatus { PENDING, COMPLETED, DISMISSED }

/** How the source app of a screenshot was worked out. */
enum class SourceMethod { FILE_NAME, USAGE_STATS, MANUAL, UNKNOWN }

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** When the notification should fire (epoch millis). */
    val dueAt: Long,
    val address: String? = null,
    val phone: String? = null,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    /** Set when the reminder is completed or dismissed. */
    val closedAt: Long? = null,
    val snoozeCount: Int = 0,

    // Where the reminder came from.
    val sourceAppName: String? = null,
    val sourcePackage: String? = null,
    val sourceMethod: SourceMethod = SourceMethod.MANUAL,
    val screenshotPath: String? = null,
    val screenshotFileName: String? = null,
    val screenshotTakenAt: Long? = null,
    val ocrText: String? = null,
    /** The due time the extractor suggested, so later we can learn how often it gets edited. */
    val suggestedDueAt: Long? = null,
)

enum class EventType { CREATED, EDITED, FIRED, COMPLETED, SNOOZED, DISMISSED, DELETED }

/**
 * One row per thing that happens to a reminder. This is the raw data for habit
 * learning later, so it stores context (time of day, day of week, delay versus
 * the due time) rather than relying on joins that may change. There is no
 * foreign key, so the log outlives deleted reminders.
 */
@Entity(
    tableName = "reminder_events",
    indices = [Index("reminderId")],
)
data class ReminderEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long?,
    val type: EventType,
    val at: Long = System.currentTimeMillis(),
    /** The reminder's due time when this happened. */
    val dueAt: Long? = null,
    /** at - dueAt: how late (positive) or early (negative) the action was. */
    val offsetFromDueMs: Long? = null,
    val hourOfDay: Int,
    /** 1 = Monday … 7 = Sunday. */
    val dayOfWeek: Int,
    val sourcePackage: String? = null,
    /** For SNOOZED: how long the snooze was. */
    val snoozeMs: Long? = null,
    /** Where the action came from, e.g. "notification" or "app". */
    val origin: String? = null,
)
