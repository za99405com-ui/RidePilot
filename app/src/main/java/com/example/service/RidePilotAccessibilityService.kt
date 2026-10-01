package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.RidePilotApplication
import com.example.data.model.AppLogEntry
import com.example.data.model.AppTarget
import com.example.data.model.AutomationState
import com.example.data.model.InDriveMapPreview
import com.example.data.model.PricingDistanceMode
import com.example.data.model.RideAnalysis
import com.example.data.model.RideOffer
import com.example.domain.engine.InDriveRoutePreviewResolver
import com.example.domain.engine.PricingEngine
import com.example.domain.engine.ZoneEngine
import com.example.domain.parser.InDriveParser
import com.example.domain.parser.UberParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.suspendCancellableCoroutine

class RidePilotAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RidePilotAccService"
        private const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        private const val INDRIVE_PACKAGE = "sinet.startup.inDriver"

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _latestUberAnalysis = MutableStateFlow<RideAnalysis?>(null)
        val latestUberAnalysis: StateFlow<RideAnalysis?> = _latestUberAnalysis.asStateFlow()

        private val _latestInDriveParsed = MutableStateFlow<InDriveParser.InDriveParsedScreen?>(null)
        val latestInDriveParsed: StateFlow<InDriveParser.InDriveParsedScreen?> = _latestInDriveParsed.asStateFlow()

        private val _latestInDriveMapPreview = MutableStateFlow<InDriveMapPreview?>(null)
        val latestInDriveMapPreview: StateFlow<InDriveMapPreview?> = _latestInDriveMapPreview.asStateFlow()

        private val _liveCalibrationOffer = MutableStateFlow<RideOffer?>(null)
        val liveCalibrationOffer: StateFlow<RideOffer?> = _liveCalibrationOffer.asStateFlow()

        private val _activeTarget = MutableStateFlow<AppTarget?>(null)
        val activeTarget: StateFlow<AppTarget?> = _activeTarget.asStateFlow()

        private val _automationStatus = MutableStateFlow("جاهز")
        val automationStatus: StateFlow<String> = _automationStatus.asStateFlow()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lastEventByPackage = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val inDriveEventDebounceMs = 250L
    private val uberEventDebounceMs = 80L
    private val processing = AtomicBoolean(false)
    private val pendingRecheck = AtomicBoolean(false)
    private val passiveRecheckScheduled = AtomicBoolean(false)
    private val postGestureRecheckToken = AtomicInteger(0)
    private val uberLockActive = AtomicBoolean(false)
    private val forceInDriveCycle = AtomicBoolean(false)
    private var lastRequestsAutomationAt = 0L
    private var lastMapPreviewKey: String? = null
    private var mapPreviewJob: Job? = null
    private var passiveRecheckJob: Job? = null

    private val stateMachine by lazy {
        InDriveStateMachine(
            gestureDispatcher = { action -> executeGesture(action) },
            logger = { action, reason, price, dist, conf ->
                _automationStatus.value = reason
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
        serviceScope.launch {
            RidePilotApplication.instance.settingsRepository.applyV3AutomationDefaultsOnce()
            delay(120)
            requestWindowProcessing()
        }

        // Runtime state changes must take effect immediately even when neither
        // rideshare app emits a new AccessibilityEvent (Pause/Resume/STOP).
        serviceScope.launch {
            val settings = RidePilotApplication.instance.settingsRepository
            combine(
                settings.automationEnabled,
                settings.emergencyStop,
                settings.overlayEnabled
            ) { enabled, stopped, overlay ->
                Triple(enabled, stopped, overlay)
            }
                .distinctUntilChanged()
                .collect {
                    requestWindowProcessing()
                }
        }
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
        if (packageName != UBER_DRIVER_PACKAGE && packageName != INDRIVE_PACKAGE) return

        val now = System.currentTimeMillis()
        val last = lastEventByPackage[packageName] ?: 0L
        val debounceMs =
            if (packageName == UBER_DRIVER_PACKAGE) uberEventDebounceMs
            else inDriveEventDebounceMs
        if (now - last < debounceMs) return
        lastEventByPackage[packageName] = now

        // Conflate rapid Accessibility events instead of dropping a screen transition.
        // This is important when a tap opens the custom-offer editor immediately.
        requestWindowProcessing()
    }

    private fun requestWindowProcessing() {
        pendingRecheck.set(true)
        if (!processing.compareAndSet(false, true)) return

        serviceScope.launch {
            try {
                var firstPass = true
                while (firstPass || pendingRecheck.getAndSet(false)) {
                    firstPass = false
                    try {
                        handleRelevantWindows()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling ride windows", e)
                        _automationStatus.value = "خطأ في القراءة"
                    }

                    // Accessibility animations can emit events continuously.
                    // Yield between conflated passes instead of spinning at full speed.
                    if (pendingRecheck.get()) {
                        delay(120)
                    }
                }
            } finally {
                processing.set(false)
                // Close the tiny race where a new event arrives just before releasing.
                if (pendingRecheck.get()) {
                    requestWindowProcessing()
                }
            }
        }
    }

    private fun cancelTransientWork() {
        mapPreviewJob?.cancel()
        mapPreviewJob = null
        lastMapPreviewKey = null

        passiveRecheckJob?.cancel()
        passiveRecheckJob = null
        passiveRecheckScheduled.set(false)

        postGestureRecheckToken.incrementAndGet()
        pendingRecheck.set(false)
    }

    private suspend fun handleRelevantWindows() {
        val app = RidePilotApplication.instance

        // Runtime mode is checked before touching Uber or inDrive. HARD STOP is
        // terminal for the floating controller: Accessibility may stay connected
        // at the Android level, but RidePilot does not read/analyse/control rides
        // and cannot recreate the overlay until the app explicitly starts again.
        val emergencyStop = app.settingsRepository.emergencyStop.first()
        val automationEnabled = app.settingsRepository.automationEnabled.first()
        val overlayEnabled = app.settingsRepository.overlayEnabled.first()

        if (emergencyStop || !overlayEnabled) {
            cancelTransientWork()
            stateMachine.reset()
            lastRequestsAutomationAt = 0L
            _activeTarget.value = null
            _latestUberAnalysis.value = null
            _latestInDriveParsed.value = null
            _latestInDriveMapPreview.value = null
            _liveCalibrationOffer.value = null
            _automationStatus.value = "STOP — التشغيل من تطبيق RidePilot فقط"
            if (RidePilotOverlayService.isOverlayRunning) {
                stopService(Intent(applicationContext, RidePilotOverlayService::class.java))
            }
            return
        }

        // Temporary pause keeps the floating control available for Resume, but
        // performs no ride analysis and dispatches no gesture.
        if (!automationEnabled) {
            cancelTransientWork()
            stateMachine.reset()
            lastRequestsAutomationAt = 0L
            _activeTarget.value = null
            _latestUberAnalysis.value = null
            _latestInDriveParsed.value = null
            _latestInDriveMapPreview.value = null
            _liveCalibrationOffer.value = null
            _automationStatus.value = "متوقف مؤقتًا"
            ensureOverlayRunning()
            return
        }

        // Uber cards can appear as a floating window over inDrive. Always inspect Uber
        // first; while an Uber offer is visible we only analyse it and avoid tapping the
        // inDrive window underneath.
        val uberRoots = findRootsForPackage(UBER_DRIVER_PACKAGE)
        uberLockActive.set(uberRoots.isNotEmpty())
        var sawUberContent = false

        for (uberRoot in uberRoots) {
            val nodes = UberParser.extractNodes(uberRoot)
            if (nodes.isNotEmpty()) sawUberContent = true

            val offer = UberParser.parseUberScreen(nodes)
            if (offer != null) {
                _activeTarget.value = AppTarget.UBER
                processUberOffer(app, offer)
                ensureOverlayRunning()
                return
            }
        }

        // If any Uber window is already above inDrive but its text is still
        // animating/loading, never fall through and tap inDrive underneath it.
        if (sawUberContent) {
            _activeTarget.value = AppTarget.UBER
            _latestUberAnalysis.value = null
            _automationStatus.value = "Uber: جاري قراءة الطلب"
            ensureOverlayRunning()
            schedulePassiveRecheck(140L)
            return
        }

        uberLockActive.set(false)

        val inDriveRoot = findRootForPackage(INDRIVE_PACKAGE) ?: return
        _activeTarget.value = AppTarget.INDRIVE

        val parsedScreen = InDriveParser.parseScreen(inDriveRoot)
        _latestInDriveParsed.value = sanitizeForUi(parsedScreen)
        if (parsedScreen.activeOffer != null) {
            _liveCalibrationOffer.value = parsedScreen.activeOffer
        }

        val forcedCycle = forceInDriveCycle.getAndSet(false)
        if (parsedScreen.screenType == InDriveParser.InDriveScreenType.REQUESTS_LIST) {
            val now = System.currentTimeMillis()
            if (!forcedCycle && now - lastRequestsAutomationAt < 10_000L) {
                ensureOverlayRunning()
                return
            }
            lastRequestsAutomationAt = now
        } else {
            // Screen transitions/details are handled immediately and do not consume
            // the 10-second list refresh window.
            lastRequestsAutomationAt = 0L
        }

        val swipeDir = app.settingsRepository.swipeDirection.first()
        val distMode = app.settingsRepository.pricingDistanceMode.first()
        val zoneMode = app.settingsRepository.zoneVerificationMode.first()
        val maxPickupDistanceKm = app.settingsRepository.maxPickupDistanceKm.first()

        updateInDriveMapPreview(parsedScreen, maxPickupDistanceKm)

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
            maxPickupDistanceKm = maxPickupDistanceKm,
            bands = bands,
            zones = zones,
            negotiationConfig = negConfig,
            context = applicationContext
        )

        ensureOverlayRunning()

        // Accessibility events are not guaranteed for every inDrive animation.
        // Keep a lightweight read loop only while automation is active and inDrive
        // is visible so the next state is picked up without the user touching the app.
        if (automationEnabled) {
            val nextDelay = when (parsedScreen.screenType) {
                InDriveParser.InDriveScreenType.REQUESTS_LIST -> 10_000L
                InDriveParser.InDriveScreenType.ORDER_DETAILS -> 450L
                InDriveParser.InDriveScreenType.WAITING_RESPONSE -> 650L
                InDriveParser.InDriveScreenType.COUNTER_OFFER_INPUT -> 220L
                InDriveParser.InDriveScreenType.UNKNOWN -> 1_500L
            }
            schedulePassiveRecheck(nextDelay)
        }
    }

    private fun updateInDriveMapPreview(
        parsed: InDriveParser.InDriveParsedScreen,
        maxPickupDistanceKm: Double
    ) {
        if (parsed.screenType != InDriveParser.InDriveScreenType.REQUESTS_LIST) return

        val card = parsed.orderCards.firstOrNull { order ->
            val pickupKm = order.distanceKm
            !order.pickupAddress.isNullOrBlank() &&
                !order.destinationAddress.isNullOrBlank() &&
                pickupKm != null &&
                pickupKm <= maxPickupDistanceKm
        }

        if (card == null) {
            lastMapPreviewKey = null
            _latestInDriveMapPreview.value = null
            return
        }

        val pickup = card.pickupAddress ?: return
        val destination = card.destinationAddress ?: return
        val key = listOf(
            pickup,
            destination,
            card.distanceKm?.toString().orEmpty(),
            card.priceEgp?.toString().orEmpty()
        ).joinToString("|")

        if (key == lastMapPreviewKey) return
        lastMapPreviewKey = key

        mapPreviewJob?.cancel()
        mapPreviewJob = serviceScope.launch {
            val resolved = InDriveRoutePreviewResolver.resolve(
                context = applicationContext,
                pickupAddress = pickup,
                destinationAddress = destination
            )

            if (key != lastMapPreviewKey) return@launch

            _latestInDriveMapPreview.value = resolved?.let {
                InDriveMapPreview(
                    pickupAddress = pickup,
                    destinationAddress = destination,
                    pickupPoint = it.pickupPoint,
                    destinationPoint = it.destinationPoint,
                    routeDistanceKm = it.routeDistanceKm,
                    pickupDistanceKm = card.distanceKm,
                    displayedPrice = card.priceEgp
                )
            }
        }
    }

    private fun sanitizeForUi(
        parsed: InDriveParser.InDriveParsedScreen
    ): InDriveParser.InDriveParsedScreen =
        parsed.copy(
            orderCards = parsed.orderCards.map { it.copy(node = null) },
            customOfferInputNode = null
        )

    private suspend fun processUberOffer(app: RidePilotApplication, offer: RideOffer) {
        _liveCalibrationOffer.value = offer

        val bands = app.pricingRepository.getBandsDirect(AppTarget.UBER)
        val zones = app.zoneRepository.getEnabledZones()
        val distMode = app.settingsRepository.pricingDistanceMode.first()
        val zoneMode = app.settingsRepository.zoneVerificationMode.first()

        val pricingDistance: Double? = when (distMode) {
            PricingDistanceMode.TRIP_ONLY -> offer.tripDistanceKm
            PricingDistanceMode.PICKUP_PLUS_TRIP -> {
                val pickup = offer.pickupDistanceKm
                val trip = offer.tripDistanceKm
                if (pickup != null && trip != null) pickup + trip else null
            }
        }

        val displayedPrice = offer.displayedPrice
        if (pricingDistance == null || pricingDistance <= 0.0 || displayedPrice == null || displayedPrice <= 0.0) {
            _latestUberAnalysis.value = null
            _automationStatus.value = "Uber: بيانات غير مكتملة"
            return
        }

        val calc = PricingEngine.calculateMinimumPrice(pricingDistance, bands)
        if (!calc.isValid) {
            _latestUberAnalysis.value = null
            _automationStatus.value = "Uber: راجع جدول التسعير"
            return
        }

        val zoneRes = ZoneEngine.evaluateOffer(
            context = applicationContext,
            pickupAddress = offer.pickupAddress,
            destinationAddress = offer.destinationAddress,
            zones = zones,
            mode = zoneMode
        )

        if (!zoneRes.isConclusive) {
            _latestUberAnalysis.value = null
            _automationStatus.value = "Uber: تعذر التحقق من المنطقة"
            return
        }

        val isPriceViable = displayedPrice >= calc.minimumPrice
        val analysis = RideAnalysis(
            offer = offer,
            totalPricingDistanceKm = pricingDistance,
            applicableBand = calc.matchedBand,
            minRequiredPrice = calc.minimumPrice,
            pricePerKm = PricingEngine.roundToTwoDecimals(displayedPrice / pricingDistance),
            isPriceViable = isPriceViable,
            isInsideZone = zoneRes.isAllowed,
            zoneStatusReason = zoneRes.reason,
            pricingReason = calc.explanation,
            actionRecommended = if (isPriceViable && zoneRes.isAllowed) "✅ مناسب" else "❌ غير مناسب"
        )

        _latestUberAnalysis.value = analysis
        _automationStatus.value = if (isPriceViable && zoneRes.isAllowed) {
            "Uber: مناسب"
        } else {
            "Uber: غير مناسب"
        }

        app.logRepository.log(
            AppLogEntry(
                app = AppTarget.UBER,
                screen = "OfferOverlay",
                detectedPrice = displayedPrice,
                detectedDistance = pricingDistance,
                zoneResult = zoneRes.reason,
                minCalculated = calc.minimumPrice,
                action = "DISPLAY_OVERLAY",
                reason = _automationStatus.value,
                confidence = offer.confidence
            )
        )
    }

    private fun ensureOverlayRunning() {
        if (RidePilotOverlayService.isOverlayRunning) return
        val overlayIntent = Intent(applicationContext, RidePilotOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(overlayIntent)
        } else {
            startService(overlayIntent)
        }
    }

    private fun findRootsForPackage(packageName: String): List<AccessibilityNodeInfo> {
        val matching = windows
            .asSequence()
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() == packageName }
            .toMutableList()

        val activeRoot = rootInActiveWindow
        if (
            activeRoot?.packageName?.toString() == packageName &&
            matching.none { it === activeRoot }
        ) {
            matching.add(activeRoot)
        }

        return matching
    }

    private fun findRootForPackage(packageName: String): AccessibilityNodeInfo? =
        findRootsForPackage(packageName).firstOrNull()

    private fun schedulePassiveRecheck(delayMs: Long) {
        if (!passiveRecheckScheduled.compareAndSet(false, true)) return

        passiveRecheckJob = serviceScope.launch {
            try {
                delay(delayMs)
                requestWindowProcessing()
            } finally {
                passiveRecheckScheduled.set(false)
                passiveRecheckJob = null
            }
        }
    }

    private fun schedulePostGestureRechecks() {
        val token = postGestureRecheckToken.incrementAndGet()
        serviceScope.launch {
            delay(90)
            if (postGestureRecheckToken.get() != token) return@launch
            forceInDriveCycle.set(true)
            requestWindowProcessing()

            delay(160)
            if (postGestureRecheckToken.get() != token) return@launch
            forceInDriveCycle.set(true)
            requestWindowProcessing()

            delay(300)
            if (postGestureRecheckToken.get() != token) return@launch
            forceInDriveCycle.set(true)
            requestWindowProcessing()
        }
    }

    private suspend fun executeGesture(action: InDriveStateMachine.GestureAction): Boolean {
        // Final safety gate immediately before every automated action.
        val settings = RidePilotApplication.instance.settingsRepository
        if (
            settings.emergencyStop.first() ||
            !settings.automationEnabled.first() ||
            uberLockActive.get()
        ) {
            return false
        }

        return withContext(Dispatchers.Main.immediate) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return@withContext false
            }

            suspendCancellableCoroutine { cont ->
                when (action) {
                    is InDriveStateMachine.GestureAction.Tap -> {
                        val path = Path().apply { moveTo(action.x, action.y) }
                        val stroke = GestureDescription.StrokeDescription(path, 0, 55)
                        val gesture = GestureDescription.Builder().addStroke(stroke).build()
                        dispatchGesture(gesture, object : GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription?) {
                                schedulePostGestureRechecks()
                                if (cont.isActive) cont.resume(true)
                            }
                            override fun onCancelled(gestureDescription: GestureDescription?) {
                                if (cont.isActive) cont.resume(false)
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
                                schedulePostGestureRechecks()
                                if (cont.isActive) cont.resume(true)
                            }
                            override fun onCancelled(gestureDescription: GestureDescription?) {
                                if (cont.isActive) cont.resume(false)
                            }
                        }, null)
                    }

                    is InDriveStateMachine.GestureAction.ClickNode -> {
                        var current: AccessibilityNodeInfo? = action.node
                        var clicked = false
                        var hops = 0
                        while (current != null && hops < 5 && !clicked) {
                            clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            current = if (!clicked) current.parent else null
                            hops++
                        }
                        if (clicked) schedulePostGestureRechecks()
                        if (cont.isActive) cont.resume(clicked)
                    }

                    is InDriveStateMachine.GestureAction.SetText -> {
                        val args = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                action.text
                            )
                        }
                        val ok = action.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        if (ok) schedulePostGestureRechecks()
                        if (cont.isActive) cont.resume(ok)
                    }

                    is InDriveStateMachine.GestureAction.Back -> {
                        val ok = performGlobalAction(GLOBAL_ACTION_BACK)
                        if (ok) schedulePostGestureRechecks()
                        if (cont.isActive) cont.resume(ok)
                    }
                }
            }
        }
    }

}
