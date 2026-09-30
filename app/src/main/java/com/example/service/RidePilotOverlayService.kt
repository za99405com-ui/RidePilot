package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
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

class RidePilotOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "ridepilot_overlay_channel"
        const val NOTIFICATION_ID = 1001
        var isOverlayRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMinimized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isOverlayRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingOverlay()
        observeLiveState()
    }

    override fun onDestroy() {
        super.onDestroy()
        isOverlayRunning = false
        serviceScope.cancel()
        overlayView?.let { windowManager?.removeView(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RidePilot Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "المساعد العائم لقراءة طلبات Uber وinDrive"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RidePilot شغال")
            .setContentText("المساعد العائم يراقب شاشات Uber وinDrive")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createFloatingOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 200
        }

        val context = this
        val root = FrameLayout(context).apply {
            setBackgroundColor(0x00000000)
        }

        val density = resources.displayMetrics.density
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
            setBackgroundColor(0xF20B0D12.toInt())
            elevation = 12f
            layoutParams = FrameLayout.LayoutParams((255 * density).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        // Header with App Title, Minimize and Drag Handle
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(context).apply {
            text = "RidePilot"
            setTextColor(0xFF5B9CFF.toInt())
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val minimizeBtn = TextView(context).apply {
            text = "—"
            setTextColor(0xFF94A3B8.toInt())
            textSize = 18f
            setPadding(16, 0, 16, 0)
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(0xFFEF4444.toInt())
            textSize = 16f
            setPadding(16, 0, 8, 0)
        }

        headerRow.addView(titleText)
        headerRow.addView(minimizeBtn)
        headerRow.addView(closeBtn)
        cardLayout.addView(headerRow)

        // Body Container (collapsible)
        val bodyLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 0)
        }

        val statusBadge = TextView(context).apply {
            text = "في انتظار الطلب..."
            textSize = 12.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFF59E0B.toInt()) // amber
        }

        val detailsText = TextView(context).apply {
            text = "افتح Uber أو inDrive لبدء التحليل التلقائي"
            textSize = 10.5f
            setTextColor(0xFFB8BDC8.toInt())
            setPadding(0, (4 * density).toInt(), 0, (6 * density).toInt())
            maxLines = 4
        }

        // Action Buttons Row: PAUSE & STOP
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        val pauseBtn = Button(context).apply {
            text = "إيقاف"
            setBackgroundColor(0xFF2A2E36.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, (36 * density).toInt(), 1f).apply {
                marginEnd = (6 * density).toInt()
            }
        }

        val stopBtn = Button(context).apply {
            text = "STOP"
            setBackgroundColor(0xFFB3261E.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, (36 * density).toInt(), 1f)
        }

        btnRow.addView(pauseBtn)
        btnRow.addView(stopBtn)

        bodyLayout.addView(statusBadge)
        bodyLayout.addView(detailsText)
        bodyLayout.addView(btnRow)
        cardLayout.addView(bodyLayout)
        root.addView(cardLayout)

        // Touch listener for dragging anywhere on the overlay
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams!!.x
                    initialY = layoutParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams!!.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams!!.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(root, layoutParams)
                    true
                }
                else -> false
            }
        }

        minimizeBtn.setOnClickListener {
            isMinimized = !isMinimized
            bodyLayout.visibility = if (isMinimized) View.GONE else View.VISIBLE
            minimizeBtn.text = if (isMinimized) "＋" else "—"
        }

        closeBtn.setOnClickListener {
            stopSelf()
        }

        pauseBtn.setOnClickListener {
            serviceScope.launch {
                val app = RidePilotApplication.instance
                app.settingsRepository.setAutomationEnabled(false)
                statusBadge.text = "⏸ تم إيقاف الأتمتة مؤقتاً"
                statusBadge.setTextColor(0xFFF59E0B.toInt())
            }
        }

        stopBtn.setOnClickListener {
            serviceScope.launch {
                val app = RidePilotApplication.instance
                app.settingsRepository.setEmergencyStop(true)
                stopSelf()
            }
        }

        overlayView = root
        windowManager?.addView(root, layoutParams)
    }

    private fun observeLiveState() {
        val root = overlayView ?: return
        val card = (root as FrameLayout).getChildAt(0) as LinearLayout
        val body = card.getChildAt(1) as LinearLayout
        val statusBadge = body.getChildAt(0) as TextView
        val detailsText = body.getChildAt(1) as TextView

        serviceScope.launch {
            combine(
                RidePilotAccessibilityService.activeTarget,
                RidePilotAccessibilityService.latestUberAnalysis,
                RidePilotAccessibilityService.latestInDriveParsed
            ) { target, uber, inDrive -> Triple(target, uber, inDrive) }
                .collect { (target, uber, inDrive) ->
                    when (target) {
                        AppTarget.UBER -> {
                            if (uber == null) {
                                statusBadge.text = "Uber • جاري قراءة الطلب"
                                statusBadge.setTextColor(0xFF5B9CFF.toInt())
                                detailsText.text = "انتظر لحظة حتى تكتمل قراءة السعر والمسافات."
                            } else {
                                val good = uber.isPriceViable && uber.isInsideZone
                                statusBadge.text = if (good) "Uber • ✓ مناسب" else "Uber • ✕ غير مناسب"
                                statusBadge.setTextColor(if (good) 0xFF58C98D.toInt() else 0xFFFF6B6B.toInt())
                                val offer = uber.offer
                                detailsText.text = buildString {
                                    append("${offer.displayedPrice ?: 0} ج.م")
                                    append("  •  ${uber.totalPricingDistanceKm} كم")
                                    append("\n${uber.pricePerKm} ج/كم  •  الحد ${uber.minRequiredPrice} ج")
                                    if (!uber.isInsideZone) append("\nخارج منطقة العمل")
                                }
                            }
                        }

                        AppTarget.INDRIVE -> {
                            if (inDrive == null) {
                                statusBadge.text = "inDrive • جاري القراءة"
                                statusBadge.setTextColor(0xFF5B9CFF.toInt())
                                detailsText.text = ""
                            } else {
                                val offline = inDrive.isOffline
                                statusBadge.text = if (offline) "inDrive • غير متصل" else "inDrive • تحقق من الحالة"
                                statusBadge.setTextColor(if (offline) 0xFF58C98D.toInt() else 0xFFFFB84D.toInt())
                                detailsText.text = buildString {
                                    append(inDrive.screenType.name)
                                    inDrive.activeOffer?.let { offer ->
                                        append("\n${offer.displayedPrice ?: "—"} ج.م")
                                        offer.tripDistanceKm?.let { append("  •  $it كم") }
                                    }
                                    if (inDrive.orderCards.isNotEmpty()) {
                                        append("\nطلبات مرصودة: ${inDrive.orderCards.size}")
                                    }
                                }
                            }
                        }

                        null -> {
                            statusBadge.text = "RidePilot • جاهز"
                            statusBadge.setTextColor(0xFF5B9CFF.toInt())
                            detailsText.text = "افتح Uber أو inDrive."
                        }
                    }
                }
        }
    }

}
