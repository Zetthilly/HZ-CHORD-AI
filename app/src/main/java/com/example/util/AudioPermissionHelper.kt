package com.example.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object AudioPermissionHelper {

    const val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO

    /**
     * Checks if RECORD_AUDIO permission is currently granted.
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            RECORD_AUDIO_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if we should display an educational rationale before requesting permission.
     */
    fun shouldShowPermissionRationale(activity: Activity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            RECORD_AUDIO_PERMISSION
        )
    }

    /**
     * Opens the application's system settings details screen so the user can manually
     * toggle microphone recording permissions if they previously selected "Don't ask again".
     */
    fun launchAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/**
 * A beautifully designed Cosmic-themed Dialog that provides educational rationale to the user.
 * Built with Material Design 3 and aligned with HZ CHORD AI's branding.
 */
@Composable
fun AudioPermissionRationaleDialog(
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    isPermanentlyDenied: Boolean
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D1726), // Midnight Slate
                            Color(0xFF030A16)  // Deep Navy
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF00B7FF).copy(alpha = 0.5f), // Electric Blue
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Microphone icon with a glowing indigo circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = Color(0xFF00B7FF).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Microphone Access Required",
                        tint = Color(0xFF00F0FF), // Neon Cyan
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Microphone Access Required",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isPermanentlyDenied) {
                        "Microphone permission has been permanently denied. To enable real-time chord detection, frequency analysis, and the guitar tuner, please click 'Open Settings' and grant microphone access."
                    } else {
                        "HZ CHORD AI requires access to your microphone to capture real-time guitar strumming, vocals, or keyboard playback for instantaneous chord identification, stem extraction, and frequency tutoring."
                    },
                    color = Color(0xFFC9D1D9), // Text Silver
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // Subtitle/Note about safety and offline privacy
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF6D4CFF).copy(alpha = 0.1f) // Cosmic Purple
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Privacy Information",
                            tint = Color(0xFF6D4CFF), // Cosmic Purple
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "100% Offline: Your captured audio is processed entirely local on-device. No audio data ever leaves this workstation.",
                            color = Color(0xFFC9D1D9),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel Button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFC9D1D9)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFC9D1D9).copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    // Action Button
                    Button(
                        onClick = {
                            if (isPermanentlyDenied) {
                                onOpenSettings()
                            } else {
                                onRequestPermission()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00B7FF), // Electric Blue
                            contentColor = Color.Black
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isPermanentlyDenied) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Open Settings",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Settings",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            } else {
                                Text(
                                    text = "Grant",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
