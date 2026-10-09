package com.attract.attendance.app

import android.content.Context
import com.attract.attendance.util.AppLog
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object AppCheckConfigurator {
    fun initialize(context: Context) {
        try {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            AppLog.i("AppCheck", "Initialized Play Integrity App Check provider")
        } catch (t: Throwable) {
            AppLog.w("AppCheck", "Release App Check init failed: ${t.message}", t)
        }
    }
}
