package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.example.data.model.NegotiationConfig
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BorderDark
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NegotiationScreen(
    navController: NavController
) {
    val scope = rememberCoroutineScope()
    val app = RidePilotApplication.instance
    val currentConfig by app.settingsRepository.negotiationConfig.collectAsState(initial = NegotiationConfig())

    var startMarginInput by remember(currentConfig) { mutableStateOf(currentConfig.startMarginEgp.toString()) }
    var stepInput by remember(currentConfig) { mutableStateOf(currentConfig.negotiationStepEgp.toString()) }
    var maxAttemptsInput by remember(currentConfig) { mutableStateOf(currentConfig.maxNegotiationAttempts.toString()) }
    var roundToSelected by remember(currentConfig) { mutableStateOf(currentConfig.roundToEgp) }
    var autoAccept by remember(currentConfig) { mutableStateOf(currentConfig.autoAccept) }
    var autoCounter by remember(currentConfig) { mutableStateOf(currentConfig.autoCounterOffer) }

    var saveSuccessMessage by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات التفاوض (inDrive)", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
            // Floor Minimum Notice Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AmberAccent)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = AmberAccent)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "الحد الأدنى (Floor): خط أحمر",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "يحسب تلقائياً من جدول تسعير inDrive. محظور تماماً على التطبيق تقديم أي عرض أقل منه تحت أي ظرف.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Margin & Step Form
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "قيم التفاوض والخطوات",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = startMarginInput,
                            onValueChange = { startMarginInput = it },
                            label = { Text("الهامش المبدئي فوق الحد الأدنى (EGP)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = BorderDark
                            )
                        )
                        Text(
                            text = "مثال: لو الحد الأدنى 72 ج.م والهامش 15 ج.م، أول عرض مقترح سيكون 87 ج.م (أو 90 بعد التقريب).",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = stepInput,
                            onValueChange = { stepInput = it },
                            label = { Text("قيمة التخفيض في كل محاولة / Step (EGP)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = BorderDark
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = maxAttemptsInput,
                            onValueChange = { maxAttemptsInput = it },
                            label = { Text("أقصى عدد لمحاولات التفاوض للطلب الواحد") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = BorderDark
                            )
                        )
                    }
                }
            }

            // Rounding Option
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "تقريب الأسعار المقترحة (Rounding)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1.0 to "1 ج.م", 5.0 to "5 ج.م", 10.0 to "10 ج.م").forEach { (step, label) ->
                                FilterChip(
                                    selected = roundToSelected == step,
                                    onClick = { roundToSelected = step },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldPrimary,
                                        selectedLabelColor = DarkBackground
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Automation Toggles
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
                                    text = "تقديم العرض المقابل تلقائياً (Auto Counter)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "عندما يكون عرض العميل أقل من الحد الأدنى، يقدم التطبيق سعراً أعلى تلقائياً",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = autoCounter,
                                onCheckedChange = { autoCounter = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "القبول التلقائي (Auto Accept)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "معطل افتراضياً. عند تفعيله، يقبل التطبيق الرحلة فوراً إذا كان عرض الراكب مناسباً.",
                                    fontSize = 11.sp,
                                    color = if (autoAccept) AmberAccent else TextSecondary
                                )
                            }
                            Switch(
                                checked = autoAccept,
                                onCheckedChange = { autoAccept = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary)
                            )
                        }
                    }
                }
            }

            // Save Button
            item {
                Button(
                    onClick = {
                        val margin = startMarginInput.toDoubleOrNull() ?: 15.0
                        val step = stepInput.toDoubleOrNull() ?: 5.0
                        val maxAttempts = maxAttemptsInput.toIntOrNull() ?: 3

                        scope.launch {
                            app.settingsRepository.updateNegotiationConfig(
                                NegotiationConfig(
                                    startMarginEgp = margin,
                                    negotiationStepEgp = step,
                                    maxNegotiationAttempts = maxAttempts,
                                    roundToEgp = roundToSelected,
                                    autoAccept = autoAccept,
                                    autoCounterOffer = autoCounter
                                )
                            )
                            saveSuccessMessage = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حفظ إعدادات التفاوض", fontWeight = FontWeight.Bold, color = DarkBackground, fontSize = 15.sp)
                }

                if (saveSuccessMessage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ تم حفظ إعدادات التفاوض بنجاح!",
                        fontSize = 12.sp,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
