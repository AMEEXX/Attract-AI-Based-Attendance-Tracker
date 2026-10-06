package com.attract.attendance.data.drive

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

class DriveAuthManager(private val context: Context) {

    companion object {
        const val OAUTH_CLIENT_ID = "219413758032-oge7dc4f5e16rdjooop56jge54cu8u13.apps.googleusercontent.com"
        const val PROJECT_ID = "attract-510814"
    }

    private val prefs = DriveBackupPreferences(context)
    private val authClient = Identity.getAuthorizationClient(context)

    fun getStoredAccountEmail(): String? = prefs.accountEmail

    fun requestAuthorization(
        onLaunchResolution: (IntentSenderRequest) -> Unit,
        onDirectSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope("email"),
                    Scope("profile"),
                    Scope(DriveScopes.DRIVE_APPDATA)
                )
            )
            .build()

        authClient.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        onLaunchResolution(IntentSenderRequest.Builder(pendingIntent).build())
                    } else {
                        onError("Authorization pending intent was null")
                    }
                } else {
                    val email = extractEmail(result)
                    if (!email.isNullOrBlank()) {
                        prefs.accountEmail = email
                        prefs.lastSyncError = null
                        onDirectSuccess(email)
                    } else {
                        onError("Authorization granted, but Google Account email was empty")
                    }
                }
            }
            .addOnFailureListener { e ->
                val errorMsg = mapError(e)
                prefs.lastSyncError = errorMsg
                onError(errorMsg)
            }
    }

    fun handleAuthorizationResult(resultCode: Int, data: Intent?): Result<String> {
        if (resultCode != Activity.RESULT_OK) {
            return Result.failure(Exception("Authorization cancelled"))
        }
        if (data == null) {
            return Result.failure(Exception("Authorization result was empty — retry"))
        }
        return runCatching {
            val authResult = authClient.getAuthorizationResultFromIntent(data)
            val email = extractEmail(authResult)
            if (!email.isNullOrBlank()) {
                prefs.accountEmail = email
                prefs.lastSyncError = null
                email
            } else {
                error("Google Account email was not returned in authorization result")
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(mapError(it))) }
        )
    }

    private fun extractEmail(result: AuthorizationResult): String? {
        val account = result.toGoogleSignInAccount()
        return account?.email?.takeIf { it.isNotBlank() }
            ?: account?.account?.name?.takeIf { it.isNotBlank() }
            ?: prefs.accountEmail?.takeIf { it.isNotBlank() }
            ?: GoogleSignIn.getLastSignedInAccount(context.applicationContext)?.email?.takeIf { it.isNotBlank() }
            ?: GoogleSignIn.getLastSignedInAccount(context.applicationContext)?.account?.name?.takeIf { it.isNotBlank() }
            ?: runCatching {
                android.accounts.AccountManager.get(context.applicationContext)
                    .getAccountsByType("com.google")
                    .firstOrNull()?.name
            }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun signOut(onComplete: () -> Unit) {
        prefs.accountEmail = null
        prefs.lastSyncError = null
        onComplete()
    }

    fun mapError(throwable: Throwable): String {
        if (throwable is ApiException) {
            return when (throwable.statusCode) {
                10 -> "Google Cloud configuration required (Error 10: OAuth client ID or SHA-1 fingerprint mismatch in Google Cloud Console)"
                7 -> "Network error (Code 7: Check internet connection)"
                12500, 12501 -> "Google Sign-In cancelled"
                12502 -> "Sign-in already in progress"
                8 -> "Google Play Services internal error (Code 8). Fixes: 1) Update Google Play Services in the Play Store, 2) Settings → Apps → Google Play Services → Storage → Clear cache, 3) Make sure Google Drive API is enabled in the Google Cloud project, 4) Try again."
                16 -> "Sign-in cancelled"
                else -> "Google Sign-In error (${throwable.statusCode}: ${throwable.message ?: "Failed"})"
            }
        }
        return throwable.message ?: "Google Drive connection failed"
    }
}
