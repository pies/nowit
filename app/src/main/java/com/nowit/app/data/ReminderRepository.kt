package com.nowit.app.data

import android.content.Context
import com.nowit.app.notify.ReminderScheduler
import java.time.Instant
import java.time.ZoneId

/**
 * The single place that changes reminders. Every change is mirrored to the
 * alarm schedule and written to the event log.
 */
class ReminderRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val reminders = db.reminders()
    private val events = db.events()

    fun observeReminders() = reminders.observeAll()
    fun observeEvents() = events.observeRecent()

    suspend fun get(id: Long) = reminders.get(id)

    suspend fun save(reminder: Reminder): Long {
        return if (reminder.id == 0L) {
            val id = reminders.insert(reminder)
            val saved = reminder.copy(id = id)
            log(saved, EventType.CREATED, origin = "app")
            ReminderScheduler.schedule(context, saved)
            id
        } else {
            reminders.update(reminder)
            log(reminder, EventType.EDITED, origin = "app")
            if (reminder.status == ReminderStatus.PENDING) {
                ReminderScheduler.schedule(context, reminder)
            } else {
                ReminderScheduler.cancel(context, reminder.id)
            }
            reminder.id
        }
    }

    suspend fun markFired(id: Long) {
        val r = reminders.get(id) ?: return
        log(r, EventType.FIRED, origin = "alarm")
    }

    suspend fun complete(id: Long, origin: String) = close(id, ReminderStatus.COMPLETED, EventType.COMPLETED, origin)

    suspend fun dismiss(id: Long, origin: String) = close(id, ReminderStatus.DISMISSED, EventType.DISMISSED, origin)

    private suspend fun close(id: Long, status: ReminderStatus, type: EventType, origin: String) {
        val r = reminders.get(id) ?: return
        if (r.status != ReminderStatus.PENDING) return
        val now = System.currentTimeMillis()
        reminders.update(r.copy(status = status, closedAt = now))
        log(r, type, origin = origin, at = now)
        ReminderScheduler.cancel(context, id)
        ReminderScheduler.cancelNotification(context, id)
    }

    suspend fun snooze(id: Long, snoozeMs: Long, origin: String) {
        val r = reminders.get(id) ?: return
        val now = System.currentTimeMillis()
        val updated = r.copy(
            dueAt = now + snoozeMs,
            snoozeCount = r.snoozeCount + 1,
            status = ReminderStatus.PENDING,
            closedAt = null,
        )
        reminders.update(updated)
        // Log against the original due time so the offset says how late the snooze was.
        log(r, EventType.SNOOZED, origin = origin, at = now, snoozeMs = snoozeMs)
        ReminderScheduler.cancelNotification(context, id)
        ReminderScheduler.schedule(context, updated)
    }

    suspend fun delete(id: Long) {
        val r = reminders.get(id) ?: return
        log(r, EventType.DELETED, origin = "app")
        ReminderScheduler.cancel(context, id)
        ReminderScheduler.cancelNotification(context, id)
        reminders.delete(r)
    }

    /** Re-arms alarms that haven't fired yet; past-due ones stay visible in the app. */
    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        reminders.pending().filter { it.dueAt > now }.forEach { ReminderScheduler.schedule(context, it) }
    }

    private suspend fun log(
        r: Reminder,
        type: EventType,
        origin: String,
        at: Long = System.currentTimeMillis(),
        snoozeMs: Long? = null,
    ) {
        val local = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        events.insert(
            ReminderEvent(
                reminderId = r.id,
                type = type,
                at = at,
                dueAt = r.dueAt,
                offsetFromDueMs = at - r.dueAt,
                hourOfDay = local.hour,
                dayOfWeek = local.dayOfWeek.value,
                sourcePackage = r.sourcePackage,
                snoozeMs = snoozeMs,
                origin = origin,
            ),
        )
    }
}
