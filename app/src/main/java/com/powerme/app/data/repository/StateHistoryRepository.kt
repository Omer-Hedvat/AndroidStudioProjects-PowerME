package com.powerme.app.data.repository

import com.powerme.app.data.database.StateHistoryDao
import com.powerme.app.data.database.StateHistoryEntry
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
