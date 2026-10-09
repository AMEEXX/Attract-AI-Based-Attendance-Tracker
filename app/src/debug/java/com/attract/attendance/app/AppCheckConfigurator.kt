package com.attract.attendance.app

import android.content.Context
import com.attract.attendance.util.AppLog
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object AppCheckConfigurator {
    fun initialize(context: Context) {
        try {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            AppLog.i("AppCheck", "Initialized Debug App Check provider")
        } catch (t: Throwable) {
            AppLog.w("AppCheck", "Debug App Check init failed: ${t.message}", t)
        }
    }
}
