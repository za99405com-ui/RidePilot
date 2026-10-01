package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.RidePilotApplication
import com.example.data.model.PricingDistanceMode
import com.example.data.model.SwipeDirection
import com.example.data.model.ZoneVerificationMode
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCard
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    navController: NavController
) {
    val scope = rememberCoroutineScope()
    val app = RidePilotApplication.instance

    val distanceMode by app.settingsRepository.pricingDistanceMode.collectAsState(initial = PricingDistanceMode.PICKUP_PLUS_TRIP)
    val zoneMode by app.settingsRepository.zoneVerificationMode.collectAsState(initial = ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION)
    val swipeDir by app.settingsRepository.swipeDirection.collectAsState(initial = SwipeDirection.SWIPE_LEFT)
    val confidenceThreshold by app.settingsRepository.confidenceThreshold.collectAsState(initial = 70)
    val maxPickupDistanceKm by app.settingsRepository.maxPickupDistanceKm.collectAsState(initial = 5.0)
    val ocrFallback by app.settingsRepository.ocrFallbackEnabled.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات المتقدمة", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
            // Pricing Distance Mode
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "المسافة المعتمدة في حساب التسعير",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "اختر ما إذا كان جدول الشرائح يطبق على مسافة الرحلة فقط أم مسافة الوصول + الرحلة.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = distanceMode == PricingDistanceMode.PICKUP_PLUS_TRIP,
                                onClick = {
                                    scope.launch {
                                        app.settingsRepository.setPricingDistanceMode(PricingDistanceMode.PICKUP_PLUS_TRIP)
                                    }
                                },
                                label = { Text("الوصول + الرحلة (Default)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = DarkBackground
                                )
                            )
                            FilterChip(
                                selected = distanceMode == PricingDistanceMode.TRIP_ONLY,
                                onClick = {
                                    scope.launch {
                                        app.settingsRepository.setPricingDistanceMode(PricingDistanceMode.TRIP_ONLY)
                                    }
                                },
                                label = { Text("الرحلة فقط") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = DarkBackground
                                )
                            )
                        }
                    }
                }
            }

            // Maximum pickup distance shown on each inDrive request card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "أقصى مسافة بينك وبين العميل",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "يستخدم المسافة المكتوبة أعلى طلب inDrive قبل فتح الطلب.",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Text(
                                text = String.format("%.1f كم", maxPickupDistanceKm),
                                color = EmeraldPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = maxPickupDistanceKm.toFloat(),
                            onValueChange = { value ->
                                scope.launch {
                                    app.settingsRepository.setMaxPickupDistanceKm(value.toDouble())
                                }
                            },
                            valueRange = 0.5f..15f,
                            steps = 28,
                            colors = SliderDefaults.colors(
                                thumbColor = EmeraldPrimary,
                                activeTrackColor = EmeraldPrimary
                            )
                        )

                        Text(
                            text = "أي طلب أبعد من هذا الحد يتم تجاهله قبل فتحه.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Zone Verification Mode
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "قاعدة التحقق من منطقة العمل (Zone Rule)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "تحديد أي من النقاط يجب أن تقع داخل المنطقة المصرح بها.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = zoneMode == ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION,
                                onClick = {
                                    scope.launch {
                                        app.settingsRepository.setZoneVerificationMode(ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION)
                                    }
                                },
                                label = { Text("الركوب + الوجهة كلاهما داخل الـZone (الافتراضي)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = DarkBackground
                                )
                            )
                            FilterChip(
                                selected = zoneMode == ZoneVerificationMode.PICKUP_ONLY,
                                onClick = {
                                    scope.launch {
                                        app.settingsRepository.setZoneVerificationMode(ZoneVerificationMode.PICKUP_ONLY)
                                    }
                                },
                                label = { Text("نقطة الركوب فقط داخل الـZone") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = DarkBackground
                                )
                            )
                            FilterChip(
                                selected = zoneMode == ZoneVerificationMode.DESTINATION_ONLY,
                                onClick = {
                                    scope.launch {
                                        app.settingsRepository.setZoneVerificationMode(ZoneVerificationMode.DESTINATION_ONLY)
                                    }
                                },
                                label = { Text("الوجهة فقط داخل الـZone") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = DarkBackground
                                )
                            )
                        }
                    }
                }
            }

            // Swipe Direction for inDrive
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "اتجاه سحب البطاقات في inDrive",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "الاتجاه المستخدم لإخفاء الطلب الخارج عن المنطقة عبر السحب النسبي.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = swipeDir == SwipeDirection.SWIPE_LEFT,
                                onClick = {
                                    scope.launch {
                                        app.settingsRepository.setSwipeDirection(SwipeDirection.SWIPE_LEFT)
                                    }
                                },
                                label = { Text("سحب لليسار (Swipe Left)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = DarkBackground
                                )
                            )
                            FilterChip(
                                selected = swipeDir == SwipeDirection.SWIPE_RIGHT,
                                onClick = {
                                    scope.launch {
                                        app.settingsRepository.setSwipeDirection(SwipeDirection.SWIPE_RIGHT)
                                    }
                                },
                                label = { Text("سحب لليمين (Swipe Right)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = DarkBackground
                                )
                            )
                        }
                    }
                }
            }

            // Confidence Threshold Slider
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "حد الثقة الأدنى لتنفيذ الأوامر (Confidence)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "$confidenceThreshold%",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary
                            )
                        }
                        Text(
                            text = "إذا كانت ثقة قراءة الشاشة أقل من هذه النسبة، يمتنع التطبيق تماماً عن تنفيذ أي ضغط أو سحب.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = confidenceThreshold.toFloat(),
                            onValueChange = { newVal ->
                                scope.launch {
                                    app.settingsRepository.setConfidenceThreshold(newVal.toInt())
                                }
                            },
                            valueRange = 40f..95f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = EmeraldPrimary,
                                activeTrackColor = EmeraldPrimary
                            )
                        )
                    }
                }
            }

            // OCR Fallback Switch
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تفعيل OCR كحل احتياطي (MediaProjection)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "يستخدم ML Kit لتحليل النص بصرياً فقط في حال كانت شجرة الـ Accessibility خالية من النصوص.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = ocrFallback,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    app.settingsRepository.setOcrFallbackEnabled(checked)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary)
                        )
                    }
                }
            }
        }
    }
}
