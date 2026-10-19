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
    private var swipeMs = 250L
    private var pauseMs = 0L
    private var running = false
    private var currentPkg = ""

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrEmpty()) currentPkg = pkg
        }
    }

    // Не останавливаем сценарий: система прерывает службу сама, в том числе во время жестов
    override fun onInterrupt() {}

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
        this.swipeMs = swipeMs.coerceAtLeast(50L)
        pos = 0
        running = true
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun isYouTubeOnTop(): Boolean {
        val pkg = rootInActiveWindow?.packageName?.toString() ?: currentPkg
        return pkg == YOUTUBE_PKG
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!running) return
            if (total >= 0 && pos >= total) {
                stop()
                return
            }
            if (!isYouTubeOnTop()) {
                handler.postDelayed(this, 500)
                return
            }
            val dir = steps[pos % steps.size]
            val sent = swipe(dir)
            // Если система не приняла жест, повторим тот же шаг, а не пропустим его
            if (sent) pos++
            handler.postDelayed(this, swipeMs + pauseMs)
        }
    }

    private fun swipe(direction: Int): Boolean {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val h = dm.heightPixels.toFloat()

        // direction > 0: палец снизу вверх -> следующий шортс
        // direction < 0: палец сверху вниз -> предыдущий шортс
        val far = h * 0.75f
        val near = h * 0.25f
        val (y1, y2) = if (direction > 0) far to near else near to far

        val path = Path().apply {
            moveTo(x, y1)
            lineTo(x, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, swipeMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }
}
