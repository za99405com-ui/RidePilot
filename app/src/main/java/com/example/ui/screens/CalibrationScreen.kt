package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.service.RidePilotAccessibilityService
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BorderDark
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCard
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    navController: NavController
) {
    val liveOffer by RidePilotAccessibilityService.liveCalibrationOffer.collectAsState()
    val uberAnalysis by RidePilotAccessibilityService.latestUberAnalysis.collectAsState()
    val inDriveParsed by RidePilotAccessibilityService.latestInDriveParsed.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المعايرة والقراءة المباشرة", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = EmeraldPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "وضع المعايرة المباشر (Live Calibration)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "هذه الشاشة تعرض ما تلتقطه خدمة Accessibility في الوقت الفعلي من شاشات Uber وinDrive، مع مصدر القراءة ونسبة الثقة.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            if (liveOffer == null && inDriveParsed == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = AmberAccent,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "في انتظار فتح تطبيق Uber أو inDrive...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "تأكد من تفعيل خدمة إمكانية الوصول في إعدادات الهاتف، ثم افتح أي شاشة طلب لتظهر القراءة هنا فوراً.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                liveOffer?.let { offer ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(EmeraldPrimary)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "التطبيق المرصود: ${offer.app.name}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (offer.app.name == "UBER") AmberAccent else InfoBlue
                                    )
                                    SourceBadge(source = offer.rawSource)
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "مستوى الثقة: ${offer.confidence}%",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (offer.confidence / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (offer.confidence >= 70) SuccessGreen else AmberAccent,
                                    trackColor = BorderDark
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                CalibrationField(label = "السعر المقروء (Price)", value = "${offer.displayedPrice ?: "لم يرصد"} ج.م", source = offer.rawSource)
                                CalibrationField(label = "مسافة الوصول (Pickup Distance)", value = "${offer.pickupDistanceKm ?: "غير محدد"} كم", source = offer.rawSource)
                                CalibrationField(label = "مسافة الرحلة (Trip Distance)", value = "${offer.tripDistanceKm ?: "غير محدد"} كم", source = offer.rawSource)
                                CalibrationField(label = "عنوان الانطلاق (Pickup)", value = offer.pickupAddress ?: "غير محدد", source = offer.rawSource)
                                CalibrationField(label = "عنوان الوجهة (Destination)", value = offer.destinationAddress ?: "غير محدد", source = offer.rawSource)
                                CalibrationField(label = "نوع الخدمة", value = offer.rideType ?: "افتراضي", source = offer.rawSource)
                                CalibrationField(label = "تقييم العميل", value = "${offer.passengerRating ?: "غير متوفر"}", source = offer.rawSource)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalibrationField(
    label: String,
    value: String,
    source: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = TextMuted)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        SourceBadge(source = source)
    }
}

@Composable
fun SourceBadge(source: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (source == "ACCESSIBILITY") EmeraldPrimary.copy(alpha = 0.2f) else AmberAccent.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = source,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (source == "ACCESSIBILITY") EmeraldPrimary else AmberAccent
        )
    }
}
