package com.example.shortsbot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    companion object {
        // Максимальная задержка (около 31 года), безопасная для Handler
        const val MAX = 1_000_000_000_000L
    }

    private fun edit(value: String, numeric: Boolean): EditText =
        EditText(this).apply {
            setText(value)
            inputType = if (numeric)
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            else
                InputType.TYPE_CLASS_TEXT
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val pattern = edit("+1,-1", numeric = false)
        val cycles = edit("0", numeric = true)
        val hours = edit("0", numeric = true)
        val ms = edit("0", numeric = true)
        val swipe = edit("60", numeric = true)

        fun add(label: String, v: View) {
            col.addView(TextView(this).apply { text = label })
            col.addView(v)
        }

        add("Шаблон: +1 = вверх, -1 = вниз (через запятую)", pattern)
        add("Повторов шаблона (0 = бесконечно)", cycles)
        add("Задержка на шортсе: часы", hours)
        add("Задержка на шортсе: миллисекунды", ms)
        add("Длительность свайпа, мс (меньше = быстрее, минимум 20)", swipe)

        col.addView(Button(this).apply {
            text = "Открыть настройки спец. возможностей"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        col.addView(Button(this).apply {
            text = "СТАРТ"
            setOnClickListener {
                val svc = ShortsScrollService.instance
                if (svc == null) {
                    Toast.makeText(this@MainActivity,
                        "Сначала включите службу в спец. возможностях",
                        Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    return@setOnClickListener
                }

                val tokens = pattern.text.toString().split(",")
                    .mapNotNull { it.trim().toIntOrNull() }

                val h = (hours.text.toString().toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                val hMs = (h * 3_600_000.0).toLong().coerceAtMost(MAX)
                val mMs = (ms.text.toString().toLongOrNull() ?: 0L).coerceAtLeast(0L).coerceAtMost(MAX)
                val delay = (hMs + mMs).coerceAtMost(MAX)

                svc.start(
                    pattern = tokens,
                    cycles = cycles.text.toString().toIntOrNull() ?: 0,
                    pauseMs = delay,
                    swipeMs = swipe.text.toString().toLongOrNull() ?: 60L
                )
                Toast.makeText(this@MainActivity,
                    "Запущено. Откройте YouTube Shorts",
                    Toast.LENGTH_SHORT).show()
            }
        })

        col.addView(Button(this).apply {
            text = "СТОП"
            setOnClickListener { ShortsScrollService.instance?.stop() }
        })

        setContentView(ScrollView(this).apply { addView(col) })
    }
}
