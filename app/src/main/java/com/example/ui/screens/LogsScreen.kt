package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.RidePilotApplication
import com.example.data.model.AppLogEntry
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCard
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = RidePilotApplication.instance
    val logs by app.logRepository.getRecentLogs().collectAsState(initial = emptyList())

    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل العمليات (Logs)", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                    }
                },
                actions = {
                    // Export Logs
                    IconButton(
                        onClick = {
                            scope.launch {
                                val allLogs = app.logRepository.getAllLogs()
                                val sb = StringBuilder()
                                sb.append("=== RidePilot Logs Export ===\n\n")
                                for (l in allLogs) {
                                    sb.append("${timeFormatter.format(Date(l.timestamp))} | ${l.app.name} | ${l.action}\n")
                                    sb.append("السعر: ${l.detectedPrice ?: "-"} | المسافة: ${l.detectedDistance ?: "-"} | الحد: ${l.minCalculated ?: "-"}\n")
                                    sb.append("المنطقة: ${l.zoneResult}\n")
                                    sb.append("السبب: ${l.reason} (ثقة: ${l.confidence}%)\n")
                                    sb.append("-----------------------------\n")
                                }
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, sb.toString())
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "تصدير السجل"))
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "تصدير", tint = EmeraldPrimary)
                    }

                    // Clear Logs
                    IconButton(
                        onClick = {
                            scope.launch {
                                app.logRepository.clearLogs()
                            }
                        }
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "مسح السجل", tint = DangerRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد عمليات مسجلة حتى الآن",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(logs) { log ->
                    LogCard(log = log, timeFormatter = timeFormatter)
                }
            }
        }
    }
}

@Composable
fun LogCard(
    log: AppLogEntry,
    timeFormatter: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val appColor = if (log.app.name == "UBER") AmberAccent else InfoBlue
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(appColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = log.app.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = appColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.action,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = timeFormatter.format(Date(log.timestamp)),
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (log.detectedPrice != null) {
                    Text(
                        text = "السعر: ${log.detectedPrice} ج.م",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                if (log.detectedDistance != null) {
                    Text(
                        text = "المسافة: ${log.detectedDistance} كم",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Text(
                    text = "الثقة: ${log.confidence}%",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.reason,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}
