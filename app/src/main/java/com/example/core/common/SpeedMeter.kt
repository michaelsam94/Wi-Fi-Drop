package com.example.core.common

class SpeedMeter(private val windowMs: Long = 1000) {
    private val samples = ArrayDeque<Pair<Long, Long>>() // (timestamp, bytes)

    fun record(bytes: Long) {
        synchronized(this) {
            val now = System.currentTimeMillis()
            samples.addLast(now to bytes)
            while (samples.isNotEmpty() && now - samples.first().first > windowMs) {
                samples.removeFirst()
            }
        }
    }

    val currentBps: Long
        get() = synchronized(this) {
            val now = System.currentTimeMillis()
            while (samples.isNotEmpty() && now - samples.first().first > windowMs) {
                samples.removeFirst()
            }
            if (samples.size < 2) return 0L
            val totalBytes = samples.sumOf { it.second }
            val oldest = samples.first().first
            val newest = samples.last().first
            val duration = (newest - oldest).coerceAtLeast(100) // prevent divide by zero
            (totalBytes * 1000L / duration).coerceAtLeast(0L)
        }
}
