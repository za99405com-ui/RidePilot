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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
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
import com.example.domain.engine.ZoneEngine
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BorderDark
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonesScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = RidePilotApplication.instance
    val zones by app.zoneRepository.getAllZones().collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var zoneNameInput by remember { mutableStateOf("") }
    var zoneKeywordsInput by remember { mutableStateOf("") }

    val drawnPoints = remember { mutableStateListOf<GeoPoint>() }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var drawMode by remember { mutableStateOf(false) }
    var selectionClosed by remember { mutableStateOf(false) }

    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var autoRadiusKm by remember { mutableStateOf(3f) }
    var locateAfterPermission by remember { mutableStateOf(false) }

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

    fun renderMap(mapView: MapView?) {
        if (mapView == null) return

        // Rebuild visual overlays while preserving the touch/drawing overlay.
        mapView.overlays.removeAll { it is Polygon || it is Polyline || it is Marker }

        zones.forEach { zone ->
            val points = ZoneEngine.parsePolygonJson(zone.polygonJson)
                .map { GeoPoint(it.latitude, it.longitude) }

            if (points.size >= 3) {
                mapView.overlays.add(
                    Polygon(mapView).apply {
                        this.points = points
                        title = zone.name
                        fillPaint.color = if (zone.isEnabled) 0x2250D6A0 else 0x1675849A
                        outlinePaint.color =
                            if (zone.isEnabled) 0xFF50D6A0.toInt() else 0xFF75849A.toInt()
                        outlinePaint.strokeWidth = if (zone.isEnabled) 4.5f else 3f
                    }
                )
            }
        }

        if (drawnPoints.size >= 2 && !selectionClosed) {
            mapView.overlays.add(
                Polyline(mapView).apply {
                    setPoints(drawnPoints.toList())
                    outlinePaint.color = 0xFF63A7FF.toInt()
                    outlinePaint.strokeWidth = 7f
                }
            )
        }

        if (drawnPoints.size >= 3 && selectionClosed) {
            mapView.overlays.add(
                Polygon(mapView).apply {
                    points = drawnPoints.toList()
                    title = "الزون الجديدة"
                    fillPaint.color = 0x3363A7FF
                    outlinePaint.color = 0xFF63A7FF.toInt()
                    outlinePaint.strokeWidth = 6f
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

        mapView.invalidate()
    }

    fun focusOn(point: GeoPoint, zoom: Double = 16.0) {
        mapViewInstance?.let { mapView ->
            mapView.controller.animateTo(point)
            mapView.controller.setZoom(zoom)
        }
    }

    fun focusZone(zone: WorkZone) {
        val points = ZoneEngine.parsePolygonJson(zone.polygonJson)
        if (points.isEmpty()) return
        val center = GeoPoint(
            points.map { it.latitude }.average(),
            points.map { it.longitude }.average()
        )
        focusOn(center, if (points.size >= 20) 13.5 else 14.5)
    }

    @SuppressLint("MissingPermission")
    fun locateNow() {
        if (!hasLocationPermission) return

        isLocating = true
        locationMessage = "جار تحديد موقعك بدقة…"
        val cancellation = CancellationTokenSource()

        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = GeoPoint(location.latitude, location.longitude)
                    isLocating = false
                    locationMessage = "تم تحديد موقعك"
                    focusOn(currentLocation!!, 16.5)
                    renderMap(mapViewInstance)
                } else {
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { last ->
                            isLocating = false
                            if (last != null) {
                                currentLocation = GeoPoint(last.latitude, last.longitude)
                                locationMessage = "تم استخدام آخر موقع معروف"
                                focusOn(currentLocation!!, 16.0)
                                renderMap(mapViewInstance)
                            } else {
                                locationMessage = "تعذر تحديد الموقع — فعّل GPS وحاول مرة أخرى"
                            }
                        }
                        .addOnFailureListener {
                            isLocating = false
                            locationMessage = "تعذر قراءة الموقع"
                        }
                }
            }
            .addOnFailureListener {
                isLocating = false
                locationMessage = "تعذر تحديد الموقع — حاول مرة أخرى"
            }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocationPermission =
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (!hasLocationPermission) {
            locateAfterPermission = false
            locationMessage = "إذن الموقع مطلوب للتحديد التلقائي"
        }
    }

    fun requestLocation() {
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

    fun createAutomaticZonePreview() {
        val center = currentLocation
        if (center == null) {
            locationMessage = "حدد موقعك أولًا ثم اضغط التحديد التلقائي"
            requestLocation()
            return
        }

        val generated = ZoneEngine.createCirclePolygon(
            center = LatLngPoint(center.latitude, center.longitude),
            radiusKm = autoRadiusKm.toDouble(),
            segments = 48
        )

        drawnPoints.clear()
        drawnPoints.addAll(generated.map { GeoPoint(it.latitude, it.longitude) })
        drawMode = false
        selectionClosed = true

        if (zoneNameInput.isBlank()) {
            zoneNameInput = String.format(Locale.US, "حول موقعي %.1f كم", autoRadiusKm)
        }

        val zoom = when {
            autoRadiusKm <= 1f -> 15.3
            autoRadiusKm <= 3f -> 14.2
            autoRadiusKm <= 7f -> 12.8
            else -> 11.5
        }

        focusOn(center, zoom)
        renderMap(mapViewInstance)
        locationMessage = "تم إنشاء Zone تلقائيًا حول موقعك"
    }

    LaunchedEffect(hasLocationPermission, locateAfterPermission) {
        if (hasLocationPermission && locateAfterPermission) {
            locateAfterPermission = false
            locateNow()
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            locateNow()
        }
    }

    LaunchedEffect(zones) {
        renderMap(mapViewInstance)
    }

    DisposableEffect(mapViewInstance) {
        mapViewInstance?.onResume()
        onDispose {
            mapViewInstance?.onPause()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مناطق العمل (Work Zones)", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        drawMode.value = false
                        drawnPoints.clear()
                        redrawZoneSelection(mapViewInstance, closed = false)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "مسح النقاط", tint = AmberAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = EmeraldPrimary,
                contentColor = DarkBackground
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة منطقة")
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Interactive Map Header Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(13.0)
                            // Default to Alexandria center (31.2001, 29.9187)
                            controller.setCenter(GeoPoint(31.2200, 29.9500))

                            // Precise point-by-point zone drawing. A tap adds one corner;
                            // dragging still pans the map because touch-move events are not consumed.
                            val touchOverlay = object : org.osmdroid.views.overlay.Overlay() {
                                override fun onSingleTapConfirmed(event: MotionEvent?, mapView: MapView?): Boolean {
                                    if (!drawMode.value || event == null || mapView == null) return false

                                    val point = mapView.projection
                                        .fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                                    drawnPoints.add(point)
                                    redrawZoneSelection(mapView, closed = false)
                                    return true
                                }
                            }
                            overlays.add(touchOverlay)
                            mapViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.94f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when {
                            drawMode.value && drawnPoints.isEmpty() -> "اضغط على أركان المنطقة نقطة بنقطة"
                            drawMode.value -> "تم تحديد ${drawnPoints.size} نقطة • اضغط إنهاء بعد 3 نقاط أو أكثر"
                            drawnPoints.size >= 3 -> "✓ الزون جاهز — راجعه ثم احفظه"
                            else -> "حرّك الخريطة للمكان المطلوب ثم ابدأ التحديد"
                        },
                        fontSize = 11.sp,
                        color = if (drawMode.value) AmberAccent else TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!drawMode.value) {
                        Button(
                            onClick = {
                                drawnPoints.clear()
                                redrawZoneSelection(mapViewInstance, closed = false)
                                drawMode.value = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("ابدأ التحديد", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (drawnPoints.isNotEmpty()) {
                                    drawnPoints.removeAt(drawnPoints.lastIndex)
                                    redrawZoneSelection(mapViewInstance, closed = false)
                                }
                            },
                            enabled = drawnPoints.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("تراجع", color = TextPrimary)
                        }

                        Button(
                            onClick = {
                                if (drawnPoints.size >= 3) {
                                    drawMode.value = false
                                    redrawZoneSelection(mapViewInstance, closed = true)
                                }
                            },
                            enabled = drawnPoints.size >= 3,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("إنهاء", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = {
                                drawMode.value = false
                                drawnPoints.clear()
                                redrawZoneSelection(mapViewInstance, closed = false)
                            }
                        ) {
                            Text("إلغاء", color = DangerRed)
                        }
                    }
                }
            }

            // Zones List Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المناطق المحفوظة (${zones.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "تفعيل / تعطيل الزون",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Zones LazyColumn
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(zones) { zone ->
                    val pointCount = ZoneEngine.parsePolygonJson(zone.polygonJson).size
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = if (zone.isEnabled) EmeraldPrimary else TextMuted
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = zone.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "مضلع من $pointCount نقاط",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = zone.isEnabled,
                                        onCheckedChange = { isChecked ->
                                            scope.launch {
                                                app.zoneRepository.updateZone(zone.copy(isEnabled = isChecked))
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
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = DangerRed)
                                    }
                                }
                            }

                            if (zone.allowedKeywords.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "الكلمات المفتاحية: ${zone.allowedKeywords}",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Zone Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("إنشاء منطقة عمل جديدة", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = if (drawnPoints.size >= 3) "تم رسم مضلع من ${drawnPoints.size} نقاط على الخريطة" else "تنبيه: يمكنك حفظ المنطقة بالكلمات المفتاحية أو إضافة نقاط على الخريطة",
                        fontSize = 12.sp,
                        color = if (drawnPoints.size >= 3) SuccessGreen else AmberAccent
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = zoneNameInput,
                        onValueChange = { zoneNameInput = it },
                        label = { Text("اسم المنطقة (مثال: شرق الإسكندرية)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = BorderDark
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = zoneKeywordsInput,
                        onValueChange = { zoneKeywordsInput = it },
                        label = { Text("الكلمات المفتاحية (مفصولة بفاصلة)") },
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
                        if (zoneNameInput.isNotBlank()) {
                            scope.launch {
                                // Serialize points
                                val jsonArr = JSONArray()
                                for (p in drawnPoints) {
                                    val obj = JSONObject()
                                    obj.put("latitude", p.latitude)
                                    obj.put("longitude", p.longitude)
                                    jsonArr.put(obj)
                                }

                                app.zoneRepository.addZone(
                                    WorkZone(
                                        name = zoneNameInput.trim(),
                                        polygonJson = jsonArr.toString(),
                                        isEnabled = true,
                                        allowedKeywords = zoneKeywordsInput.trim()
                                    )
                                )
                                showCreateDialog = false
                                zoneNameInput = ""
                                drawnPoints.clear()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("حفظ المنطقة", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("إلغاء", color = TextSecondary)
                }
            },
            containerColor = DarkCard
        )
    }
}
