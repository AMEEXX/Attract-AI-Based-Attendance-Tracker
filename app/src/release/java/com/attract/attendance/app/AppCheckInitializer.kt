package com.attract.attendance.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release variant of [AppCheckInitializer].
 * Uses [PlayIntegrityAppCheckProviderFactory] with Google Play Integrity API.
 * Keeps [DebugAppCheckProviderFactory] completely out of the release compilation and APK/bundle.
 */
object AppCheckInitializer {
    fun init(firebaseAppCheck: FirebaseAppCheck) {
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
