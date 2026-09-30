package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.RidePilotApplication
import com.example.data.model.AppTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

class RidePilotOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "ridepilot_overlay_channel"
        const val NOTIFICATION_ID = 1001
        var isOverlayRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var overlayRoot: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isOverlayRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createBubble()
    }

    override fun onDestroy() {
        super.onDestroy()
        isOverlayRunning = false
        serviceScope.cancel()
        overlayRoot?.let { windowManager?.removeView(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RidePilot",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "RidePilot يعمل في الخلفية"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RidePilot يعمل")
            .setContentText("مراقبة طلبات Uber وinDrive")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (8 * density).toInt()
            y = (150 * density).toInt()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }

        val bubble = TextView(this).apply {
            text = "R"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = bubbleDrawable(0xFF3978D9.toInt())
            layoutParams = LinearLayout.LayoutParams(
                (38 * density).toInt(),
                (38 * density).toInt()
            )
        }

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(
                (12 * density).toInt(),
                (10 * density).toInt(),
                (12 * density).toInt(),
                (10 * density).toInt()
            )
            background = roundedRect(0xF20B0D12.toInt(), 16f * density)
            layoutParams = LinearLayout.LayoutParams(
                (210 * density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * density).toInt()
            }
        }

        val title = TextView(this).apply {
            text = "RidePilot"
            textSize = 13f
            setTextColor(0xFFF5F7FA.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val status = TextView(this).apply {
            text = "جاهز"
            textSize = 11f
            setTextColor(0xFFB2B8C2.toInt())
            setPadding(0, (5 * density).toInt(), 0, (8 * density).toInt())
            maxLines = 3
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val pause = Button(this).apply {
            text = "إيقاف"
            textSize = 10f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2A2E36.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                (36 * density).toInt(),
                1f
            ).apply { marginEnd = (5 * density).toInt() }
        }

        val stop = Button(this).apply {
            text = "STOP"
            textSize = 10f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFB3261E.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                (36 * density).toInt(),
                1f
            )
        }

        buttons.addView(pause)
        buttons.addView(stop)
        details.addView(title)
        details.addView(status)
        details.addView(buttons)
        root.addView(bubble)
        root.addView(details)

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams!!.x
                    startY = layoutParams!!.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > 8f || abs(dy) > 8f) moved = true
                    if (moved) {
                        layoutParams!!.x = startX + dx.toInt()
                        layoutParams!!.y = startY + dy.toInt()
                        windowManager?.updateViewLayout(root, layoutParams)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    true
                }

                else -> false
            }
        }

        pause.setOnClickListener {
            serviceScope.launch {
                RidePilotApplication.instance.settingsRepository.setAutomationEnabled(false)
                status.text = "الأتمتة متوقفة"
            }
        }

        stop.setOnClickListener {
            serviceScope.launch {
                RidePilotApplication.instance.settingsRepository.setEmergencyStop(true)
                stopSelf()
            }
        }

        overlayRoot = root
        windowManager?.addView(root, layoutParams)

        serviceScope.launch {
            combine(
                RidePilotAccessibilityService.activeTarget,
                RidePilotAccessibilityService.latestUberAnalysis,
                RidePilotAccessibilityService.latestInDriveParsed,
                RidePilotAccessibilityService.automationStatus
            ) { target, uber, inDrive, message ->
                OverlayState(target, uber, inDrive, message)
            }.collect { state ->
                when (state.target) {
                    AppTarget.UBER -> {
                        val good = state.uber?.let { it.isPriceViable && it.isInsideZone }
                        bubble.text = "U"
                        bubble.background = bubbleDrawable(
                            when (good) {
                                true -> 0xFF2E9D63.toInt()
                                false -> 0xFFB3261E.toInt()
                                null -> 0xFF3978D9.toInt()
                            }
                        )
                        title.text = when (good) {
                            true -> "Uber • مناسب"
                            false -> "Uber • غير مناسب"
                            null -> "Uber • تحليل"
                        }
                        status.text = state.uber?.let {
                            "${it.offer.displayedPrice ?: "—"} ج • ${it.totalPricingDistanceKm} كم\n${it.pricePerKm} ج/كم • الحد ${it.minRequiredPrice} ج"
                        } ?: state.message
                    }

                    AppTarget.INDRIVE -> {
                        bubble.text = "iD"
                        bubble.background = bubbleDrawable(0xFF3978D9.toInt())
                        title.text = "inDrive"
                        status.text = state.message
                    }

                    null -> {
                        bubble.text = "R"
                        bubble.background = bubbleDrawable(0xFF3978D9.toInt())
                        title.text = "RidePilot"
                        status.text = "جاهز"
                    }
                }
            }
        }
    }

    private fun bubbleDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2, 0x55FFFFFF)
        }

    private fun roundedRect(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
            setStroke(1, 0x332A2E36)
        }

    private data class OverlayState(
        val target: AppTarget?,
        val uber: com.example.data.model.RideAnalysis?,
        val inDrive: com.example.domain.parser.InDriveParser.InDriveParsedScreen?,
        val message: String
    )
}
