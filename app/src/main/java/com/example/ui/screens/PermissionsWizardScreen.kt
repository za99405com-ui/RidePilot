package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.service.RidePilotAccessibilityService
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCard
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsWizardScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val isAccessibilityConnected by RidePilotAccessibilityService.isServiceConnected.collectAsState()

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var isIgnoringBatteryOptimizations by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocationPermission = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("معالج الأذونات (Setup Wizard)", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "تهيئة أذونات الهاتف خطوة بخطوة",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "يعمل التطبيق بشكل كامل ومحلي بدون أي خادم خارجي. الأذونات مطلوبة فقط لقراءة الطلبات من الشاشة وعرض المساعد العائم.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Step 1: Accessibility
            item {
                PermissionStepCard(
                    stepNumber = "1",
                    title = "خدمة إمكانية الوصول (Accessibility)",
                    description = "مطلوبة لقراءة بيانات الطلب (السعر، المسافات، العناوين) من شاشة Uber وinDrive، ولتنفيذ السحب الآمن للطلبات خارج منطقتك.",
                    icon = Icons.Default.TouchApp,
                    isGranted = isAccessibilityConnected,
                    buttonLabel = if (isAccessibilityConnected) "مفعلة ✓" else "فتح إعدادات الوصول",
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }

            // Step 2: Overlay
            item {
                PermissionStepCard(
                    stepNumber = "2",
                    title = "الظهور فوق التطبيقات (Overlay)",
                    description = "مطلوب لإظهار بطاقة المساعد العائمة فوق Uber وinDrive لتوضيح هل الرحلة مناسبة أم غير مناسبة والحد الأدنى للربح.",
                    icon = Icons.Default.Layers,
                    isGranted = hasOverlayPermission,
                    buttonLabel = if (hasOverlayPermission) "ممنوح ✓" else "منح إذن الظهور",
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            }

            // Step 3: Location
            item {
                PermissionStepCard(
                    stepNumber = "3",
                    title = "الموقع الجغرافي (Location)",
                    description = "مطلوب لتحديد موقعك بالنسبة لمناطق العمل (Work Zones) والتأكد من مطابقة نقطة الركوب والوجهة للـ Polygon.",
                    icon = Icons.Default.MyLocation,
                    isGranted = hasLocationPermission,
                    buttonLabel = if (hasLocationPermission) "ممنوح ✓" else "طلب إذن الموقع",
                    onAction = {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )
            }

            // Step 4: Notifications
            item {
                PermissionStepCard(
                    stepNumber = "4",
                    title = "إشعارات الخدمة الخلفية (Notifications)",
                    description = "مطلوب في أندرويد لإبقاء المساعد يعمل بثبات في الخلفية أثناء التبديل بين التطبيقات بدون إغلاقه من النظام.",
                    icon = Icons.Default.Notifications,
                    isGranted = hasNotificationPermission,
                    buttonLabel = if (hasNotificationPermission) "ممنوح ✓" else "طلب إذن الإشعارات",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }

            // Step 5: Battery Optimization
            item {
                PermissionStepCard(
                    stepNumber = "5",
                    title = "استثناء تحسينات البطارية (Battery)",
                    description = "مهم على هواتف سامسونج لمنع النظام من تجميد خدمة المساعد أثناء عملك على الطريق.",
                    icon = Icons.Default.BatteryAlert,
                    isGranted = isIgnoringBatteryOptimizations,
                    buttonLabel = if (isIgnoringBatteryOptimizations) "مستثنى ✓" else "استثناء التطبيق",
                    onAction = {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionStepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    buttonLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) SuccessGreen.copy(alpha = 0.2f) else AmberAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGranted) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    } else {
                        Text(text = stepNumber, fontWeight = FontWeight.Bold, color = AmberAccent, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = description, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isGranted) SuccessGreen else EmeraldPrimary
                )
            ) {
                Text(text = buttonLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
