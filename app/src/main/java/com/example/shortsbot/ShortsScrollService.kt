package com.example.shortsbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

class ShortsScrollService : AccessibilityService() {

    companion object {
        var instance: ShortsScrollService? = null
        const val YOUTUBE_PKG = "com.google.android.youtube"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var steps: List<Int> = emptyList()
    private var pos = 0
    private var total = -1
    private var swipeMs = 60L
    private var pauseMs = 0L
    private var running = false
    private var currentPkg = ""

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPkg = event.packageName?.toString() ?: ""
        }
    }

    override fun onInterrupt() = stop()

    override fun onDestroy() {
        stop()
        instance = null
        super.onDestroy()
    }

    fun start(pattern: List<Int>, cycles: Int, pauseMs: Long, swipeMs: Long) {
        // +2 -> [1, 1], -1 -> [-1]
        val expanded = pattern.flatMap { n -> List(abs(n)) { if (n > 0) 1 else -1 } }
        if (expanded.isEmpty()) return

        steps = expanded
        total = if (cycles <= 0) -1 else cycles * expanded.size
        this.pauseMs = pauseMs.coerceAtLeast(0L)
        this.swipeMs = swipeMs.coerceAtLeast(20L)
        pos = 0
        running = true
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!running) return
            if (total >= 0 && pos >= total) {
                stop()
                return
            }
            // Свайпаем только в YouTube, иначе ждём
            if (currentPkg != YOUTUBE_PKG) {
                handler.postDelayed(this, 500)
                return
            }
            val dir = steps[pos % steps.size]
            pos++
            swipe(dir)
            handler.postDelayed(this, swipeMs + pauseMs)
        }
    }

    private fun swipe(direction: Int) {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val h = dm.heightPixels.toFloat()

        // direction > 0: палец снизу вверх -> следующий шортс
        // direction < 0: палец сверху вниз -> предыдущий шортс
        val (y1, y2) = if (direction > 0) h * 0.8f to h * 0.2f
                       else h * 0.2f to h * 0.8f

        val path = Path().apply {
            moveTo(x, y1)
            lineTo(x, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, swipeMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
