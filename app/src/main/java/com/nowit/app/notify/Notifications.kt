package com.nowit.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nowit.app.MainActivity
import com.nowit.app.R
import com.nowit.app.data.Reminder

object Notifications {
    const val CHANNEL_ID = "reminders"
    const val SNOOZE_MS = 15 * 60 * 1000L
    private val ACTIONS = listOf(
        ReminderReceiver.ACTION_COMPLETE,
        ReminderReceiver.ACTION_SNOOZE,
        ReminderReceiver.ACTION_DISMISS,
    )

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (android.os.Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    fun show(context: Context, reminder: Reminder) {
        if (!canPost(context)) return
        val id = reminder.id
        val text = listOfNotNull(
            reminder.notes.takeIf { it.isNotBlank() },
            reminder.address,
            reminder.phone,
        ).joinToString("\n")

        val open = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_REMINDER_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(text.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            // Swiping the notification away counts as a dismiss.
            .setDeleteIntent(action(context, ReminderReceiver.ACTION_DISMISS, id))
            .addAction(0, "Done", action(context, ReminderReceiver.ACTION_COMPLETE, id))
            .addAction(0, "Snooze 15 min", action(context, ReminderReceiver.ACTION_SNOOZE, id))
            .addAction(0, "Dismiss", action(context, ReminderReceiver.ACTION_DISMISS, id))
            .build()

        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id.toInt(), notification)
    }

    private fun action(context: Context, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // Distinct request codes so the actions don't overwrite each other.
            (id * 10 + ACTIONS.indexOf(action) + 1).toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
