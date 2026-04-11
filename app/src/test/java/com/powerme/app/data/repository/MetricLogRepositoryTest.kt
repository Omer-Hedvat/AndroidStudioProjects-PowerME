package com.powerme.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.powerme.app.data.database.MetricLog
import com.powerme.app.data.database.MetricLogDao
import com.powerme.app.data.database.MetricType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Calendar

class MetricLogRepositoryTest {

    private lateinit var repository: MetricLogRepository
    private lateinit var mockDao: MetricLogDao
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockAuth: FirebaseAuth

    @Before
    fun setup() {
        mockDao = mock()
        mockFirestore = mock()
        mockAuth = mock()
        whenever(mockAuth.currentUser).thenReturn(null)
        repository = MetricLogRepository(mockDao, mockFirestore, mockAuth)
    }

    // ── upsertTodayIfChanged ──────────────────────────────────────────────────

    @Test
    fun `upsertTodayIfChanged - no prior row - inserts new entry`() = runTest {
        whenever(mockDao.getLatestForType(MetricType.WEIGHT.name)).thenReturn(null)

        repository.upsertTodayIfChanged(MetricType.WEIGHT, 80.0)

        verify(mockDao).insert(any())
    }

    @Test
    fun `upsertTodayIfChanged - today same value - no-op`() = runTest {
        val entry = MetricLog(
            id = 1,
            timestamp = System.currentTimeMillis(),
            type = MetricType.WEIGHT,
            value = 80.0
        )
        whenever(mockDao.getLatestForType(MetricType.WEIGHT.name)).thenReturn(entry)

        repository.upsertTodayIfChanged(MetricType.WEIGHT, 80.0)

        verify(mockDao, never()).delete(any())
        verify(mockDao, never()).insert(any())
    }

    @Test
    fun `upsertTodayIfChanged - today different value - replaces row`() = runTest {
        val entry = MetricLog(
            id = 1,
            timestamp = System.currentTimeMillis(),
            type = MetricType.WEIGHT,
            value = 79.5
        )
        whenever(mockDao.getLatestForType(MetricType.WEIGHT.name)).thenReturn(entry)

        repository.upsertTodayIfChanged(MetricType.WEIGHT, 80.0)

        verify(mockDao).delete(entry)
        verify(mockDao).insert(any())
    }

    @Test
    fun `upsertTodayIfChanged - prior row from yesterday - appends new row without deleting old`() = runTest {
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        val entry = MetricLog(
            id = 1,
            timestamp = yesterday,
            type = MetricType.BODY_FAT,
            value = 18.0
        )
        whenever(mockDao.getLatestForType(MetricType.BODY_FAT.name)).thenReturn(entry)

        repository.upsertTodayIfChanged(MetricType.BODY_FAT, 17.5)

        verify(mockDao, never()).delete(any())
        verify(mockDao).insert(any())
    }
}
