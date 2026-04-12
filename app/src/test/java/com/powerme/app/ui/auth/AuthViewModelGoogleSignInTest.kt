package com.powerme.app.ui.auth

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.auth.AuthCredential
import com.powerme.app.data.database.User
import com.powerme.app.util.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.mockito.kotlin.whenever

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
    private lateinit var mockContext: Context
    private lateinit var viewModel: AuthViewModel

    private val fakeUser = User(email = "test@example.com")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockUserSessionManager = mock()
        mockGoogleSignInHelper = mock()
        mockContext = mock()
        viewModel = AuthViewModel(mockUserSessionManager, mockGoogleSignInHelper)
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
}
