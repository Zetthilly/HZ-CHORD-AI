import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BackgroundDark = Color(0xFF0A0C10)
val PanelDark = Color(0xFF13161C)
val NeonCyan = Color(0xFF06B6D4)
val NeonPurple = Color(0xFF8B5CF6)

@Composable
fun StemSeparationScreen(
    filePath: String,
    onBack: () -> Unit,
    onExportStem: (stemName: String) -> Unit,
    onSendToModule: (targetRoute: String, stemFilePath: String) -> Unit
) {
    val stems = listOf("Vocals", "Drums", "Bass", "Guitar", "Piano", "Other")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        Text(
            text = "STEM SEPARATION",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Source: $filePath",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        stems.forEach { stemName ->
            StemMixerRow(
                stemName = stemName,
                onExportClick = { onExportStem(stemName) },
                onSendToChordDetector = { 
                    val simulatedStemPath = "$filePath_${stemName.lowercase()}.wav"
                    onSendToModule(Routes.CHORD_DETECTOR, simulatedStemPath)
                },
                onSendToArpeggio = { 
                    val simulatedStemPath = "$filePath_${stemName.lowercase()}.wav"
                    onSendToModule(Routes.ARPEGGIO_DETECTOR, simulatedStemPath)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun StemMixerRow(
    stemName: String,
    onExportClick: () -> Unit,
    onSendToChordDetector: () -> Unit,
    onSendToArpeggio: () -> Unit
) {
    var volumeLevel by remember { mutableStateOf(0.8f) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = PanelDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = stemName,
                color = Color.White,
                modifier = Modifier.width(70.dp)
            )
            
            // Volume Slider
            Slider(
                value = volumeLevel,
                onValueChange = { volumeLevel = it },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = NeonPurple,
                    activeTrackColor = NeonPurple.copy(alpha = 0.8f),
                    inactiveTrackColor = Color.DarkGray
                )
            )
            
            // Direct Export Button
            IconButton(onClick = onExportClick) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Export $stemName",
                    tint = NeonCyan
                )
            }

            // Options Menu for "Send To" Module File Exchange
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = Color.White
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(PanelDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Send to Chord Detector", color = Color.White) },
                        onClick = {
                            showMenu = false
                            onSendToChordDetector()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Send to Arpeggio Detector", color = Color.White) },
                        onClick = {
                            showMenu = false
                            onSendToArpeggio()
                        }
                    )
                }
            }
        }
    }
}
