package com.nowit.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nowit.app.data.ReminderRepository
import com.nowit.app.data.ReminderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id < 0) return
        val repo = ReminderRepository(context.applicationContext)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> {
                        val r = repo.get(id)
                        if (r != null && r.status == ReminderStatus.PENDING) {
                            repo.markFired(id)
                            Notifications.show(context, r)
                        }
                    }
                    ACTION_COMPLETE -> repo.complete(id, origin = "notification")
                    ACTION_SNOOZE -> repo.snooze(id, Notifications.SNOOZE_MS, origin = "notification")
                    ACTION_DISMISS -> repo.dismiss(id, origin = "notification")
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val ACTION_FIRE = "com.nowit.app.FIRE"
        const val ACTION_COMPLETE = "com.nowit.app.COMPLETE"
        const val ACTION_SNOOZE = "com.nowit.app.SNOOZE"
        const val ACTION_DISMISS = "com.nowit.app.DISMISS"
    }
}
