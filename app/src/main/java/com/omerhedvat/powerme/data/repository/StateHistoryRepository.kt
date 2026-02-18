package com.omerhedvat.powerme.data.repository

import com.omerhedvat.powerme.data.database.StateHistoryDao
import com.omerhedvat.powerme.data.database.StateHistoryEntry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StateHistoryRepository @Inject constructor(
    private val stateHistoryDao: StateHistoryDao
) {
    suspend fun insertEntry(entry: StateHistoryEntry): Long {
        return stateHistoryDao.insertEntry(entry)
    }

    suspend fun getRecentHistory(): List<StateHistoryEntry> {
        return stateHistoryDao.getRecentHistory()
    }

    suspend fun getHistoryForType(type: String): List<StateHistoryEntry> {
        return stateHistoryDao.getHistoryForType(type)
    }
}
