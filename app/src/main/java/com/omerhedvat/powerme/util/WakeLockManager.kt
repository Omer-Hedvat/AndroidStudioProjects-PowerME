package com.omerhedvat.powerme.util

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeLockManager @Inject constructor(
    @ApplicationContext ctx: Context
) {
    private val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire() {
        if (wakeLock?.isHeld == true) return
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PowerME:TimerLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)  // 10 min max
    }

    fun release() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }
}
