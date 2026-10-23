package dev.sync.wear

import android.app.Application
import android.util.Log

class WearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "wear-alarm-sync watch app started")
    }
    companion object {
        const val TAG = "AlarmSyncWear"
    }
}
