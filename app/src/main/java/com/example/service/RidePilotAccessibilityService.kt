package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.RidePilotApplication
import com.example.data.model.AppLogEntry
import com.example.data.model.AppTarget
import com.example.data.model.AutomationState
import com.example.data.model.PricingDistanceMode
import com.example.data.model.RideAnalysis
import com.example.data.model.RideOffer
import com.example.domain.engine.PricingEngine
import com.example.domain.engine.ZoneEngine
import com.example.domain.parser.InDriveParser
import com.example.domain.parser.UberParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class RidePilotAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RidePilotAccService"

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _latestUberAnalysis = MutableStateFlow<RideAnalysis?>(null)
        val latestUberAnalysis: StateFlow<RideAnalysis?> = _latestUberAnalysis.asStateFlow()

        private val _latestInDriveParsed = MutableStateFlow<InDriveParser.InDriveParsedScreen?>(null)
        val latestInDriveParsed: StateFlow<InDriveParser.InDriveParsedScreen?> = _latestInDriveParsed.asStateFlow()

        private val _liveCalibrationOffer = MutableStateFlow<RideOffer?>(null)
        val liveCalibrationOffer: StateFlow<RideOffer?> = _liveCalibrationOffer.asStateFlow()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastEventTime = 0L
    private val debounceMs = 700L

    private val stateMachine by lazy {
        InDriveStateMachine(
            gestureDispatcher = { action -> executeGesture(action) },
            logger = { action, reason, price, dist, conf ->
                val app = RidePilotApplication.instance
                app.logRepository.log(
                    AppLogEntry(
                        app = AppTarget.INDRIVE,
                        screen = "InDriveScreen",
                        detectedPrice = price,
                        detectedDistance = dist,
                        zoneResult = "Evaluated",
                        minCalculated = null,
                        action = action,
                        reason = reason,
                        confidence = conf
                    )
                )
            }
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceConnected.value = true
        Log.i(TAG, "RidePilot Accessibility Service Connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceConnected.value = false
        serviceScope.cancel()
        Log.i(TAG, "RidePilot Accessibility Service Destroyed")
    }

    override fun onInterrupt() {
        Log.w(TAG, "RidePilot Accessibility Service Interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        val now = System.currentTimeMillis()
        if (now - lastEventTime < debounceMs) {
            return
        }
        lastEventTime = now

        serviceScope.launch {
            try {
                handlePackageEvent(packageName)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling accessibility event", e)
            }
        }
    }

    private suspend fun handlePackageEvent(packageName: String) {
        val app = RidePilotApplication.instance
        val rootNode = rootInActiveWindow ?: return

        // 1. Detect Uber
        if (packageName.contains("uber", ignoreCase = true)) {
            val nodes = UberParser.extractNodes(rootNode)
            val offer = UberParser.parseUberScreen(nodes)

            if (offer != null) {
                _liveCalibrationOffer.value = offer
                val bands = app.pricingRepository.getBandsDirect(AppTarget.UBER)
                val zones = app.zoneRepository.getEnabledZones()
                val distMode = app.settingsRepository.pricingDistanceMode.first()
                val zoneMode = app.settingsRepository.zoneVerificationMode.first()

                // Calculate pricing distance
                val pricingDistance = when (distMode) {
                    PricingDistanceMode.TRIP_ONLY -> offer.tripDistanceKm ?: 5.0
                    PricingDistanceMode.PICKUP_PLUS_TRIP -> (offer.pickupDistanceKm ?: 0.0) + (offer.tripDistanceKm ?: 5.0)
                }

                val calc = PricingEngine.calculateMinimumPrice(pricingDistance, bands)
                val zoneRes = ZoneEngine.evaluateOffer(
                    context = applicationContext,
                    pickupAddress = offer.pickupAddress,
                    destinationAddress = offer.destinationAddress,
                    zones = zones,
                    mode = zoneMode
                )

                val displayedPrice = offer.displayedPrice ?: 0.0
                val isPriceViable = displayedPrice >= calc.minimumPrice

                val analysis = RideAnalysis(
                    offer = offer,
                    totalPricingDistanceKm = pricingDistance,
                    applicableBand = calc.matchedBand,
                    minRequiredPrice = calc.minimumPrice,
                    pricePerKm = if (pricingDistance > 0) PricingEngine.roundToTwoDecimals(displayedPrice / pricingDistance) else 0.0,
                    isPriceViable = isPriceViable,
                    isInsideZone = zoneRes.isAllowed,
                    zoneStatusReason = zoneRes.reason,
                    pricingReason = calc.explanation,
                    actionRecommended = if (isPriceViable && zoneRes.isAllowed) "✅ مناسب" else "❌ غير مناسب"
                )

                _latestUberAnalysis.value = analysis

                // Log analysis
                app.logRepository.log(
                    AppLogEntry(
                        app = AppTarget.UBER,
                        screen = "OfferOverlay",
                        detectedPrice = displayedPrice,
                        detectedDistance = pricingDistance,
                        zoneResult = zoneRes.reason,
                        minCalculated = calc.minimumPrice,
                        action = "DISPLAY_OVERLAY",
                        reason = if (isPriceViable && zoneRes.isAllowed) "مناسب للعمل" else "أقل من الحد أو خارج الزون",
                        confidence = offer.confidence
                    )
                )

                // Ensure floating overlay service is running
                val overlayIntent = Intent(applicationContext, RidePilotOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(overlayIntent)
                } else {
                    startService(overlayIntent)
                }
            }
        }

        // 2. Detect inDrive
        else if (packageName.contains("indriver", ignoreCase = true) || packageName.contains("inDriver", ignoreCase = true)) {
            val parsedScreen = InDriveParser.parseScreen(rootNode)
            _latestInDriveParsed.value = parsedScreen
            if (parsedScreen.activeOffer != null) {
                _liveCalibrationOffer.value = parsedScreen.activeOffer
            }

            val automationEnabled = app.settingsRepository.automationEnabled.first()
            val emergencyStop = app.settingsRepository.emergencyStop.first()
            val swipeDir = app.settingsRepository.swipeDirection.first()
            val distMode = app.settingsRepository.pricingDistanceMode.first()
            val zoneMode = app.settingsRepository.zoneVerificationMode.first()
            val bands = app.pricingRepository.getBandsDirect(AppTarget.INDRIVE)
            val zones = app.zoneRepository.getEnabledZones()
            val negConfig = app.settingsRepository.negotiationConfig.first()

            stateMachine.processScreen(
                parsed = parsedScreen,
                automationEnabled = automationEnabled,
                emergencyStop = emergencyStop,
                swipeDirection = swipeDir,
                pricingDistanceMode = distMode,
                zoneVerificationMode = zoneMode,
                bands = bands,
                zones = zones,
                negotiationConfig = negConfig,
                context = applicationContext
            )

            // Start overlay if enabled
            val overlayIntent = Intent(applicationContext, RidePilotOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent)
            } else {
                startService(overlayIntent)
            }
        }
    }

    private suspend fun executeGesture(action: InDriveStateMachine.GestureAction): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        return suspendCancellableCoroutine<Boolean> { cont ->
            when (action) {
                is InDriveStateMachine.GestureAction.Tap -> {
                    val path = Path().apply {
                        moveTo(action.x, action.y)
                    }
                    val stroke = GestureDescription.StrokeDescription(path, 0, 100)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()

                    dispatchGesture(gesture, object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            cont.resume(true)
                        }
                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            cont.resume(false)
                        }
                    }, null)
                }

                is InDriveStateMachine.GestureAction.Swipe -> {
                    val path = Path().apply {
                        moveTo(action.startX, action.startY)
                        lineTo(action.endX, action.endY)
                    }
                    val stroke = GestureDescription.StrokeDescription(path, 0, action.durationMs)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()

                    dispatchGesture(gesture, object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            cont.resume(true)
                        }
                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            cont.resume(false)
                        }
                    }, null)
                }

                is InDriveStateMachine.GestureAction.Back -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    cont.resume(true)
                }
            }
        }
    }
}
