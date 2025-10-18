package com.trustvault.android.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trustvault.android.security.TotpGenerator
import kotlinx.coroutines.delay
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

/**
 * Composable for displaying TOTP code for a credential.
 *
 * Features:
 * - Displays current TOTP code with visual countdown
 * - Auto-refreshes every second
 * - Copy to clipboard button
 * - Shows progress indicator for time remaining
 *
 * @param otpSecret Base32-encoded TOTP secret (or null if TOTP not configured)
 * @param modifier Modifier for the card
 * @param credentialTitle Title of the credential (for UI context)
 */
@Composable
fun TotpDisplayCard(
    otpSecret: String?,
    modifier: Modifier = Modifier,
    credentialTitle: String = ""
) {
    if (otpSecret.isNullOrBlank()) {
        return
    }

    val totpGenerator = remember { TotpGenerator() }
    val context = LocalContext.current

    var totpCode by remember { mutableStateOf("------") }
    var remainingSeconds by remember { mutableStateOf(30) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Update TOTP code every second
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val now = System.currentTimeMillis() / 1000
                val result = totpGenerator.generate(otpSecret, timeSeconds = now)
                totpCode = result.code
                remainingSeconds = result.remainingSeconds
                isRefreshing = remainingSeconds <= 5 // Flash when about to refresh
            } catch (e: Exception) {
                // Silently handle invalid secrets
                totpCode = "ERROR"
            }
            delay(1000)
        }
    }

    // Animate progress bar
    val progress by animateFloatAsState(
        targetValue = remainingSeconds.toFloat() / 30.0f,
        label = "TOTP Progress"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.VpnKey,
                        contentDescription = "TOTP",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "2FA Code",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Refresh indicator
                if (isRefreshing) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refreshing",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // TOTP Code Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = totpCode,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 42.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Progress bar
            Column(modifier = Modifier.padding(top = 12.dp)) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    color = if (isRefreshing) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Expires in: ${remainingSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Copy button
                    IconButton(
                        onClick = {
                            try {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                val clip = ClipData.newPlainText("TOTP Code", totpCode)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to copy", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy code",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Preview for TotpDisplayCard
 */
@Composable
fun TotpDisplayCardPreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            TotpDisplayCard(
                otpSecret = "GEZDGNBVGY3TQOJQ",
                credentialTitle = "Gmail"
            )
        }
    }
}
