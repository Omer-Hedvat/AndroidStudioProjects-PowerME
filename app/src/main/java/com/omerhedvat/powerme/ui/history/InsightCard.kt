package com.omerhedvat.powerme.ui.history

/**
 * A single insight card for the weekly carousel at the top of HistoryScreen.
 * @param title Short label (e.g. "Weekly Volume")
 * @param value Formatted main value (e.g. "12,400 kg")
 * @param delta Optional delta indicator: positive = ↑, negative = ↓, null = → (no change)
 * @param subtitle Optional supporting text
 */
data class InsightCard(
    val title: String,
    val value: String,
    val delta: Double? = null,
    val subtitle: String? = null
)
