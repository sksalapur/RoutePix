@file:OptIn(ExperimentalMaterial3Api::class)

package com.routepix.ui.join

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.routepix.ui.components.GlassTopBar
import com.routepix.ui.components.RoutepixLoader
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun JoinTripScreen(
    onTripJoined: (tripId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: JoinTripViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    var inviteCode by rememberSaveable { mutableStateOf("") }
    val isFormValid = inviteCode.length == 6
    val errorMessage = (state as? JoinTripState.Error)?.message

    // Screen Entry Animation
    val entryProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entryProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(state) {
        val s = state
        if (s is JoinTripState.Success) {
            onTripJoined(s.tripId)
        }
    }

    Scaffold(
        topBar = {
            GlassTopBar(
                title = { Text("Join Trip", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entryProgress.value
                    translationY = (1f - entryProgress.value) * 40.dp.toPx()
                }
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Enter the 6-character invite code shared by the trip admin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = inviteCode,
                onValueChange = { value ->
                    if (value.length <= 6) {
                        inviteCode = value.uppercase()

                        if (state is JoinTripState.Error) viewModel.clearError()
                    }
                },
                label = { Text("Invite Code") },
                placeholder = { Text("ABC123") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errorMessage != null,
                supportingText = if (errorMessage != null) {
                    { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                } else null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val buttonScale by animateFloatAsState(
                targetValue = if (isPressed) 0.96f else 1f,
                label = "button_scale"
            )

            Button(
                onClick = { viewModel.joinTrip(inviteCode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    },
                interactionSource = interactionSource,
                shape = RoundedCornerShape(16.dp),
                enabled = isFormValid && state !is JoinTripState.Loading
            ) {
                if (state is JoinTripState.Loading) {
                    RoutepixLoader(
                        modifier = Modifier.size(24.dp),
                        speed = 1800
                    )
                } else {
                    Text("Join Trip", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

