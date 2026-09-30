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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.RidePilotApplication
import com.example.data.model.AppTarget
import com.example.data.model.PricingBand
import com.example.domain.engine.PricingEngine
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
fun PricingManagementScreen(
    navController: NavController,
    appTarget: AppTarget
) {
    val scope = rememberCoroutineScope()
    val app = RidePilotApplication.instance
    val bands by app.pricingRepository.getBands(appTarget).collectAsState(initial = emptyList())

    val isUber = appTarget == AppTarget.UBER
    val screenTitle = if (isUber) "تسعير رحلات Uber" else "تسعير رحلات inDrive"
    val accentColor = if (isUber) AmberAccent else InfoBlue

    var testDistanceInput by remember { mutableStateOf("15.0") }
    var showAddDialog by remember { mutableStateOf(false) }

    var minKmInput by remember { mutableStateOf("") }
    var maxKmInput by remember { mutableStateOf("") }
    var pricePerKmInput by remember { mutableStateOf("") }

    val testDist = testDistanceInput.toDoubleOrNull() ?: 0.0
    val testCalc = PricingEngine.calculateMinimumPrice(testDist, bands)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = accentColor,
                contentColor = DarkBackground
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة شريحة")
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Live Simulation / Tester Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(accentColor)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = accentColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "حاسبة التسعير الفورية (محاكاة)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = testDistanceInput,
                            onValueChange = { testDistanceInput = it },
                            label = { Text("أدخل مسافة لاختبار الحساب (كم)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = BorderDark
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "الحد الأدنى المطلوب:",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${testCalc.minimumPrice} ج.م",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "المعدل للشريحة:",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${testCalc.pricePerKm} ج.م / كم",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = testCalc.explanation,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Pricing Rules Explanation Note
            item {
                Text(
                    text = "قاعدة التسعير: غير تصاعدية (Non-Progressive). يتم تطبيق سعر الكيلومتر للشريحة المطابقة على كامل مسافة الرحلة.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Bands List Header
            item {
                Text(
                    text = "شرائح المسافة (${bands.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Bands List
            items(bands) { band ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "من ${band.minKm} إلى ${band.maxKm} كم",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "الحد الأدنى: ${band.pricePerKm} ج.م / كم",
                                fontSize = 13.sp,
                                color = accentColor,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    app.pricingRepository.deleteBand(band)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = DangerRed)
                        }
                    }
                }
            }
        }
    }

    // Add Band Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("إضافة شريحة تسعير", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = minKmInput,
                        onValueChange = { minKmInput = it },
                        label = { Text("بداية الشريحة (كم) مثل 10") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = maxKmInput,
                        onValueChange = { maxKmInput = it },
                        label = { Text("نهاية الشريحة (كم) مثل 20") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pricePerKmInput,
                        onValueChange = { pricePerKmInput = it },
                        label = { Text("سعر الكيلومتر (ج.م) مثل 7.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val min = minKmInput.toDoubleOrNull()
                        val max = maxKmInput.toDoubleOrNull()
                        val rate = pricePerKmInput.toDoubleOrNull()
                        if (min != null && max != null && rate != null && max > min) {
                            scope.launch {
                                app.pricingRepository.addBand(
                                    PricingBand(
                                        appTarget = appTarget,
                                        minKm = min,
                                        maxKm = max,
                                        pricePerKm = rate
                                    )
                                )
                                showAddDialog = false
                                minKmInput = ""
                                maxKmInput = ""
                                pricePerKmInput = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("حفظ الشريحة", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("إلغاء", color = TextSecondary)
                }
            },
            containerColor = DarkCard
        )
    }
}
