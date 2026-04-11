package com.powerme.app.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.powerme.app.BuildConfig
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Abstracts the Credential Manager → Google ID token → Firebase sign-in chain so
 * AuthViewModel stays testable without Android instrumentation.
 * Throws [androidx.credentials.exceptions.GetCredentialCancellationException] on user dismissal,
 * [androidx.credentials.exceptions.NoCredentialException] when no Google account is present,
 * or a generic [Exception] for any other failure.
 */
interface GoogleSignInHelper {
    suspend fun signIn(activityContext: Context)
}

// Provided as a singleton via DatabaseModule.provideGoogleSignInHelper — not auto-injected.
class DefaultGoogleSignInHelper(appContext: Context) : GoogleSignInHelper {

    // CredentialManager is safe to create eagerly — no validation on construction.
    private val credentialManager = CredentialManager.create(appContext)

    // Lazy so that a missing/empty GOOGLE_WEB_CLIENT_ID does NOT crash at app startup.
    // GetGoogleIdOption.Builder throws IllegalArgumentException if serverClientId is blank;
    // deferring to first use lets signInWithGoogle() catch it and surface it as a UI error.
    private val request by lazy {
        GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setAutoSelectEnabled(true)
                    .build()
            )
            .build()
    }

    override suspend fun signIn(activityContext: Context) {
        // activityContext is required by getCredential() to anchor the account-picker UI.
        val response = credentialManager.getCredential(activityContext, request)
        val idToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        Firebase.auth.signInWithCredential(firebaseCredential).await()
    }
}
