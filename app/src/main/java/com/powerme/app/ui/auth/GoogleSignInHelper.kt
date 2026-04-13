package com.powerme.app.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthUserCollisionException
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
 * [AccountCollisionException] when the Google email already has an email/password account,
 * or a generic [Exception] for any other failure.
 */
interface GoogleSignInHelper {
    suspend fun signIn(activityContext: Context)
}

/**
 * Thrown by [DefaultGoogleSignInHelper.signIn] when Firebase reports
 * ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL. Carries the email of the existing account
 * and the pending Google [AuthCredential] so the caller can link after re-authentication.
 */
class AccountCollisionException(
    val email: String,
    val pendingCredential: AuthCredential
) : Exception("An account already exists with email $email")

// Provided as a singleton via DatabaseModule.provideGoogleSignInHelper — not auto-injected.
class DefaultGoogleSignInHelper(appContext: Context) : GoogleSignInHelper {

    // CredentialManager is safe to create eagerly — no validation on construction.
    private val credentialManager = CredentialManager.create(appContext)

    // Both requests are lazy: Builder throws IllegalArgumentException if serverClientId is blank.
    // Deferring to first use lets signInWithGoogle() catch it and surface it as a UI error.
    private val request by lazy {
        GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false)
                    .build()
            )
            .build()
    }

    // Cached fallback for when GetGoogleIdOption finds no credential (emulators, older Play Services).
    private val fallbackRequest by lazy {
        GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .build()
            )
            .build()
    }

    override suspend fun signIn(activityContext: Context) {
        // activityContext is required by getCredential() to anchor the account-picker UI.
        // GetGoogleIdOption is tried first (fast, supports auto-select for returning users).
        // If it finds no matching credential, fall back to GetSignInWithGoogleOption which
        // shows the full account-picker bottom sheet — works on emulators and older Play Services.
        val response = try {
            credentialManager.getCredential(activityContext, request)
        } catch (e: NoCredentialException) {
            credentialManager.getCredential(activityContext, fallbackRequest)
        }
        val idToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        try {
            Firebase.auth.signInWithCredential(firebaseCredential).await()
        } catch (e: FirebaseAuthUserCollisionException) {
            val email = e.email
                ?: throw Exception("Account collision but Firebase did not provide the email")
            throw AccountCollisionException(email, firebaseCredential)
        }
    }
}
