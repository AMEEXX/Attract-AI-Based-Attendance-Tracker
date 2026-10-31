package com.attract.attendance.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug variant of [AppCheckInitializer].
 * Uses [DebugAppCheckProviderFactory] to allow local debugging and testing without Play Integrity attestations.
 */
object AppCheckInitializer {
    fun init(firebaseAppCheck: FirebaseAppCheck) {
        firebaseAppCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
