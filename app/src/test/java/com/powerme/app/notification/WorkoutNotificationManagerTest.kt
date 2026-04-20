package com.powerme.app.notification

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for WorkoutNotificationManager notification content and gating logic.
 *
 * Note: NotificationCompat.Builder requires a real Context (not a mock) to build
 * Notification objects. These tests focus on the gating / guard logic (notificationsEnabled
 * flag) using a mock NotificationManager, without actually building Notification objects.
 */
class WorkoutNotificationManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockNotificationManager: NotificationManager

    @Before
    fun setup() {
        mockNotificationManager = mock()
        mockContext = mock()
        whenever(mockContext.getSystemService(Context.NOTIFICATION_SERVICE))
            .thenReturn(mockNotificationManager)
        whenever(mockContext.packageName).thenReturn("com.powerme.app")
    }

    @Test
    fun `postRestDoneNotification suppressed when notificationsEnabled is false`() {
        val manager = WorkoutNotificationManager(mockContext)

        manager.postRestDoneNotification(
            exerciseName = "Bench Press",
            setInfo = "Set 3",
            notificationsEnabled = false
        )

        verify(mockNotificationManager, never()).notify(any(), any())
    }

    @Test
    fun `postSummaryNotification suppressed when notificationsEnabled is false`() {
        val manager = WorkoutNotificationManager(mockContext)

        manager.postSummaryNotification(
            workoutName = "Push Day",
            durationText = "45m",
            sets = 12,
            notificationsEnabled = false
        )

        verify(mockNotificationManager, never()).notify(any(), any())
    }

    @Test
    fun `cancelAll cancels both notification IDs`() {
        val manager = WorkoutNotificationManager(mockContext)

        manager.cancelAll()

        verify(mockNotificationManager).cancel(WorkoutNotificationManager.NOTIFICATION_ID_PERSISTENT)
        verify(mockNotificationManager).cancel(WorkoutNotificationManager.NOTIFICATION_ID_REST_DONE)
    }

    @Test
    fun `cancelRestDoneNotification cancels only rest done notification`() {
        val manager = WorkoutNotificationManager(mockContext)

        manager.cancelRestDoneNotification()

        verify(mockNotificationManager).cancel(WorkoutNotificationManager.NOTIFICATION_ID_REST_DONE)
        verify(mockNotificationManager, never()).cancel(WorkoutNotificationManager.NOTIFICATION_ID_PERSISTENT)
    }

    @Test
    fun `notification IDs are distinct`() {
        assertFalse(
            WorkoutNotificationManager.NOTIFICATION_ID_PERSISTENT ==
                WorkoutNotificationManager.NOTIFICATION_ID_REST_DONE
        )
    }

    @Test
    fun `channel IDs are distinct`() {
        assertFalse(
            WorkoutNotificationManager.CHANNEL_ACTIVE ==
                WorkoutNotificationManager.CHANNEL_REST
        )
    }

    @Test
    fun `action constants are distinct`() {
        val actions = setOf(
            WorkoutNotificationManager.ACTION_SKIP_REST,
            WorkoutNotificationManager.ACTION_FINISH_WORKOUT,
            WorkoutNotificationManager.ACTION_START_FOREGROUND
        )
        assertEquals(3, actions.size)
    }
}
