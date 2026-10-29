package com.nowit.app

import android.app.Application
import com.nowit.app.notify.Notifications

class NowItApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
    }
}
