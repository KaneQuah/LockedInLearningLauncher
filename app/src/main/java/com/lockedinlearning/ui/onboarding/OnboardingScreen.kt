package com.lockedinlearning.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lockedinlearning.ui.theme.Primary

// ---------------------------------------------------------------------------
// Onboarding — S-01 and S-02
// ---------------------------------------------------------------------------
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (state.step) {
        OnboardingStep.LAUNCHER_SETUP -> LauncherSetupPage(
            onOpenSettings = {
                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            },
            onAlreadySet = { viewModel.advance() }
        )
        OnboardingStep.CREATE_DECK -> CreateDeckPage(
            onCreateSample = { viewModel.createSampleDeck(); onComplete() },
            onSkip = { onComplete() }
        )
        OnboardingStep.DONE -> { onComplete() }
    }
}

@Composable
private fun LauncherSetupPage(onOpenSettings: () -> Unit, onAlreadySet: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🧠", fontSize = 72.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "LockedInLearning",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Every time you unlock your phone, answer one question. Build a habit. Master anything.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Set as Default Launcher")
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onAlreadySet) {
            Text("Already set — continue →")
        }
    }
}

@Composable
private fun CreateDeckPage(onCreateSample: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📚", fontSize = 72.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Let's add some questions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Start with a sample deck of World Capitals, or import your own CSV.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onCreateSample,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Use Sample Deck")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("I'll add my own later")
        }
    }
}
