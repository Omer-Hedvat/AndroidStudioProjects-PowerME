package com.powerme.app.ui.auth

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.auth.AuthCredential
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.database.User
import com.powerme.app.data.sync.FirestoreSyncManager
import com.powerme.app.data.sync.SyncResult
import com.powerme.app.health.HealthConnectManager
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any

/**
 * Unit tests for AuthViewModel.signInWithGoogle().
 *
 * GoogleSignInHelper and UserSessionManager are mocked so no Android context or
 * Firebase connection is required. Tests cover all five exception/success branches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelGoogleSignInTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockUserSessionManager: UserSessionManager
    private lateinit var mockGoogleSignInHelper: GoogleSignInHelper
    private lateinit var mockFirestoreSyncManager: FirestoreSyncManager
    private lateinit var mockAppSettingsDataStore: AppSettingsDataStore
    private lateinit var mockHealthConnectManager: HealthConnectManager
    private lateinit var mockContext: Context
    private lateinit var viewModel: AuthViewModel

    private val fakeUser = User(email = "test@example.com")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockUserSessionManager = mock()
        mockGoogleSignInHelper = mock()
        mockFirestoreSyncManager = mock()
        mockAppSettingsDataStore = mock()
        mockHealthConnectManager = mock()
        mockContext = mock()
        // Default: already restored — auto-restore is a no-op for all existing tests.
        // HC is unavailable by default so the HC offer gate is skipped for existing tests.
        runBlocking {
            whenever(mockAppSettingsDataStore.hasRestoredOnce).thenReturn(flowOf(true))
            whenever(mockAppSettingsDataStore.hcOfferDismissed).thenReturn(flowOf(false))
            whenever(mockAppSettingsDataStore.setHasRestoredOnce(any())).thenAnswer { }
            whenever(mockFirestoreSyncManager.pullFromCloud()).thenReturn(SyncResult(success = true))
            whenever(mockFirestoreSyncManager.pullProfileOnly()).thenReturn(false)
            whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)
        }
        viewModel = AuthViewModel(mockUserSessionManager, mockGoogleSignInHelper, mockFirestoreSyncManager, mockAppSettingsDataStore, mockHealthConnectManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Test 1: Cancellation is silent ────────────────────────────────────────

    @Test
    fun `signInWithGoogle - user cancels - loading clears, no error shown`() = runTest(testDispatcher) {
        // thenAnswer bypasses Mockito's checked-exception validation (GetCredentialCancellationException
        // extends checked Exception, not RuntimeException).
        whenever(mockGoogleSignInHelper.signIn(mockContext))
            .thenAnswer { throw GetCredentialCancellationException() }

        viewModel.signInWithGoogle(mockContext)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.isSignedIn)
        assertFalse(state.needsProfileSetup)
    }

    // ── Test 2: No Google account on device ───────────────────────────────────

    @Test
    fun `signInWithGoogle - no credential - loading clears, error message set`() = runTest(testDispatcher) {
        whenever(mockGoogleSignInHelper.signIn(mockContext))
            .thenAnswer { throw NoCredentialException() }

        viewModel.signInWithGoogle(mockContext)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("No Google accounts available on this device", state.error)
        assertFalse(state.isSignedIn)
    }

    // ── Test 3: Generic failure ────────────────────────────────────────────────

    @Test
    fun `signInWithGoogle - generic exception - loading clears, error message set`() = runTest(testDispatcher) {
        whenever(mockGoogleSignInHelper.signIn(mockContext))
            .thenThrow(RuntimeException("network error"))

        viewModel.signInWithGoogle(mockContext)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("network error", state.error)
        assertFalse(state.isSignedIn)
    }

    // ── Test 4: Success — new user (no Room row) → needsProfileSetup ─────────

    @Test
    fun `signInWithGoogle - success, new user - needsProfileSetup true`() = runTest(testDispatcher) {
        whenever(mockGoogleSignInHelper.signIn(mockContext)).thenReturn(Unit)
        whenever(mockUserSessionManager.getCurrentUser()).thenReturn(null)

        viewModel.signInWithGoogle(mockContext)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.needsProfileSetup)
        assertFalse(state.isSignedIn)
        assertNull(state.error)
    }

    // ── Test 5: Success — returning user (Room row exists) → isSignedIn ───────

    @Test
    fun `signInWithGoogle - success, returning user - isSignedIn true`() = runTest(testDispatcher) {
        whenever(mockGoogleSignInHelper.signIn(mockContext)).thenReturn(Unit)
        whenever(mockUserSessionManager.getCurrentUser()).thenReturn(fakeUser)

        viewModel.signInWithGoogle(mockContext)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isSignedIn)
        assertFalse(state.needsProfileSetup)
        assertNull(state.error)
    }

    // ── Test 6: Collision — pendingLinkEmail set, no error ───────────────────

    @Test
    fun `signInWithGoogle - collision - sets pendingLinkEmail, no error`() = runTest(testDispatcher) {
        val mockCredential: AuthCredential = mock()
        whenever(mockGoogleSignInHelper.signIn(mockContext))
            .thenAnswer { throw AccountCollisionException("user@example.com", mockCredential) }

        viewModel.signInWithGoogle(mockContext)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("user@example.com", state.pendingLinkEmail)
        assertNull(state.error)
        assertFalse(state.isSignedIn)
        assertFalse(state.needsProfileSetup)
    }

    // ── Test 7: dismissLinkPrompt — clears pendingLinkEmail ──────────────────

    @Test
    fun `dismissLinkPrompt - clears pendingLinkEmail`() = runTest(testDispatcher) {
        val mockCredential: AuthCredential = mock()
        whenever(mockGoogleSignInHelper.signIn(mockContext))
            .thenAnswer { throw AccountCollisionException("user@example.com", mockCredential) }

        viewModel.signInWithGoogle(mockContext)
        runCurrent()
        viewModel.dismissLinkPrompt()

        val state = viewModel.uiState.value
        assertNull(state.pendingLinkEmail)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    // ── Test 8: Auto-restore triggers on first sign-in ───────────────────────

    @Test
    fun `signInWithGoogle - success, hasRestoredOnce false - pullProfileOnly called`() = runTest(testDispatcher) {
        whenever(mockAppSettingsDataStore.hasRestoredOnce).thenReturn(flowOf(false))
        whenever(mockGoogleSignInHelper.signIn(mockContext)).thenReturn(Unit)
        whenever(mockUserSessionManager.getCurrentUser()).thenReturn(fakeUser)

        viewModel.signInWithGoogle(mockContext)
        runCurrent()

        verify(mockFirestoreSyncManager).pullProfileOnly()
    }

    // ── Test 9: Auto-restore skipped when already restored ───────────────────

    @Test
    fun `signInWithGoogle - success, hasRestoredOnce true - pullProfileOnly not called`() = runTest(testDispatcher) {
        whenever(mockAppSettingsDataStore.hasRestoredOnce).thenReturn(flowOf(true))
        whenever(mockGoogleSignInHelper.signIn(mockContext)).thenReturn(Unit)
        whenever(mockUserSessionManager.getCurrentUser()).thenReturn(fakeUser)

        viewModel.signInWithGoogle(mockContext)
        runCurrent()

        verify(mockFirestoreSyncManager, never()).pullProfileOnly()
    }

    // ── Test 10: Auto-restore on first sign-in resolves before navigation ─────
    // pullProfileOnly blocks (fast — single doc), then launchBackgroundSync fires
    // workouts/routines without delaying sign-in. A restored profile means no ProfileSetup.

    @Test
    fun `signInWithGoogle - first sign-in, cloud restores profile - needsProfileSetup false`() = runTest(testDispatcher) {
        whenever(mockAppSettingsDataStore.hasRestoredOnce).thenReturn(flowOf(false))
        whenever(mockGoogleSignInHelper.signIn(mockContext)).thenReturn(Unit)
        // pullProfileOnly returns true — profile was imported
        whenever(mockFirestoreSyncManager.pullProfileOnly()).thenReturn(true)
        // After pull, a user now exists locally (simulates what pullProfileOnly would have written)
        whenever(mockUserSessionManager.getCurrentUser()).thenReturn(fakeUser)

        viewModel.signInWithGoogle(mockContext)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isSignedIn)
        assertFalse(state.needsProfileSetup)
        assertNull(state.error)
        verify(mockFirestoreSyncManager).pullProfileOnly()
    }

    // ── Test 11: HC offer shown when HC available and permissions not granted ──

    @Test
    fun `signInWithGoogle - returning user, HC available, not connected, not dismissed - needsHcOffer true`() = runTest(testDispatcher) {
        whenever(mockGoogleSignInHelper.signIn(mockContext)).thenReturn(Unit)
        whenever(mockUserSessionManager.getCurrentUser()).thenReturn(fakeUser)
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(false)
            whenever(mockAppSettingsDataStore.hcOfferDismissed).thenReturn(flowOf(false))
        }

        viewModel.signInWithGoogle(mockContext)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.needsHcOffer)
        assertFalse(state.isSignedIn)
        assertFalse(state.needsProfileSetup)
        assertNull(state.error)
    }

    // ── Test 12: HC offer skipped when already dismissed ─────────────────────

    @Test
    fun `signInWithGoogle - returning user, HC available, dismissed - isSignedIn true, no HC offer`() = runTest(testDispatcher) {
        whenever(mockGoogleSignInHelper.signIn(mockContext)).thenReturn(Unit)
        whenever(mockUserSessionManager.getCurrentUser()).thenReturn(fakeUser)
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(false)
            whenever(mockAppSettingsDataStore.hcOfferDismissed).thenReturn(flowOf(true))
        }

        viewModel.signInWithGoogle(mockContext)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.needsHcOffer)
        assertTrue(state.isSignedIn)
    }

    // ── Test 13: HC offer skipped when already connected ─────────────────────

    @Test
    fun `signInWithGoogle - returning user, HC permissions already granted - isSignedIn true, no HC offer`() = runTest(testDispatcher) {
        whenever(mockGoogleSignInHelper.signIn(mockContext)).thenReturn(Unit)
        whenever(mockUserSessionManager.getCurrentUser()).thenReturn(fakeUser)
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(true)
        }

        viewModel.signInWithGoogle(mockContext)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.needsHcOffer)
        assertTrue(state.isSignedIn)
    }
}
