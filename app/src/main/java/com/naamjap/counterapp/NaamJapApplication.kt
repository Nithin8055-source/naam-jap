package com.naamjap.counterapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.naamjap.counterapp.notifications.NotificationCoordinator
import javax.inject.Inject

@HiltAndroidApp
class NaamJapApplication : Application() {
    @Inject lateinit var notificationCoordinator: NotificationCoordinator

    override fun onCreate() {
        super.onCreate()
        notificationCoordinator.start()
    }
}
