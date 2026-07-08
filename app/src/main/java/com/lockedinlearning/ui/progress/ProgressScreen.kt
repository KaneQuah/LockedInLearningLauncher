package com.lockedinlearning.ui.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lockedinlearning.ui.theme.CorrectGreen
import com.lockedinlearning.ui.theme.Primary
import java.text.SimpleDateFormat
import java.util.*

// ---------------------------------------------------------------------------
// S-10 — Progress Dashboard
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            // Streak row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCard(emoji = "🔥", label = "Streak", value = "${state.currentStreak} days")
                Spacer(Modifier.width(12.dp))
                StatCard(emoji = "⭐", label = "Best", value = "${state.bestStreak} days")
            }

            Spacer(Modifier.height(24.dp))
            Text("This week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            // Weekly bar chart
            WeeklyBarChart(state.weeklyData)

            Spacer(Modifier.height(24.dp))
            Text("Deck Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            state.deckStats.forEach { deck ->
                DeckMasteryRow(deck)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(24.dp))
            Text("Lifetime: ${state.totalCorrect} correct answers",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Bypasses: ${state.bypasses}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RowScope.StatCard(emoji: String, label: String, value: String) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 32.sp)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeeklyBarChart(data: List<DayBar>) {
    val maxVal = data.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())

    Row(
        Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { bar ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text("${bar.count}", style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((bar.count.toFloat() / maxVal * 80).dp)
                        .then(
                            Modifier.padding(horizontal = 2.dp)
                        )
                ) {
                    Surface(
                        color = if (bar.isToday) Primary else CorrectGreen.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.small
                    ) {}
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dayFmt.format(Date(bar.epochDay * 86_400_000L)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bar.isToday) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeckMasteryRow(deck: DeckStat) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(deck.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { deck.masteryPercent / 100f },
            modifier = Modifier.width(120.dp).height(8.dp),
            color = CorrectGreen
        )
        Spacer(Modifier.width(8.dp))
        Text("${deck.masteryPercent}%", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
