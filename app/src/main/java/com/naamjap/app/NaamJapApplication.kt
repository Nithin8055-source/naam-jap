package com.naamjap.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.naamjap.app.notifications.NotificationCoordinator
import javax.inject.Inject

@HiltAndroidApp
class NaamJapApplication : Application() {
    @Inject lateinit var notificationCoordinator: NotificationCoordinator

    override fun onCreate() {
        super.onCreate()
        notificationCoordinator.start()
    }
}
