package com.powerme.app.ai

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiCoreAvailabilityTest {

    @Test
    fun `check returns Ready when model status is AVAILABLE`() = runTest {
        val model = mock<GenerativeModel>()
        whenever(model.checkStatus()).thenReturn(FeatureStatus.AVAILABLE)
        val availability = AiCoreAvailabilityTestable(model)

        assertEquals(AiCoreStatus.Ready, availability.check())
    }

    @Test
    fun `check returns NeedsDownload when model status is DOWNLOADABLE`() = runTest {
        val model = mock<GenerativeModel>()
        whenever(model.checkStatus()).thenReturn(FeatureStatus.DOWNLOADABLE)
        val availability = AiCoreAvailabilityTestable(model)

        assertEquals(AiCoreStatus.NeedsDownload, availability.check())
    }

    @Test
    fun `check returns NeedsDownload when model status is DOWNLOADING`() = runTest {
        val model = mock<GenerativeModel>()
        whenever(model.checkStatus()).thenReturn(FeatureStatus.DOWNLOADING)
        val availability = AiCoreAvailabilityTestable(model)

        assertEquals(AiCoreStatus.NeedsDownload, availability.check())
    }

    @Test
    fun `check returns NotSupported when model status is UNAVAILABLE`() = runTest {
        val model = mock<GenerativeModel>()
        whenever(model.checkStatus()).thenReturn(FeatureStatus.UNAVAILABLE)
        val availability = AiCoreAvailabilityTestable(model)

        assertEquals(AiCoreStatus.NotSupported, availability.check())
    }

    @Test
    fun `check returns NotSupported when SDK throws`() = runTest {
        val availability = AiCoreAvailabilityThrows()

        assertEquals(AiCoreStatus.NotSupported, availability.check())
    }

    @Test
    fun `check updates status StateFlow`() = runTest {
        val model = mock<GenerativeModel>()
        whenever(model.checkStatus()).thenReturn(FeatureStatus.AVAILABLE)
        val availability = AiCoreAvailabilityTestable(model)

        assertEquals(AiCoreStatus.NotSupported, availability.status.value)
        availability.check()
        assertEquals(AiCoreStatus.Ready, availability.status.value)
    }
}

private class AiCoreAvailabilityTestable(private val model: GenerativeModel) : AiCoreAvailability() {
    override suspend fun createModel() = model
}

private class AiCoreAvailabilityThrows : AiCoreAvailability() {
    override suspend fun createModel(): GenerativeModel = throw RuntimeException("AICore not available")
}
