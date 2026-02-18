package com.omerhedvat.powerme.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.omerhedvat.powerme.data.database.User
import com.omerhedvat.powerme.util.UserSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSignedIn: Boolean = false,
    val needsEmailVerification: Boolean = false,
    val needsProfileSetup: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

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
                val dbUser = userSessionManager.getCurrentUser()
                if (dbUser == null) {
                    _uiState.update { it.copy(isLoading = false, needsProfileSetup = true) }
                } else {
                    _uiState.update { it.copy(isLoading = false, isSignedIn = true) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun saveProfile(
        name: String?,
        age: Int?,
        heightCm: Float?,
        occupationType: String?,
        parentalLoad: Int?,
        chronotype: String?,
        averageSleepHours: Float?
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val email = Firebase.auth.currentUser?.email ?: run {
                    _uiState.update { it.copy(isLoading = false, error = "Not signed in") }
                    return@launch
                }
                val user = User(
                    email = email,
                    name = name?.takeIf { it.isNotBlank() },
                    age = age,
                    heightCm = heightCm,
                    occupationType = occupationType,
                    parentalLoad = parentalLoad,
                    chronotype = chronotype,
                    averageSleepHours = averageSleepHours
                )
                userSessionManager.saveUser(user)
                _uiState.update { it.copy(isLoading = false, isSignedIn = true, needsProfileSetup = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun isEmailVerified(): Boolean {
        return Firebase.auth.currentUser?.isEmailVerified == true
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
