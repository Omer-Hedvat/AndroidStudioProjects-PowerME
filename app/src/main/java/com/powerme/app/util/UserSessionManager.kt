package com.powerme.app.util

import com.powerme.app.data.database.User
import com.powerme.app.data.database.UserDao
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSessionManager @Inject constructor(
    private val userDao: UserDao
) {
    suspend fun getCurrentUser(): User? {
        return userDao.getCurrentUser()
    }

    suspend fun saveUser(user: User) {
        userDao.insertUser(user)
    }

    suspend fun isLoggedIn(): Boolean {
        return Firebase.auth.currentUser != null && userDao.getCurrentUser() != null
    }

    /**
     * Patches the body metrics fields on the current user with HC-sourced values.
     * Only updates fields where the HC value is non-null; existing values are preserved otherwise.
     * No-op if there is no logged-in user.
     */
    suspend fun updateBodyMetricsFromHc(weightKg: Double?, bodyFatPercent: Double?, heightCm: Double?) {
        val user = getCurrentUser() ?: return
        val updated = user.copy(
            weightKg = weightKg?.toFloat() ?: user.weightKg,
            bodyFatPercent = bodyFatPercent?.toFloat() ?: user.bodyFatPercent,
            heightCm = heightCm?.toFloat() ?: user.heightCm
        )
        saveUser(updated)
    }

    suspend fun clearUser() {
        userDao.deleteUser()
        Firebase.auth.signOut()
    }
}
