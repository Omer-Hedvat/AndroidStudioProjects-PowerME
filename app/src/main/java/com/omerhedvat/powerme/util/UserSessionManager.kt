package com.omerhedvat.powerme.util

import com.omerhedvat.powerme.data.database.User
import com.omerhedvat.powerme.data.database.UserDao
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

    suspend fun clearUser() {
        userDao.deleteUser()
        Firebase.auth.signOut()
    }
}
