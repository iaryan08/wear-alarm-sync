package dev.sync.phone

import android.app.Application
import android.util.Log

/**
 * Application class for the phone module.
 * Minimal — no third-party SDKs to initialise.
 */
class PhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "wear-alarm-sync phone app started")
    }

    companion object {
        const val TAG = "AlarmSync"
    }
}
