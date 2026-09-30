package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.RidePilotApplication
import com.example.data.model.AppTarget
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
                        Text("RidePilot", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text("مساعد الرحلات", color = TextMuted, fontSize = 12.sp)
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
                    shape = RoundedCornerShape(22.dp),
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
                                    if (automationEnabled) "الأتمتة شغالة" else "الأتمتة متوقفة",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (automationEnabled) "inDrive يتحكم حسب قواعدك • Uber تحليل فقط"
                                    else "فعّلها بعد التأكد من الإعدادات",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = automationEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        if (enabled) app.settingsRepository.setEmergencyStop(false)
                                        app.settingsRepository.setAutomationEnabled(enabled)
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
                        if (emergencyStop) {
                            Spacer(Modifier.height(10.dp))
                            Text("إيقاف الطوارئ مفعّل", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
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

            if (!accessibilityConnected || !Settings.canDrawOverlays(context)) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(18.dp),
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

            item { Text("الإعدادات الأساسية", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }

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

            item { Text("المزيد", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }

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
                            app.settingsRepository.setEmergencyStop(true)
                            context.stopService(Intent(context, RidePilotOverlayService::class.java))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.StopCircle, null)
                    Spacer(Modifier.size(8.dp))
                    Text("إيقاف طوارئ", fontWeight = FontWeight.Bold)
                }
            }
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
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
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
