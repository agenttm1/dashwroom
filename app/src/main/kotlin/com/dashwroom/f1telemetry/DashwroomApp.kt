package com.dashwroom.f1telemetry

import android.app.Application
import com.dashwroom.f1telemetry.data.history.LapArchive
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DashwroomApp : Application() {
    @Inject lateinit var lapArchive: LapArchive

    override fun onCreate() {
        super.onCreate()
        // Every completed player lap is archived for Lap Analysis, whatever screen is showing.
        lapArchive.start()
    }
}
