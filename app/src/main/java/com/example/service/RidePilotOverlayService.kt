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
import com.example.domain.engine.PricingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
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

        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 20)
            setBackgroundColor(0xF0121A24.toInt()) // dark modern card background
            elevation = 16f
        }

        // Header with App Title, Minimize and Drag Handle
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(context).apply {
            text = "RidePilot"
            setTextColor(0xFF10B981.toInt()) // Emerald Green
            textSize = 15f
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
            text = "في انتظار كشف الطلبات..."
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFF59E0B.toInt()) // amber
        }

        val detailsText = TextView(context).apply {
            text = "افتح Uber أو inDrive لبدء التحليل التلقائي"
            textSize = 12f
            setTextColor(0xFFCBD5E1.toInt())
            setPadding(0, 6, 0, 10)
        }

        // Action Buttons Row: PAUSE & STOP
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        val pauseBtn = Button(context).apply {
            text = "إيقاف مؤقت"
            setBackgroundColor(0xFFD97706.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, 95, 1f).apply {
                marginEnd = 10
            }
        }

        val stopBtn = Button(context).apply {
            text = "STOP كامل"
            setBackgroundColor(0xFFDC2626.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, 95, 1f)
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

        // Observe Uber analysis
        serviceScope.launch {
            RidePilotAccessibilityService.latestUberAnalysis.collectLatest { analysis ->
                if (analysis != null) {
                    val offer = analysis.offer
                    if (analysis.isPriceViable && analysis.isInsideZone) {
                        statusBadge.text = "UBER: ✅ مناسب"
                        statusBadge.setTextColor(0xFF10B981.toInt()) // Green
                    } else {
                        statusBadge.text = "UBER: ❌ غير مناسب"
                        statusBadge.setTextColor(0xFFEF4444.toInt()) // Red
                    }

                    val info = StringBuilder()
                    info.append("السعر: ${offer.displayedPrice ?: 0} ج.م\n")
                    info.append("المسافة: ${analysis.totalPricingDistanceKm} كم (${analysis.pricePerKm} ج.م/كم)\n")
                    info.append("الحد الأدنى: ${analysis.minRequiredPrice} ج.م\n")
                    info.append(analysis.zoneStatusReason)

                    detailsText.text = info.toString()
                }
            }
        }

        // Observe inDrive parsed
        serviceScope.launch {
            RidePilotAccessibilityService.latestInDriveParsed.collectLatest { parsed ->
                if (parsed != null && parsed.screenType != com.example.domain.parser.InDriveParser.InDriveScreenType.UNKNOWN) {
                    val statusText = if (parsed.isOffline) "inDrive: غير متصل (آمن)" else "inDrive: متصل ⚠"
                    statusBadge.text = statusText
                    statusBadge.setTextColor(if (parsed.isOffline) 0xFF10B981.toInt() else 0xFFEF4444.toInt())

                    val sb = StringBuilder()
                    sb.append("الشاشة: ${parsed.screenType.name}\n")
                    if (parsed.orderCards.isNotEmpty()) {
                        sb.append("الطلبات المرصودة: ${parsed.orderCards.size} طلبات\n")
                    }
                    if (parsed.activeOffer != null) {
                        val off = parsed.activeOffer
                        sb.append("عرض العميل: ${off.displayedPrice ?: 0} EGP (${off.tripDistanceKm ?: 0} كم)\n")
                    }
                    detailsText.text = sb.toString()
                }
            }
        }
    }
}
