package com.nowit.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nowit.app.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms are cleared on reboot and app update, so put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderRepository(context.applicationContext).rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
