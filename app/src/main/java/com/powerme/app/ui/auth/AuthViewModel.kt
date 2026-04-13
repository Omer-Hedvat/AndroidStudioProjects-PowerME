package com.powerme.app.ui.auth

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException as CoroutineCancellationException
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.sync.FirestoreSyncManager
import com.powerme.app.util.UserSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSignedIn: Boolean = false,
    val needsEmailVerification: Boolean = false,
    val needsProfileSetup: Boolean = false,
    val resetEmailSent: Boolean = false,
    val pendingLinkEmail: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userSessionManager: UserSessionManager,
    private val googleSignInHelper: GoogleSignInHelper,
    private val firestoreSyncManager: FirestoreSyncManager,
    private val appSettingsDataStore: AppSettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Kept outside the StateFlow because AuthCredential does not implement equals() —
    // storing it in a data class would break StateFlow's duplicate-emission filtering.
    private var pendingLinkCredential: AuthCredential? = null

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                Firebase.auth.createUserWithEmailAndPassword(email, password).await()
                Firebase.auth.currentUser?.sendEmailVerification()?.await()
                _uiState.update { it.copy(isLoading = false, needsEmailVerification = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                Firebase.auth.signInWithEmailAndPassword(email, password).await()
                val firebaseUser = Firebase.auth.currentUser
                if (firebaseUser != null && !firebaseUser.isEmailVerified) {
                    _uiState.update { it.copy(isLoading = false, needsEmailVerification = true) }
                    return@launch
                }
                applyNewUserGate()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                googleSignInHelper.signIn(activityContext)
                applyNewUserGate()
            } catch (e: CoroutineCancellationException) {
                // Coroutine scope was cancelled (ViewModel cleared mid-flight) — rethrow so the
                // coroutine machinery can clean up normally. Do not surface this as a UI error.
                throw e
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the account picker — silent, no error shown.
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: NoCredentialException) {
                _uiState.update { it.copy(isLoading = false, error = "No Google accounts available on this device") }
            } catch (e: AccountCollisionException) {
                pendingLinkCredential = e.pendingCredential
                _uiState.update { it.copy(isLoading = false, pendingLinkEmail = e.email) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Google sign-in failed") }
            }
        }
    }

    fun linkGoogleAfterPasswordAuth(password: String) {
        val email = _uiState.value.pendingLinkEmail ?: return
        val credential = pendingLinkCredential ?: return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                Firebase.auth.signInWithEmailAndPassword(email, password).await()
                val currentUser = Firebase.auth.currentUser
                    ?: run {
                        _uiState.update { it.copy(isLoading = false, error = "Sign-in succeeded but no user session") }
                        return@launch
                    }
                currentUser.linkWithCredential(credential).await()
                pendingLinkCredential = null
                applyNewUserGate()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Linking failed") }
            }
        }
    }

    private suspend fun applyNewUserGate() {
        val alreadyRestored = appSettingsDataStore.hasRestoredOnce.first()
        if (!alreadyRestored) {
            appSettingsDataStore.setHasRestoredOnce(true)
            // Block only on the profile pull so needsProfileSetup is correct before navigation.
            // Workouts and routines sync in the background without delaying sign-in.
            firestoreSyncManager.pullProfileOnly()
            firestoreSyncManager.launchBackgroundSync()
        }
        val dbUser = userSessionManager.getCurrentUser()
        _uiState.update {
            it.copy(
                isLoading = false,
                isSignedIn = dbUser != null,
                needsProfileSetup = dbUser == null,
                pendingLinkEmail = null
            )
        }
    }

    fun dismissLinkPrompt() {
        pendingLinkCredential = null
        _uiState.update { it.copy(pendingLinkEmail = null) }
    }

    fun isEmailVerified(): Boolean {
        return Firebase.auth.currentUser?.isEmailVerified == true
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                Firebase.auth.sendPasswordResetEmail(email).await()
                _uiState.update { it.copy(isLoading = false, resetEmailSent = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun dismissResetConfirmation() {
        _uiState.update { it.copy(resetEmailSent = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
