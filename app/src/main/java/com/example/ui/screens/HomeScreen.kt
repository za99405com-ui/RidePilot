package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarRental
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.RidePilotApplication
import com.example.service.RidePilotAccessibilityService
import com.example.service.RidePilotOverlayService
import com.example.ui.navigation.Screen
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BorderDark
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = RidePilotApplication.instance

    val isAccessibilityConnected by RidePilotAccessibilityService.isServiceConnected.collectAsState()
    val uberOffer by RidePilotAccessibilityService.latestUberAnalysis.collectAsState()
    val inDriveParsed by RidePilotAccessibilityService.latestInDriveParsed.collectAsState()

    val automationEnabled by app.settingsRepository.automationEnabled.collectAsState(initial = false)
    val emergencyStop by app.settingsRepository.emergencyStop.collectAsState(initial = false)
    val overlayEnabled by app.settingsRepository.overlayEnabled.collectAsState(initial = true)

    val canDrawOverlays = Settings.canDrawOverlays(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isAccessibilityConnected) SuccessGreen else DangerRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RidePilot",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Master Automation Control Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(if (automationEnabled) EmeraldPrimary else BorderDark)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "الأتمتة العامة (Master Toggle)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (automationEnabled) "مفعلة (تحليل الشاشات + مساعدة inDrive)" else "معطلة (لا يتم تنفيذ أي ضغط أو سحب)",
                                    fontSize = 12.sp,
                                    color = if (automationEnabled) SuccessGreen else TextMuted
                                )
                            }
                            Switch(
                                checked = automationEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        if (checked) {
                                            app.settingsRepository.setEmergencyStop(false)
                                        }
                                        app.settingsRepository.setAutomationEnabled(checked)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = EmeraldPrimary,
                                    checkedTrackColor = EmeraldPrimary.copy(alpha = 0.3f)
                                )
                            )
                        }

                        // Emergency Stop Button
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    app.settingsRepository.setEmergencyStop(true)
                                    val overlayIntent = Intent(context, RidePilotOverlayService::class.java)
                                    context.stopService(overlayIntent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "إيقاف طوارئ فوري (EMERGENCY STOP)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (emergencyStop) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "⚠ تم تفعيل إيقاف الطوارئ! تم إلغاء كل الأوامر الجارية فوراً.",
                                fontSize = 12.sp,
                                color = DangerRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Real-time Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "حالة الاتصال والخدمات",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        StatusRow(
                            label = "خدمة إمكانية الوصول (Accessibility)",
                            isActive = isAccessibilityConnected,
                            actionText = if (!isAccessibilityConnected) "تفعيل" else null,
                            onAction = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        )

                        StatusRow(
                            label = "المساعد العائم (Overlay)",
                            isActive = canDrawOverlays && RidePilotOverlayService.isOverlayRunning,
                            actionText = if (!canDrawOverlays) "إذن الظهور" else if (!RidePilotOverlayService.isOverlayRunning) "تشغيل" else "إيقاف",
                            onAction = {
                                if (!canDrawOverlays) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    val overlayIntent = Intent(context, RidePilotOverlayService::class.java)
                                    if (RidePilotOverlayService.isOverlayRunning) {
                                        context.stopService(overlayIntent)
                                    } else {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(overlayIntent)
                                        } else {
                                            context.startService(overlayIntent)
                                        }
                                    }
                                }
                            }
                        )

                        StatusRow(
                            label = "مراقبة Uber",
                            isActive = uberOffer != null,
                            activeSubtext = "تم رصد آخر طلب (${uberOffer?.offer?.displayedPrice ?: 0} ج.م)",
                            inactiveSubtext = "في انتظار فتح Uber"
                        )

                        StatusRow(
                            label = "مراقبة inDrive",
                            isActive = inDriveParsed != null,
                            activeSubtext = if (inDriveParsed?.isOffline == true) "غير متصل (الوضع الآمن)" else "متصل ⚠",
                            inactiveSubtext = "في انتظار فتح inDrive"
                        )
                    }
                }
            }

            // Quick Menu Items Grid
            item {
                Text(
                    text = "القوائم والإعدادات",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                MenuGridItem(
                    title = "مناطق العمل (Work Zones)",
                    subtitle = "رسم وتعديل Polygon على الخريطة وقواعد الزون",
                    icon = Icons.Default.Map,
                    accentColor = EmeraldPrimary,
                    onClick = { navController.navigate(Screen.WorkZones.route) }
                )
            }

            item {
                MenuGridItem(
                    title = "تسعير Uber (Uber Pricing)",
                    subtitle = "جدول شرائح الكيلومتر والحد الأدنى لرحلات Uber",
                    icon = Icons.Default.Payment,
                    accentColor = AmberAccent,
                    onClick = { navController.navigate(Screen.UberPricing.route) }
                )
            }

            item {
                MenuGridItem(
                    title = "تسعير inDrive (inDrive Pricing)",
                    subtitle = "جدول شرائح الأسعار المستقلة لـ inDrive",
                    icon = Icons.Default.CarRental,
                    accentColor = InfoBlue,
                    onClick = { navController.navigate(Screen.InDrivePricing.route) }
                )
            }

            item {
                MenuGridItem(
                    title = "إعدادات التفاوض (Negotiation)",
                    subtitle = "الهامش المبدئي، الخطوة، الحد الأدنى (Floor)، والقبول التلقائي",
                    icon = Icons.Default.Handshake,
                    accentColor = EmeraldPrimary,
                    onClick = { navController.navigate(Screen.Negotiation.route) }
                )
            }

            item {
                MenuGridItem(
                    title = "المعايرة المباشرة (Calibration)",
                    subtitle = "عرض ما يراه Accessibility وOCR في الوقت الفعلي",
                    icon = Icons.Default.Speed,
                    accentColor = AmberAccent,
                    onClick = { navController.navigate(Screen.Calibration.route) }
                )
            }

            item {
                MenuGridItem(
                    title = "سجل العمليات (Logs)",
                    subtitle = "سجل القرارات والأسعار وتصدير البيانات",
                    icon = Icons.Default.History,
                    accentColor = TextSecondary,
                    onClick = { navController.navigate(Screen.Logs.route) }
                )
            }

            item {
                MenuGridItem(
                    title = "معالج الأذونات (Permissions Wizard)",
                    subtitle = "التحقق خطوة بخطوة من كافة أذونات النظام المطلوبة",
                    icon = Icons.Default.Security,
                    accentColor = SuccessGreen,
                    onClick = { navController.navigate(Screen.Permissions.route) }
                )
            }

            item {
                MenuGridItem(
                    title = "الإعدادات المتقدمة (Advanced)",
                    subtitle = "مسافة التسعير، اتجاه السحب، وحساسية الكشف",
                    icon = Icons.Default.Settings,
                    accentColor = TextMuted,
                    onClick = { navController.navigate(Screen.AdvancedSettings.route) }
                )
            }
        }
    }
}

@Composable
fun StatusRow(
    label: String,
    isActive: Boolean,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    activeSubtext: String? = null,
    inactiveSubtext: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isActive) SuccessGreen else DangerRed)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                val sub = if (isActive) activeSubtext else inactiveSubtext
                if (sub != null) {
                    Text(
                        text = sub,
                        fontSize = 11.sp,
                        color = if (isActive) SuccessGreen else TextMuted
                    )
                }
            }
        }

        if (actionText != null && onAction != null) {
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(actionText, fontSize = 11.sp, color = EmeraldPrimary)
            }
        }
    }
}

@Composable
fun MenuGridItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
