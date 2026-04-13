package com.powerme.app.ui.metrics

import com.powerme.app.analytics.WeeklyInsights
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.HealthConnectSync
import com.powerme.app.data.database.HealthConnectSyncDao
import com.powerme.app.data.database.MetricLog
import com.powerme.app.data.database.MetricType
import com.powerme.app.data.database.User
import com.powerme.app.data.database.ageYears
import com.powerme.app.data.repository.AnalyticsRepository
import com.powerme.app.data.repository.MetricLogRepository
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.health.HealthConnectReadResult
import com.powerme.app.util.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MetricsViewModelBodyVitalsTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockAnalyticsRepository: AnalyticsRepository
    private lateinit var mockMetricLogRepository: MetricLogRepository
    private lateinit var mockHealthConnectManager: HealthConnectManager
    private lateinit var mockUserSessionManager: UserSessionManager
    private lateinit var mockHealthConnectSyncDao: HealthConnectSyncDao
    private lateinit var mockAppSettingsDataStore: AppSettingsDataStore

    private val sampleUser = User(
        email = "test@example.com",
        age = 32,
        weightKg = 80f,
        bodyFatPercent = 18f,
        heightCm = 181f
    )

    private val sampleSync = HealthConnectSync(
        date = LocalDate.now(),
        sleepDurationMinutes = 450,
        hrv = 48.0,
        rhr = 58,
        steps = 7842,
        syncTimestamp = System.currentTimeMillis() - 5 * 60_000L
    )

    private val sampleHcResult = HealthConnectReadResult(
        weight = 80.0,
        height = 181f,
        bodyFat = 18.0,
        sleepMinutes = 450,
        hrv = 48.0,
        rhr = 58,
        steps = 7842,
        lastSyncTimestamp = System.currentTimeMillis()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockAnalyticsRepository = mock()
        mockMetricLogRepository = mock()
        mockHealthConnectManager = mock()
        mockUserSessionManager = mock()
        mockHealthConnectSyncDao = mock()
        mockAppSettingsDataStore = mock()
        whenever(mockAppSettingsDataStore.unitSystem).thenReturn(flowOf(UnitSystem.METRIC))

        whenever(mockMetricLogRepository.getByType(MetricType.WEIGHT)).thenReturn(flowOf(emptyList()))
        whenever(mockMetricLogRepository.getByType(MetricType.BODY_FAT)).thenReturn(flowOf(emptyList()))
        whenever(mockMetricLogRepository.getByType(MetricType.HEIGHT)).thenReturn(flowOf(emptyList()))

        runBlocking {
            whenever(mockAnalyticsRepository.generateWeeklyInsights()).thenReturn(
                WeeklyInsights(
                    weekStartDate = 0L,
                    weekEndDate = 0L,
                    status = "OK",
                    summary = "",
                    volumeLoadAnomalies = emptyList(),
                    progressionAnomalies = emptyList(),
                    recommendations = emptyList(),
                    healthPerformanceCorrelation = null
                )
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): MetricsViewModel = MetricsViewModel(
        analyticsRepository = mockAnalyticsRepository,
        metricLogRepository = mockMetricLogRepository,
        healthConnectManager = mockHealthConnectManager,
        userSessionManager = mockUserSessionManager,
        healthConnectSyncDao = mockHealthConnectSyncDao,
        appSettingsDataStore = mockAppSettingsDataStore
    )

    // ── HC unavailable ────────────────────────────────────────────────────────

    @Test
    fun `loadBodyVitals - HC unavailable - sets UNAVAILABLE state`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)

        val vm = buildViewModel()
        runCurrent()

        assertEquals(HcAvailability.UNAVAILABLE, vm.uiState.value.bodyVitals.hcAvailability)
    }

    // ── Permissions not granted ───────────────────────────────────────────────

    @Test
    fun `loadBodyVitals - available but not granted - sets AVAILABLE_NOT_GRANTED`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking { whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(false) }

        val vm = buildViewModel()
        runCurrent()

        assertEquals(HcAvailability.AVAILABLE_NOT_GRANTED, vm.uiState.value.bodyVitals.hcAvailability)
    }

    // ── Connected with data ───────────────────────────────────────────────────

    @Test
    fun `loadBodyVitals - granted with user and sync - populates all fields`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(true)
            whenever(mockUserSessionManager.getCurrentUser()).thenReturn(sampleUser)
            whenever(mockHealthConnectSyncDao.getLatestSync()).thenReturn(sampleSync)
        }

        val vm = buildViewModel()
        runCurrent()

        val vitals = vm.uiState.value.bodyVitals
        assertEquals(HcAvailability.AVAILABLE_GRANTED, vitals.hcAvailability)
        assertEquals(32, vitals.age)
        assertEquals(58, vitals.rhrBpm)
        assertEquals(48.0, vitals.hrvMs!!, 0.001)
        assertEquals(450, vitals.sleepMinutes)
        assertEquals(7842, vitals.stepsToday)
    }

    // ── BMI computation ───────────────────────────────────────────────────────

    @Test
    fun `BMI computed correctly from weight and height`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(true)
            whenever(mockUserSessionManager.getCurrentUser()).thenReturn(sampleUser)
            whenever(mockHealthConnectSyncDao.getLatestSync()).thenReturn(sampleSync)
        }
        // Weight from MetricLog
        whenever(mockMetricLogRepository.getByType(MetricType.WEIGHT)).thenReturn(
            flowOf(listOf(MetricLog(type = MetricType.WEIGHT, value = 80.0)))
        )

        val vm = buildViewModel()
        runCurrent()

        val bmi = vm.uiState.value.bodyVitals.bmi
        assertNotNull(bmi)
        // 80 / (1.81^2) ≈ 24.39
        assertEquals(24.39, bmi!!, 0.1)
    }

    @Test
    fun `BMI is null when height is missing`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(true)
            whenever(mockUserSessionManager.getCurrentUser()).thenReturn(
                sampleUser.copy(heightCm = null)
            )
            whenever(mockHealthConnectSyncDao.getLatestSync()).thenReturn(sampleSync)
        }

        val vm = buildViewModel()
        runCurrent()

        assertNull(vm.uiState.value.bodyVitals.bmi)
    }

    // ── 7d delta ──────────────────────────────────────────────────────────────

    @Test
    fun `7d delta - returns signed difference when reference entry exists in window`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(true)
            whenever(mockUserSessionManager.getCurrentUser()).thenReturn(sampleUser)
            whenever(mockHealthConnectSyncDao.getLatestSync()).thenReturn(sampleSync)
        }
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        whenever(mockMetricLogRepository.getByType(MetricType.WEIGHT)).thenReturn(
            flowOf(listOf(
                MetricLog(id = 1, timestamp = sevenDaysAgo - 1_000, type = MetricType.WEIGHT, value = 79.0),
                MetricLog(id = 2, timestamp = System.currentTimeMillis() - 1_000, type = MetricType.WEIGHT, value = 80.5)
            ))
        )

        val vm = buildViewModel()
        runCurrent()

        val delta = vm.uiState.value.bodyVitals.weightDelta7d
        assertNotNull(delta)
        assertEquals(1.5, delta!!, 0.01)
    }

    @Test
    fun `7d delta - null when no entry in 7 to 10 day window`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(true)
            whenever(mockUserSessionManager.getCurrentUser()).thenReturn(sampleUser)
            whenever(mockHealthConnectSyncDao.getLatestSync()).thenReturn(sampleSync)
        }
        whenever(mockMetricLogRepository.getByType(MetricType.WEIGHT)).thenReturn(
            flowOf(listOf(
                MetricLog(id = 1, timestamp = System.currentTimeMillis() - 1_000, type = MetricType.WEIGHT, value = 80.5)
            ))
        )

        val vm = buildViewModel()
        runCurrent()

        assertNull(vm.uiState.value.bodyVitals.weightDelta7d)
    }

    // ── syncHealthConnect ──────────────────────────────────────────────────────

    @Test
    fun `syncHealthConnect - sets isSyncing true then false on success`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(true)
            whenever(mockUserSessionManager.getCurrentUser()).thenReturn(sampleUser)
            whenever(mockHealthConnectSyncDao.getLatestSync()).thenReturn(sampleSync)
            whenever(mockHealthConnectManager.syncAndRead()).thenReturn(sampleHcResult)
        }

        val vm = buildViewModel()
        runCurrent()

        vm.syncHealthConnect()
        runCurrent()

        assertFalse(vm.uiState.value.bodyVitals.isSyncing)
        assertNull(vm.uiState.value.bodyVitals.syncError)
        verify(mockHealthConnectManager).syncAndRead()
    }

    @Test
    fun `syncHealthConnect - sets syncError on failure`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(true)
            whenever(mockUserSessionManager.getCurrentUser()).thenReturn(sampleUser)
            whenever(mockHealthConnectSyncDao.getLatestSync()).thenReturn(sampleSync)
            whenever(mockHealthConnectManager.syncAndRead()).thenThrow(RuntimeException("network error"))
        }

        val vm = buildViewModel()
        runCurrent()

        vm.syncHealthConnect()
        runCurrent()

        assertFalse(vm.uiState.value.bodyVitals.isSyncing)
        assertNotNull(vm.uiState.value.bodyVitals.syncError)
        assertTrue(vm.uiState.value.bodyVitals.syncError!!.contains("network error"))
    }
}
