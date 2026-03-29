package com.routepix.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.absoluteValue
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.graphicsLayer
import com.routepix.ui.components.FullScreenLoaderOverlay
import com.routepix.ui.components.GlassCard
import com.routepix.ui.components.RoutepixLoader

@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Screen Entry Animation
    val entryProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entryProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bg_scroll")
    val scrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bg_offset"
    )

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(com.routepix.R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    authViewModel.signInWithGoogle(idToken)
                }
            } catch (_: ApiException) {
            }
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onNavigateToHome()
        }
    }

    LaunchedEffect(authState) {
        val state = authState
        if (state is AuthState.Error) {
            snackbarHostState.showSnackbar(state.message)
            authViewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            // Ambient animated background
            val c1 = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            val c2 = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            val c3 = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            val colors = listOf(c1, c2, c3)
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxWidth = 140.dp.toPx()
                val spacing = 16.dp.toPx()
                val totalW = boxWidth + spacing
                val shift = scrollOffset % totalW
                translate(left = -shift, top = shift) {
                    for (i in -2..((size.width / totalW).toInt() + 3)) {
                        for (j in -4..((size.height / totalW).toInt() + 2)) {
                            val randVariant = (i * 17 + j * 31).absoluteValue % 3
                            val boxHeight = boxWidth * (1f + randVariant * 0.5f)
                            val colorIdx = (i * 7 + j * 11).absoluteValue % colors.size
                            val topOffset = j * totalW + (if (i % 2 != 0) totalW / 2 else 0f)

                            drawRoundRect(
                                color = colors[colorIdx],
                                topLeft = Offset(i * totalW, topOffset),
                                size = Size(boxWidth, boxHeight),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                        }
                    }
                }
            }
            
            // Content layer with Entry Animation (Fade + Slide Up)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .graphicsLayer {
                        alpha = entryProgress.value
                        translationY = (1f - entryProgress.value) * 40.dp.toPx()
                    },
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    modifier = Modifier.wrapContentSize(),
                    cornerRadius = 32.dp,
                    opacity = 0.75f
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(36.dp)
                    ) {
                        Text(
                            text = "RoutePix",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Share your trip photos\nvia Telegram",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        if (authState !is AuthState.Success) {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val scale by animateFloatAsState(
                                targetValue = if (isPressed) 0.93f else 1f,
                                label = "button_scale"
                            )

                            Button(
                                onClick = {
                                    signInLauncher.launch(googleSignInClient.signInIntent)
                                },
                                interactionSource = interactionSource,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Continue with Google",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            
            // Full-screen overlay when loading
            if (authState is AuthState.Loading) {
                FullScreenLoaderOverlay(label = "Signing in...")
            }
        }
    }
}

