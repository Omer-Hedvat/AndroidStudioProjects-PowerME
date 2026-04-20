package com.powerme.app.ai

import com.powerme.app.data.secure.SecurePreferencesStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GeminiKeyResolverTest {

    private lateinit var mockStore: SecurePreferencesStore

    @Before
    fun setup() {
        mockStore = mock()
    }

    @Test
    fun `user key set - returns UserKey`() {
        whenever(mockStore.getUserGeminiApiKey()).thenReturn("user-key-123")
        val resolver = GeminiKeyResolver(store = mockStore, shippedKey = "shipped-key")
        assertEquals(KeyResolution.UserKey("user-key-123"), resolver.resolve())
    }

    @Test
    fun `no user key and shipped key present - returns ShippedKey`() {
        whenever(mockStore.getUserGeminiApiKey()).thenReturn(null)
        val resolver = GeminiKeyResolver(store = mockStore, shippedKey = "shipped-key")
        assertEquals(KeyResolution.ShippedKey("shipped-key"), resolver.resolve())
    }

    @Test
    fun `no user key and blank shipped key - returns NoKey`() {
        whenever(mockStore.getUserGeminiApiKey()).thenReturn(null)
        val resolver = GeminiKeyResolver(store = mockStore, shippedKey = "")
        assertEquals(KeyResolution.NoKey, resolver.resolve())
    }

    @Test
    fun `user key is whitespace-only - falls back to shipped key`() {
        whenever(mockStore.getUserGeminiApiKey()).thenReturn("   ")
        val resolver = GeminiKeyResolver(store = mockStore, shippedKey = "shipped-key")
        assertEquals(KeyResolution.ShippedKey("shipped-key"), resolver.resolve())
    }
}
