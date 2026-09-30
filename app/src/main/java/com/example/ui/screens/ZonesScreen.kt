package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.RidePilotApplication
import com.example.data.model.LatLngPoint
import com.example.data.model.WorkZone
import com.example.domain.engine.AlexandriaZoneCatalog
import com.example.domain.engine.AreaBoundaryResolver
import com.example.domain.engine.ZoneEngine
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BorderDark
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCard
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

private enum class ZoneMapMode {
    BROWSE,
    RECOGNIZE,
    MANUAL,
    EDIT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonesScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = RidePilotApplication.instance
    val zones by app.zoneRepository.getAllZones().collectAsState(initial = emptyList())

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var mapMode by remember { mutableStateOf(ZoneMapMode.BROWSE) }

    val previewGroups = remember { mutableStateListOf<List<GeoPoint>>() }
    val manualPoints = remember { mutableStateListOf<GeoPoint>() }
    val selectedNames = remember { mutableStateListOf<String>() }
    val selectedAreaIds = remember { mutableStateListOf<String>() }
    val selectedAreaPolygons =
        remember { mutableStateMapOf<String, List<List<GeoPoint>>>() }

    var editingGroupIndex by remember { mutableStateOf(0) }
    var editingExistingZone by remember { mutableStateOf<WorkZone?>(null) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var zoneNameInput by remember { mutableStateOf("") }
    var zoneKeywordsInput by remember { mutableStateOf("") }

    var isRecognizing by remember { mutableStateOf(false) }
    var mapMessage by remember { mutableStateOf("اختر اسم المنطقة من القائمة وسيتم تحديدها فورًا") }

    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var locateAfterPermission by remember { mutableStateOf(false) }
    var recognizeCurrentAfterLocate by remember { mutableStateOf(false) }

    var radiusKm by remember { mutableStateOf(3f) }
    var showRadiusTools by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val fusedLocationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun resetDraft() {
        previewGroups.clear()
        manualPoints.clear()
        selectedNames.clear()
        selectedAreaIds.clear()
        selectedAreaPolygons.clear()
        editingExistingZone = null
        editingGroupIndex = 0
        zoneNameInput = ""
        zoneKeywordsInput = ""
        mapMode = ZoneMapMode.BROWSE
    }

    fun previewAsModel(): List<List<LatLngPoint>> =
        previewGroups.map { group ->
            group.map { LatLngPoint(it.latitude, it.longitude) }
        }

    fun focusGroups(groups: List<List<GeoPoint>>, preferredZoom: Double = 13.5) {
        val all = groups.flatten()
        if (all.isEmpty()) return
        val center = GeoPoint(
            all.map { it.latitude }.average(),
            all.map { it.longitude }.average()
        )
        mapViewInstance?.controller?.animateTo(center)
        mapViewInstance?.controller?.setZoom(preferredZoom)
    }

    fun renderMap(mapView: MapView?) {
        if (mapView == null) return

        mapView.overlays.removeAll {
            it is Polygon || it is Polyline || it is Marker
        }

        zones.forEach { zone ->
            if (editingExistingZone?.id == zone.id) return@forEach

            ZoneEngine.parsePolygonGroups(zone.polygonJson).forEach { group ->
                if (group.size < 3) return@forEach

                mapView.overlays.add(
                    Polygon(mapView).apply {
                        points = group.map { GeoPoint(it.latitude, it.longitude) }
                        title = zone.name
                        fillPaint.color = if (zone.isEnabled) 0x1F50D6A0 else 0x1275849A
                        outlinePaint.color =
                            if (zone.isEnabled) 0xFF50D6A0.toInt() else 0xFF75849A.toInt()
                        outlinePaint.strokeWidth = if (zone.isEnabled) 4f else 2.5f
                    }
                )
            }
        }

        previewGroups.forEachIndexed { index, group ->
            if (group.size < 3) return@forEachIndexed
            mapView.overlays.add(
                Polygon(mapView).apply {
                    points = group
                    title = selectedNames.getOrNull(index) ?: "Zone جديدة"
                    fillPaint.color = 0x3363A7FF
                    outlinePaint.color = 0xFF63A7FF.toInt()
                    outlinePaint.strokeWidth = 6f
                }
            )
        }

        if (mapMode == ZoneMapMode.MANUAL && manualPoints.isNotEmpty()) {
            mapView.overlays.add(
                Polyline(mapView).apply {
                    setPoints(manualPoints.toList())
                    outlinePaint.color = 0xFFFFB44D.toInt()
                    outlinePaint.strokeWidth = 7f
                }
            )
        }

        currentLocation?.let { point ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = point
                    title = "موقعك الحالي"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
            )
        }

        if (mapMode == ZoneMapMode.EDIT && previewGroups.isNotEmpty()) {
            val groupIndex = editingGroupIndex.coerceIn(0, previewGroups.lastIndex)
            val editable = previewGroups[groupIndex]

            editable.forEachIndexed { pointIndex, point ->
                val marker = Marker(mapView).apply {
                    position = point
                    title = "نقطة ${pointIndex + 1}"
                    isDraggable = true
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }

                marker.setOnMarkerDragListener(
                    object : Marker.OnMarkerDragListener {
                        override fun onMarkerDrag(marker: Marker?) = Unit
                        override fun onMarkerDragStart(marker: Marker?) = Unit

                        override fun onMarkerDragEnd(marker: Marker?) {
                            val newPosition = marker?.position ?: return
                            if (groupIndex !in previewGroups.indices) return
                            val updated = previewGroups[groupIndex].toMutableList()
                            if (pointIndex !in updated.indices) return
                            updated[pointIndex] = GeoPoint(
                                newPosition.latitude,
                                newPosition.longitude
                            )
                            previewGroups[groupIndex] = updated
                            renderMap(mapView)
                        }
                    }
                )

                mapView.overlays.add(marker)
            }
        }

        mapView.invalidate()
    }

    fun focusZone(zone: WorkZone) {
        val groups = ZoneEngine.parsePolygonGroups(zone.polygonJson)
            .map { group -> group.map { GeoPoint(it.latitude, it.longitude) } }
        focusGroups(groups, 13.5)
    }

    fun updateAutoName() {
        if (selectedNames.isNotEmpty()) {
            zoneNameInput = selectedNames.distinct().joinToString(" + ")
            zoneKeywordsInput = selectedNames.distinct().joinToString(",")
        } else {
            zoneNameInput = ""
            zoneKeywordsInput = ""
        }
    }

    fun rebuildPresetPreview() {
        previewGroups.clear()
        selectedAreaIds.forEach { id ->
            selectedAreaPolygons[id]?.let { groups ->
                previewGroups.addAll(groups)
            }
        }
    }

    fun togglePreset(area: AlexandriaZoneCatalog.AreaPreset) {
        val existingIndex = selectedAreaIds.indexOf(area.id)

        if (existingIndex >= 0) {
            selectedAreaIds.removeAt(existingIndex)
            if (existingIndex in selectedNames.indices) selectedNames.removeAt(existingIndex)
            selectedAreaPolygons.remove(area.id)
            rebuildPresetPreview()
            updateAutoName()
            mapMessage = "تم إلغاء «${area.name}»"
            renderMap(mapViewInstance)
            return
        }

        area.fixedPolygon?.let { fixed ->
            val groups = listOf(
                fixed.map { GeoPoint(it.latitude, it.longitude) }
            )
            selectedAreaIds.add(area.id)
            selectedNames.add(area.name)
            selectedAreaPolygons[area.id] = groups
            rebuildPresetPreview()
            updateAutoName()
            mapMode = ZoneMapMode.BROWSE
            mapMessage = "تم تطبيق «${area.name}» بالإحداثيات التي أرسلتها"
            renderMap(mapViewInstance)
            focusGroups(groups, 12.8)
            return
        }

        if (isRecognizing) return

        isRecognizing = true
        mapMessage = "جار تحميل الحدود الحقيقية لـ «${area.name}»…"

        scope.launch {
            AreaBoundaryResolver.resolveByName(area.searchQuery)
                .onSuccess { resolved ->
                    val groups = resolved.polygons.map { polygon ->
                        polygon.map { GeoPoint(it.latitude, it.longitude) }
                    }

                    if (groups.isEmpty()) {
                        mapMessage = "لا توجد حدود Polygon متاحة لـ «${area.name}»"
                    } else {
                        selectedAreaIds.add(area.id)
                        selectedNames.add(area.name)
                        selectedAreaPolygons[area.id] = groups
                        rebuildPresetPreview()
                        updateAutoName()
                        mapMode = ZoneMapMode.BROWSE
                        mapMessage =
                            "تم تحميل الحدود الحقيقية لـ «${area.name}» • يمكنك الدمج أو التعديل"
                        renderMap(mapViewInstance)
                        focusGroups(groups, 14.0)
                    }
                }
                .onFailure {
                    mapMessage =
                        "تعذر تحميل حدود «${area.name}» • لن يتم رسم مستطيل بديل"
                }

            isRecognizing = false
        }
    }

    suspend fun recognizeAt(point: GeoPoint) {
        if (isRecognizing) return
        isRecognizing = true
        mapMessage = "جار التعرف على المنطقة وحدودها…"

        val result = AreaBoundaryResolver.resolve(point.latitude, point.longitude)
        result.onSuccess { area ->
            if (area.osmKey in selectedAreaIds) {
                mapMessage = "منطقة «${area.name}» محددة بالفعل"
                return@onSuccess
            }

            val groups = area.polygons.map { polygon ->
                polygon.map { GeoPoint(it.latitude, it.longitude) }
            }

            if (groups.isEmpty()) {
                mapMessage = "تم التعرف على الاسم لكن لم نجد حدودًا قابلة للرسم"
                return@onSuccess
            }

            selectedAreaIds.add(area.osmKey)
            selectedNames.add(area.name)
            previewGroups.addAll(groups)
            updateAutoName()
            mapMessage = "تم تحديد «${area.name}» • اختر منطقة أخرى أو اضغط دمج"
            renderMap(mapViewInstance)
            focusGroups(groups, 13.8)
        }.onFailure {
            mapMessage = "تعذر تحديد حدود المنطقة • جرّب نقطة أقرب لمنتصف المنطقة"
        }

        isRecognizing = false
    }

    @SuppressLint("MissingPermission")
    fun locateNow() {
        if (!hasLocationPermission) return

        isLocating = true
        mapMessage = "جار تحديد موقعك…"
        val token = CancellationTokenSource()

        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = GeoPoint(location.latitude, location.longitude)
                    isLocating = false
                    mapViewInstance?.controller?.animateTo(currentLocation)
                    mapViewInstance?.controller?.setZoom(16.0)
                    renderMap(mapViewInstance)
                    mapMessage = "تم تحديد موقعك"

                    if (recognizeCurrentAfterLocate) {
                        recognizeCurrentAfterLocate = false
                        scope.launch { recognizeAt(currentLocation!!) }
                    }
                } else {
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { last ->
                            isLocating = false
                            if (last != null) {
                                currentLocation = GeoPoint(last.latitude, last.longitude)
                                renderMap(mapViewInstance)
                                mapMessage = "تم استخدام آخر موقع معروف"
                                if (recognizeCurrentAfterLocate) {
                                    recognizeCurrentAfterLocate = false
                                    scope.launch { recognizeAt(currentLocation!!) }
                                }
                            } else {
                                mapMessage = "تعذر تحديد الموقع — فعّل GPS"
                            }
                        }
                        .addOnFailureListener {
                            isLocating = false
                            mapMessage = "تعذر قراءة الموقع"
                        }
                }
            }
            .addOnFailureListener {
                isLocating = false
                mapMessage = "تعذر تحديد الموقع"
            }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (hasLocationPermission && locateAfterPermission) {
            locateAfterPermission = false
            locateNow()
        } else if (!hasLocationPermission) {
            locateAfterPermission = false
            recognizeCurrentAfterLocate = false
            mapMessage = "إذن الموقع مطلوب لاختيار منطقتك تلقائيًا"
        }
    }

    fun requestLocation(recognizeAreaAfter: Boolean = false) {
        recognizeCurrentAfterLocate = recognizeAreaAfter
        if (hasLocationPermission) {
            locateNow()
        } else {
            locateAfterPermission = true
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun recognizeCurrentArea() {
        val point = currentLocation
        if (point != null) {
            scope.launch { recognizeAt(point) }
        } else {
            requestLocation(recognizeAreaAfter = true)
        }
    }

    fun mergeSelectedAreas() {
        val groups = previewAsModel()
        if (groups.size < 2) {
            mapMessage = "اختر منطقتين أو أكثر قبل الدمج"
            return
        }

        val merged = ZoneEngine.mergePolygonGroups(groups)
        if (merged.isEmpty()) {
            mapMessage = "تعذر دمج الحدود"
            return
        }

        previewGroups.clear()
        previewGroups.addAll(
            merged.map { group ->
                group.map { GeoPoint(it.latitude, it.longitude) }
            }
        )
        editingGroupIndex = 0
        mapMode = ZoneMapMode.BROWSE
        mapMessage = if (previewGroups.size == 1) {
            "تم دمج المناطق في حد واحد"
        } else {
            "تم دمج المناطق كـ Zone واحدة من ${previewGroups.size} أجزاء"
        }
        renderMap(mapViewInstance)
        focusGroups(previewGroups.toList(), 13.2)
    }

    fun createRadiusZone() {
        val point = currentLocation
        if (point == null) {
            mapMessage = "حدد موقعك أولًا"
            requestLocation()
            return
        }

        val circle = ZoneEngine.createCirclePolygon(
            LatLngPoint(point.latitude, point.longitude),
            radiusKm.toDouble(),
            segments = 48
        )

        resetDraft()
        selectedNames.add(
            String.format(Locale.US, "حول موقعي %.1f كم", radiusKm)
        )
        previewGroups.add(circle.map { GeoPoint(it.latitude, it.longitude) })
        updateAutoName()
        mapMessage = "تم إنشاء Zone دائري احتياطي"
        renderMap(mapViewInstance)
        focusGroups(previewGroups.toList(), 14.0)
    }

    fun startEditingPreview() {
        if (previewGroups.isEmpty()) {
            mapMessage = "حدد منطقة أولًا"
            return
        }
        editingGroupIndex = editingGroupIndex.coerceIn(0, previewGroups.lastIndex)
        mapMode = ZoneMapMode.EDIT
        mapMessage = "اسحب نقاط الحدود لتعديلها ثم اضغط إنهاء التعديل"
        renderMap(mapViewInstance)
    }

    fun loadZoneForEditing(zone: WorkZone) {
        resetDraft()
        editingExistingZone = zone
        zoneNameInput = zone.name
        zoneKeywordsInput = zone.allowedKeywords

        val groups = ZoneEngine.parsePolygonGroups(zone.polygonJson)
            .map { group -> group.map { GeoPoint(it.latitude, it.longitude) } }

        previewGroups.addAll(groups)
        selectedNames.add(zone.name)
        mapMode = ZoneMapMode.EDIT
        mapMessage = "تعديل «${zone.name}» • اسحب النقاط ثم احفظ"
        focusGroups(groups, 13.8)
        renderMap(mapViewInstance)
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            requestLocation()
        }
    }

    LaunchedEffect(zones) {
        renderMap(mapViewInstance)
    }

    DisposableEffect(mapViewInstance) {
        mapViewInstance?.onResume()
        onDispose { mapViewInstance?.onPause() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("مناطق العمل", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("تحديد ذكي • تعديل • دمج", color = TextMuted, fontSize = 10.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            resetDraft()
                            mapMessage = "تم مسح التحديد الحالي"
                            renderMap(mapViewInstance)
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "مسح التحديد",
                            tint = AmberAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            if (previewGroups.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showSaveDialog = true },
                    containerColor = EmeraldPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "حفظ Zone")
                }
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    Text(
                        "مناطق الإسكندرية",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "اضغط اسم المنطقة لتحديدها • اضغط مرة أخرى لإلغائها",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(
                            AlexandriaZoneCatalog.areas,
                            key = { it.id }
                        ) { area ->
                            val selected = area.id in selectedAreaIds
                            if (selected) {
                                Button(
                                    onClick = { togglePreset(area) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldPrimary
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        area.name,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { togglePreset(area) },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        area.name,
                                        color = TextPrimary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(285.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(18.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            setBuiltInZoomControls(false)
                            minZoomLevel = 10.5
                            maxZoomLevel = 19.0
                            setScrollableAreaLimitDouble(
                                BoundingBox(
                                    AlexandriaZoneCatalog.alexandriaNorth,
                                    AlexandriaZoneCatalog.alexandriaEast,
                                    AlexandriaZoneCatalog.alexandriaSouth,
                                    AlexandriaZoneCatalog.alexandriaWest
                                )
                            )
                            controller.setZoom(11.7)
                            controller.setCenter(GeoPoint(31.2001, 29.9187))

                            val touchOverlay = object : org.osmdroid.views.overlay.Overlay() {
                                override fun onSingleTapConfirmed(
                                    event: MotionEvent?,
                                    mapView: MapView?
                                ): Boolean {
                                    if (event == null || mapView == null) return false

                                    val point = mapView.projection.fromPixels(
                                        event.x.toInt(),
                                        event.y.toInt()
                                    ) as GeoPoint

                                    return when (mapMode) {
                                        ZoneMapMode.RECOGNIZE -> {
                                            scope.launch { recognizeAt(point) }
                                            true
                                        }

                                        ZoneMapMode.MANUAL -> {
                                            manualPoints.add(point)
                                            renderMap(mapView)
                                            true
                                        }

                                        else -> false
                                    }
                                }
                            }

                            overlays.add(touchOverlay)
                            mapViewInstance = this
                            renderMap(this)
                        }
                    },
                    update = { map ->
                        mapViewInstance = map
                        renderMap(map)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkCard.copy(alpha = 0.95f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRecognizing || isLocating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = EmeraldPrimary
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(
                                        when (mapMode) {
                                            ZoneMapMode.RECOGNIZE -> EmeraldPrimary
                                            ZoneMapMode.MANUAL -> AmberAccent
                                            ZoneMapMode.EDIT -> SuccessGreen
                                            ZoneMapMode.BROWSE -> TextMuted
                                        },
                                        CircleShape
                                    )
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = mapMessage,
                            color = TextPrimary,
                            fontSize = 10.sp,
                            maxLines = 2
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { requestLocation() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkCard.copy(alpha = 0.95f)
                            ),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("موقعي", fontSize = 10.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                resetDraft()
                                mapMode = ZoneMapMode.MANUAL
                                mapMessage = "اضغط نقاط حدود المنطقة يدويًا"
                                renderMap(mapViewInstance)
                            },
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Text("رسم يدوي", fontSize = 10.sp, color = TextPrimary)
                        }

                        if (selectedAreaIds.size >= 2 || previewGroups.size >= 2) {
                            Button(
                                onClick = { mergeSelectedAreas() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldPrimary
                                ),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Text(
                                    "دمج",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (previewGroups.isNotEmpty() && mapMode != ZoneMapMode.MANUAL) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { startEditingPreview() },
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text("تعديل", fontSize = 10.sp, color = TextPrimary)
                            }

                            if (mapMode == ZoneMapMode.EDIT) {
                                Button(
                                    onClick = {
                                        mapMode = ZoneMapMode.BROWSE
                                        mapMessage = "تم إنهاء التعديل • راجع الحدود ثم احفظ"
                                        renderMap(mapViewInstance)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuccessGreen
                                    ),
                                    shape = RoundedCornerShape(13.dp)
                                ) {
                                    Text(
                                        "إنهاء",
                                        fontSize = 10.sp,
                                        color = DarkBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (previewGroups.size > 1) {
                                    TextButton(
                                        onClick = {
                                            editingGroupIndex =
                                                (editingGroupIndex + 1) % previewGroups.size
                                            mapMessage =
                                                "تعديل الجزء ${editingGroupIndex + 1} من ${previewGroups.size}"
                                            renderMap(mapViewInstance)
                                        }
                                    ) {
                                        Text(
                                            "${editingGroupIndex + 1}/${previewGroups.size}",
                                            color = AmberAccent,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (mapMode == ZoneMapMode.MANUAL) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (manualPoints.isNotEmpty()) {
                                        manualPoints.removeAt(manualPoints.lastIndex)
                                        renderMap(mapViewInstance)
                                    }
                                },
                                enabled = manualPoints.isNotEmpty(),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Text("تراجع", fontSize = 10.sp, color = TextPrimary)
                            }

                            Button(
                                onClick = {
                                    if (manualPoints.size >= 3) {
                                        previewGroups.clear()
                                        previewGroups.add(manualPoints.toList())
                                        manualPoints.clear()
                                        selectedNames.clear()
                                        selectedAreaIds.clear()
                                        zoneNameInput = "منطقة مخصصة"
                                        mapMode = ZoneMapMode.BROWSE
                                        mapMessage = "تم إنهاء الرسم اليدوي"
                                        renderMap(mapViewInstance)
                                    }
                                },
                                enabled = manualPoints.size >= 3,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldPrimary
                                ),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Text(
                                    "إنهاء الرسم",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "التحديد الذكي",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                if (selectedNames.isEmpty())
                                    "يتعرف على المنطقة وحدودها تلقائيًا من الخريطة"
                                else
                                    "المحدد: ${selectedNames.distinct().joinToString(" • ")}",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 2
                            )
                        }

                        TextButton(onClick = { showRadiusTools = !showRadiusTools }) {
                            Text(
                                if (showRadiusTools) "إخفاء الدائرة" else "تحديد بنصف قطر",
                                color = AmberAccent,
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (showRadiusTools) {
                        Spacer(Modifier.height(7.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Slider(
                                value = radiusKm,
                                onValueChange = { radiusKm = it },
                                valueRange = 0.5f..20f,
                                steps = 38,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = EmeraldPrimary,
                                    activeTrackColor = EmeraldPrimary,
                                    inactiveTrackColor = BorderDark
                                )
                            )
                            Text(
                                String.format(Locale.US, "%.1f كم", radiusKm),
                                color = EmeraldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { createRadiusZone() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = BorderDark)
                        ) {
                            Text("إنشاء Zone دائري حول موقعي", color = TextPrimary)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "المناطق المحفوظة (${zones.size})",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "اضغط المنطقة للتركيز عليها",
                    color = TextMuted,
                    fontSize = 9.sp
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                if (zones.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = TextMuted
                                )
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    "لا توجد Zones محفوظة",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "اختر منطقة من الخريطة وسيتم رسم حدودها تلقائيًا.",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                items(zones, key = { it.id }) { zone ->
                    val groups = ZoneEngine.parsePolygonGroups(zone.polygonJson)
                    val pointCount = groups.sumOf { it.size }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { focusZone(zone) },
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(13.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = if (zone.isEnabled) EmeraldPrimary else TextMuted
                                    )
                                    Spacer(Modifier.width(9.dp))
                                    Column {
                                        Text(
                                            zone.name,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            if (groups.size <= 1)
                                                "${pointCount} نقطة حدود"
                                            else
                                                "${groups.size} أجزاء • ${pointCount} نقطة",
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                IconButton(onClick = { loadZoneForEditing(zone) }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "تعديل",
                                        tint = AmberAccent
                                    )
                                }

                                Switch(
                                    checked = zone.isEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            app.zoneRepository.updateZone(
                                                zone.copy(isEnabled = enabled)
                                            )
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = EmeraldPrimary,
                                        checkedTrackColor = EmeraldPrimary.copy(alpha = 0.3f)
                                    )
                                )

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            app.zoneRepository.deleteZone(zone)
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "حذف",
                                        tint = DangerRed
                                    )
                                }
                            }

                            if (zone.allowedKeywords.isNotBlank()) {
                                Text(
                                    "المناطق: ${zone.allowedKeywords}",
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text(
                    if (editingExistingZone == null) "حفظ منطقة العمل" else "حفظ التعديلات",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "الحدود: ${previewGroups.size} جزء • ${previewGroups.sumOf { it.size }} نقطة",
                        color = SuccessGreen,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(
                        value = zoneNameInput,
                        onValueChange = { zoneNameInput = it },
                        label = { Text("اسم الـZone") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = BorderDark
                        )
                    )
                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(
                        value = zoneKeywordsInput,
                        onValueChange = { zoneKeywordsInput = it },
                        label = { Text("أسماء المناطق / كلمات مفتاحية") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = BorderDark
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (zoneNameInput.isNotBlank() && previewGroups.isNotEmpty()) {
                            scope.launch {
                                val polygonJson = ZoneEngine.serializePolygonGroups(
                                    previewAsModel()
                                )

                                val existing = editingExistingZone
                                if (existing == null) {
                                    app.zoneRepository.addZone(
                                        WorkZone(
                                            name = zoneNameInput.trim(),
                                            polygonJson = polygonJson,
                                            isEnabled = true,
                                            allowedKeywords = zoneKeywordsInput.trim()
                                        )
                                    )
                                } else {
                                    app.zoneRepository.updateZone(
                                        existing.copy(
                                            name = zoneNameInput.trim(),
                                            polygonJson = polygonJson,
                                            allowedKeywords = zoneKeywordsInput.trim()
                                        )
                                    )
                                }

                                showSaveDialog = false
                                resetDraft()
                                mapMessage = "تم حفظ الـZone"
                                renderMap(mapViewInstance)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(
                        if (editingExistingZone == null) "حفظ" else "حفظ التعديل",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("إلغاء", color = TextSecondary)
                }
            },
            containerColor = DarkCard
        )
    }
}
