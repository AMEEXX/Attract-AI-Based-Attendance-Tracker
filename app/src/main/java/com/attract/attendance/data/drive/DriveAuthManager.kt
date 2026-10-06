package com.attract.attendance.data.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

class DriveAuthManager(private val context: Context) {

    private val signInOptions: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()

    private val client: GoogleSignInClient = GoogleSignIn.getClient(context.applicationContext, signInOptions)

    fun getSignInIntent(): Intent = client.signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context.applicationContext)
        return if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))) {
            account
        } else {
            null
        }
    }

    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return runCatching {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
                ?: error("Google Sign-In returned empty account")
            if (!GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))) {
                error("Google Drive AppData permission was not granted.")
            }
            account
        }
    }

    fun signOut(onComplete: () -> Unit) {
        client.signOut().addOnCompleteListener { onComplete() }
    }
}
