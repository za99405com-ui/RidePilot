package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.RidePilotApplication
import com.example.data.model.AppTarget
import com.example.data.model.InDriveMapPreview
import com.example.service.RidePilotAccessibilityService
import com.example.service.RidePilotOverlayService
import com.example.ui.navigation.Screen
import com.example.ui.theme.BorderDark
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCard
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = RidePilotApplication.instance

    val automationEnabled by app.settingsRepository.automationEnabled.collectAsState(initial = false)
    val emergencyStop by app.settingsRepository.emergencyStop.collectAsState(initial = false)
    val accessibilityConnected by RidePilotAccessibilityService.isServiceConnected.collectAsState()
    val uberAnalysis by RidePilotAccessibilityService.latestUberAnalysis.collectAsState()
    val inDriveParsed by RidePilotAccessibilityService.latestInDriveParsed.collectAsState()
    val inDrivePreview by RidePilotAccessibilityService.latestInDriveMapPreview.collectAsState()
    val activeTarget by RidePilotAccessibilityService.activeTarget.collectAsState()

    Scaffold(containerColor = DarkBackground) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("RidePilot", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text("لوحة قيادة ذكية للطلبات", color = TextMuted, fontSize = 12.sp)
                    }
                    StatusChip(
                        text = if (accessibilityConnected) "جاهز" else "يحتاج صلاحية",
                        active = accessibilityConnected
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    when {
                                        emergencyStop -> "STOP نهائي"
                                        automationEnabled -> "التحكم التلقائي شغّال"
                                        else -> "متوقف مؤقتًا"
                                    },
                                    color = if (emergencyStop) DangerRed else TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    when {
                                        emergencyStop -> "لن يعمل أو يظهر مجددًا إلا بعد تشغيله من RidePilot"
                                        automationEnabled -> "inDrive ينفّذ قواعدك • Uber يقرأ ويحلّل"
                                        else -> "يمكنك الاستئناف من التطبيق أو الفقاعة"
                                    },
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = automationEnabled && !emergencyStop,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        if (enabled) {
                                            // Starting after HARD STOP is only allowed here, inside RidePilot.
                                            app.settingsRepository.startAutomationFromApp()
                                            if (Settings.canDrawOverlays(context)) {
                                                val overlayIntent = Intent(context, RidePilotOverlayService::class.java)
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    context.startForegroundService(overlayIntent)
                                                } else {
                                                    context.startService(overlayIntent)
                                                }
                                            }
                                        } else {
                                            app.settingsRepository.pauseAutomation()
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = EmeraldPrimary,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = BorderDark
                                )
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when {
                                emergencyStop -> "الإيقاف النهائي مفعّل • أعد التشغيل من هذا التطبيق فقط"
                                automationEnabled -> "الحالة: شغال"
                                else -> "الحالة: Pause مؤقت"
                            },
                            color = when {
                                emergencyStop -> DangerRed
                                automationEnabled -> SuccessGreen
                                else -> TextSecondary
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppStatusCard(
                        modifier = Modifier.weight(1f),
                        title = "Uber",
                        subtitle = when {
                            activeTarget == AppTarget.UBER && uberAnalysis != null ->
                                "${uberAnalysis?.offer?.displayedPrice ?: "—"} ج • ${uberAnalysis?.totalPricingDistanceKm ?: "—"} كم"
                            activeTarget == AppTarget.UBER -> "جاري قراءة الطلب"
                            else -> "انتظار"
                        },
                        active = activeTarget == AppTarget.UBER
                    )
                    AppStatusCard(
                        modifier = Modifier.weight(1f),
                        title = "inDrive",
                        subtitle = when {
                            activeTarget == AppTarget.INDRIVE && inDriveParsed?.isOffline == true -> "غير متصل • آمن"
                            activeTarget == AppTarget.INDRIVE -> inDriveParsed?.screenType?.name ?: "جاري القراءة"
                            else -> "انتظار"
                        },
                        active = activeTarget == AppTarget.INDRIVE
                    )
                }
            }

            if (inDrivePreview != null) {
                item {
                    InDriveMapPreviewCard(preview = inDrivePreview!!)
                }
            }

            if (!accessibilityConnected || !Settings.canDrawOverlays(context)) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("إعداد سريع", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            if (!accessibilityConnected) {
                                Button(
                                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                ) { Text("تفعيل Accessibility", color = Color.White) }
                            }
                            if (!Settings.canDrawOverlays(context)) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = BorderDark)
                                ) { Text("السماح بالظهور فوق التطبيقات", color = TextPrimary) }
                            }
                        }
                    }
                }
            }

            item { Text("التحكم السريع", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard(Modifier.weight(1f), "مناطق العمل", "ارسم الزون", Icons.Default.Map) {
                        navController.navigate(Screen.WorkZones.route)
                    }
                    QuickActionCard(Modifier.weight(1f), "تسعير inDrive", "الشرائح والحد الأدنى", Icons.Default.Payments) {
                        navController.navigate(Screen.InDrivePricing.route)
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard(Modifier.weight(1f), "التفاوض", "الهامش والخطوة", Icons.Default.SwapHoriz) {
                        navController.navigate(Screen.Negotiation.route)
                    }
                    QuickActionCard(Modifier.weight(1f), "المعايرة", "البرنامج بيقرأ إيه", Icons.Default.Speed) {
                        navController.navigate(Screen.Calibration.route)
                    }
                }
            }

            item { Text("الأدوات", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard(Modifier.weight(1f), "تسعير Uber", "جدول مستقل", Icons.Default.Tune) {
                        navController.navigate(Screen.UberPricing.route)
                    }
                    QuickActionCard(Modifier.weight(1f), "السجل", "قرارات البرنامج", Icons.Default.History) {
                        navController.navigate(Screen.Logs.route)
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard(Modifier.weight(1f), "الصلاحيات", "فحص الإعداد", Icons.Default.Security) {
                        navController.navigate(Screen.Permissions.route)
                    }
                    QuickActionCard(Modifier.weight(1f), "متقدم", "السحب والمسافة", Icons.Default.Settings) {
                        navController.navigate(Screen.AdvancedSettings.route)
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            app.settingsRepository.hardStop()
                            context.stopService(Intent(context, RidePilotOverlayService::class.java))
                        }
                    },
                    enabled = !emergencyStop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        disabledContainerColor = DangerRed.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.StopCircle, null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (emergencyStop) "STOP مفعّل — التشغيل من أعلى"
                        else "STOP نهائي",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InDriveMapPreviewCard(preview: InDriveMapPreview) {
    val context = LocalContext.current
    val mapView = remember(context) {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "معاينة طلب inDrive",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    "${preview.routeDistanceKm ?: "—"} كم",
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            AndroidView(
                factory = { mapView },
                update = { map ->
                    map.overlays.removeAll { it is Marker || it is Polyline }

                    val a = GeoPoint(
                        preview.pickupPoint.latitude,
                        preview.pickupPoint.longitude
                    )
                    val b = GeoPoint(
                        preview.destinationPoint.latitude,
                        preview.destinationPoint.longitude
                    )

                    map.overlays.add(
                        Marker(map).apply {
                            position = a
                            title = "A • نقطة الركوب"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                    )
                    map.overlays.add(
                        Marker(map).apply {
                            position = b
                            title = "B • الوجهة"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                    )
                    map.overlays.add(
                        Polyline(map).apply {
                            setPoints(listOf(a, b))
                            outlinePaint.strokeWidth = 6f
                        }
                    )

                    val north = maxOf(a.latitude, b.latitude) + 0.006
                    val south = minOf(a.latitude, b.latitude) - 0.006
                    val east = maxOf(a.longitude, b.longitude) + 0.006
                    val west = minOf(a.longitude, b.longitude) - 0.006

                    map.zoomToBoundingBox(
                        BoundingBox(north, east, south, west),
                        false,
                        48
                    )
                    map.invalidate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )

            Spacer(Modifier.height(10.dp))
            Text(
                "A  ${preview.pickupAddress}",
                color = TextPrimary,
                fontSize = 11.sp,
                maxLines = 2
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "B  ${preview.destinationAddress}",
                color = TextPrimary,
                fontSize = 11.sp,
                maxLines = 2
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "الوصول للعميل: ${preview.pickupDistanceKm ?: "—"} كم • السعر: ${preview.displayedPrice ?: "—"} ج",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }
}

@Composable
private fun StatusChip(text: String, active: Boolean) {
    Row(
        modifier = Modifier
            .background(
                color = if (active) SuccessGreen.copy(alpha = 0.14f) else DangerRed.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(if (active) SuccessGreen else DangerRed, CircleShape))
        Spacer(Modifier.size(7.dp))
        Text(text, color = if (active) SuccessGreen else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AppStatusCard(modifier: Modifier, title: String, subtitle: String, active: Boolean) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (active) SuccessGreen else TextMuted, CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Box(
                modifier = Modifier.size(38.dp).background(EmeraldPrimary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}
