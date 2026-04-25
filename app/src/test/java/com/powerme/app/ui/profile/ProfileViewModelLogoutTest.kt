package com.powerme.app.ui.profile

import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.MetricType
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.repository.HealthHistoryRepository
import com.powerme.app.data.repository.MetricLogRepository
import com.powerme.app.data.secure.SecurePreferencesStore
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.util.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for the logout slice of ProfileViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelLogoutTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockMetricLogRepository: MetricLogRepository
    private lateinit var mockAppSettingsDataStore: AppSettingsDataStore
    private lateinit var mockUserSessionManager: UserSessionManager
    private lateinit var mockHealthHistoryRepository: HealthHistoryRepository
    private lateinit var mockHealthConnectManager: HealthConnectManager
    private lateinit var mockDatabase: PowerMeDatabase
    private lateinit var mockSecurePreferencesStore: SecurePreferencesStore

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockMetricLogRepository = mock()
        mockAppSettingsDataStore = mock()
        mockUserSessionManager = mock()
        mockHealthHistoryRepository = mock()
        mockHealthConnectManager = mock()
        mockDatabase = mock()
        mockSecurePreferencesStore = mock()

        whenever(mockAppSettingsDataStore.unitSystem).thenReturn(flowOf(UnitSystem.METRIC))
        whenever(mockMetricLogRepository.getByType(MetricType.WEIGHT)).thenReturn(flowOf(emptyList()))
        whenever(mockMetricLogRepository.getByType(MetricType.BODY_FAT)).thenReturn(flowOf(emptyList()))
        whenever(mockHealthHistoryRepository.getActiveEntries()).thenReturn(flowOf(emptyList()))
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)
    }

    @After
    fun tearDown() {
        // Brief wait before resetting Main in case any background coroutine thread (e.g. from
        // a concurrently executing test class) is still dispatching to Dispatchers.Main.
        Thread.sleep(100)
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): ProfileViewModel = ProfileViewModel(
        userSessionManager = mockUserSessionManager,
        metricLogRepository = mockMetricLogRepository,
        appSettingsDataStore = mockAppSettingsDataStore,
        healthHistoryRepository = mockHealthHistoryRepository,
        healthConnectManager = mockHealthConnectManager,
        database = mockDatabase,
        securePreferencesStore = mockSecurePreferencesStore
    )

    @Test
    fun `signOut delegates to UserSessionManager clearUser`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        runCurrent()

        vm.signOut()
        runCurrent()

        verify(mockUserSessionManager).clearUser()
    }
}
