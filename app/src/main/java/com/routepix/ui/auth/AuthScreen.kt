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

@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val infiniteTransition = rememberInfiniteTransition()
    val scrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            
            val c1 = MaterialTheme.colorScheme.primaryContainer
            val c2 = MaterialTheme.colorScheme.secondaryContainer
            val c3 = MaterialTheme.colorScheme.tertiaryContainer
            val c4 = MaterialTheme.colorScheme.surfaceVariant
            val c5 = MaterialTheme.colorScheme.primary
            val c6 = MaterialTheme.colorScheme.secondary
            val c7 = MaterialTheme.colorScheme.tertiary

            val colors = listOf(c1, c2, c3, c4, c5, c6, c7)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxWidth = 140.dp.toPx()
                val spacing = 16.dp.toPx()
                val totalW = boxWidth + spacing
                // Move from top-right to bottom-left
                val shift = scrollOffset % totalW
                translate(left = -shift, top = shift) {
                    for (i in -2..((size.width / totalW).toInt() + 3)) {
                        for (j in -4..((size.height / totalW).toInt() + 2)) {
                            // Pseudo-random but deterministic size multiplier for staggered grid
                            val randVariant = (i * 17 + j * 31).absoluteValue % 3
                            val boxHeight = boxWidth * (1f + randVariant * 0.5f)
                            
                            val colorIdx = (i * 7 + j * 11).absoluteValue % colors.size
                            val topOffset = j * totalW + (if (i % 2 != 0) totalW / 2 else 0f)

                            drawRoundRect(
                                color = colors[colorIdx].copy(alpha = 0.4f),
                                topLeft = Offset(i * totalW, topOffset),
                                size = Size(boxWidth, boxHeight),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                        }
                    }
                }
            }
            
            // Content layer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(32.dp)
            ) {
                Text(
                    text = "RoutePix",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Share your trip photos\nvia Telegram",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(48.dp))

                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                } else if (authState !is AuthState.Success) {
                    Button(
                        onClick = {
                            signInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "Continue with Google",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

