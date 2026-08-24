package com.example.ui

import androidx.compose.foundation.layout.offset
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.DetectedChordInfo
import com.example.audio.StemSeparationState
import com.example.audio.TuningNote
import com.example.audio.RealAudioDecoder
import com.example.data.ImportedAudioMetadata
import com.example.data.ProjectSession
import com.example.data.GuitarLick
import com.example.viewmodel.WorkstationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Bg = Color(0xFF030A16)
private val Panel = Color(0xFF071321)
private val Panel2 = Color(0xFF0A1728)
private val Cyan = Color(0xFF13BFFF)
private val Blue = Color(0xFF2C7DFF)
private val Purple = Color(0xFF9B3DFF)
private val Gold = Color(0xFFFFC52E)
private val Green = Color(0xFF39E58C)
private val Pink = Color(0xFFFF3D91)
private val TextMain = Color(0xFFF4F7FB)
private val TextMuted = Color(0xFF8E9AAF)

private enum class Screen { SPLASH, DASHBOARD, MODULES, CHORD, KEYBOARD, ARPEGGIO, BPM_KEY, STEMS, TRANSCRIPTION, TUNER, LIBRARY, SETTINGS }

@Composable
fun HZChordAiApp(viewModel: WorkstationViewModel) {
    var screen by remember { mutableStateOf(Screen.SPLASH) }
    var splashVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2600)
        splashVisible = false
        screen = Screen.DASHBOARD
    }
    if (splashVisible) {
        SplashScreen()
        return
    }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
        Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
            AppScaffold(screen, onNavigate = { screen = it }, viewModel = viewModel)
        }
    }
}

@Composable
private fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(0.82f, 1.18f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF0B2347), Bg), radius = 1000f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(210.dp)) {
                    for (ring in 0..2) {
                        drawCircle(if (ring == 1) Purple.copy(alpha = .45f) else Cyan.copy(alpha = .25f), radius = size.minDimension * (.32f + ring * .12f) * pulse, style = Stroke(width = 2f))
                    }
                    for (i in 0 until 80) {
                        val a = i * 2f * PI / 80f
                        val r1 = size.minDimension * .41f
                        val r2 = r1 + size.minDimension * (.025f + (i % 7) * .006f)
                        drawLine(Cyan.copy(alpha = .25f + (i % 4) * .12f), Offset(size.width/2 + cos(a).toFloat()*r1, size.height/2 + sin(a).toFloat()*r1), Offset(size.width/2 + cos(a).toFloat()*r2, size.height/2 + sin(a).toFloat()*r2), strokeWidth = 2f, cap = StrokeCap.Round)
                    }
                }
                Text("HZ", fontSize = 64.sp, fontWeight = FontWeight.Black, color = Gold, letterSpacing = 3.sp)
            }
            Text("CHORD AI", fontSize = 33.sp, fontWeight = FontWeight.Light, color = TextMain, letterSpacing = 5.sp)
            Spacer(Modifier.height(28.dp))
            Text("Hear the Notes.\nUnderstand the Music.", color = TextMain, fontSize = 17.sp, lineHeight = 26.sp, textAlign = TextAlign.Center)
            Text("Powered by AI.", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(44.dp))
            Text("Designed & Built by", color = TextMuted, fontSize = 13.sp)
            Text("Joseph Hilary Zulukwa", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("© Joseph Hilary Zulukwa. All Rights Reserved.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun AppScaffold(screen: Screen, onNavigate: (Screen) -> Unit, viewModel: WorkstationViewModel) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (screen) {
                Screen.DASHBOARD -> DashboardScreen(viewModel, onNavigate)
                Screen.MODULES -> ModulesScreen(onNavigate)
                Screen.CHORD -> ChordDetectorScreen(viewModel, onNavigate)
                Screen.KEYBOARD -> KeyboardFretboardScreen(viewModel, onNavigate)
                Screen.ARPEGGIO -> ArpeggioScreen(viewModel, onNavigate)
                Screen.BPM_KEY -> BpmKeyScreen(viewModel, onNavigate)
                Screen.STEMS -> StemScreen(viewModel, onNavigate)
                Screen.TRANSCRIPTION -> TranscriptionScreen(viewModel, onNavigate)
                Screen.TUNER -> TunerScreen(viewModel, onNavigate)
                Screen.LIBRARY -> LibraryScreen(viewModel, onNavigate)
                Screen.SETTINGS -> SettingsScreen(onNavigate)
                else -> DashboardScreen(viewModel, onNavigate)
            }
        }
        BottomBar(screen, onNavigate)
    }
}

@Composable
private fun BottomBar(screen: Screen, onNavigate: (Screen) -> Unit) {
    Surface(color = Color(0xFF050D18), tonalElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            BottomItem("Dashboard", Icons.Default.Home, screen == Screen.DASHBOARD) { onNavigate(Screen.DASHBOARD) }
            BottomItem("Modules", Icons.Default.GridView, screen !in setOf(Screen.DASHBOARD, Screen.LIBRARY, Screen.SETTINGS)) { onNavigate(Screen.MODULES) }
            BottomItem("Library", Icons.Default.LibraryMusic, screen == Screen.LIBRARY) { onNavigate(Screen.LIBRARY) }
            BottomItem("Settings", Icons.Default.Settings, screen == Screen.SETTINGS) { onNavigate(Screen.SETTINGS) }
        }
    }
}

@Composable
private fun BottomItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 4.dp)) {
        Icon(icon, null, tint = if (active) Purple else TextMuted, modifier = Modifier.size(22.dp))
        Text(label, color = if (active) TextMain else TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextMain) }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (title == "HZ CHORD AI") Gold else TextMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Icon(Icons.Default.Info, null, tint = TextMuted)
    }
}


@Composable
private fun DashboardScreen(vm: WorkstationViewModel, nav: (Screen) -> Unit) {
    val fileName by vm.uploadedFileName.collectAsStateWithLifecycle()
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val key by vm.globalKeySignature.collectAsStateWithLifecycle()
    val chord by vm.currentChord.collectAsStateWithLifecycle()
    val playing by vm.masterIsPlaying.collectAsStateWithLifecycle()
    val position by vm.masterCurrentPositionMs.collectAsStateWithLifecycle()
    val duration by vm.masterDurationMs.collectAsStateWithLifecycle()
    val metadata by vm.lastImportedAudioMetadata.collectAsStateWithLifecycle()
    val sessions by vm.allSessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val importLauncher = rememberAudioLauncher(context, vm)
    val recordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleRecording()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Menu, null, tint = TextMain, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("HZ CHORD AI", color = Gold, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Text("MUSIC ANALYSIS WORKSTATION", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Text("PRO", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("NOW PLAYING")
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF1A3A76), Purple))), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Cyan) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(fileName ?: "No audio loaded", color = TextMain, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (duration > 0) "${timeText(position)} / ${timeText(duration)}" else "Import an audio file to begin", color = TextMuted, fontSize = 11.sp)
                }
                SmallAction("OPEN", Icons.Default.FolderOpen, Purple) { importLauncher.launch("audio/*") }
                Spacer(Modifier.width(7.dp))
                SmallAction(if (playing) "PAUSE" else "PLAY", if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, Purple) { vm.toggleMasterPlayback() }
            }
            Spacer(Modifier.height(12.dp))
            Waveform(amplitudes = metadata?.waveformAmplitudes, progress = if (duration > 0) position.toFloat() / duration else 0f, accent = Cyan)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("CURRENT CHORD", chord?.name ?: "—", Cyan, Modifier.weight(1.3f))
            MetricCard("BPM", bpm.toString(), Purple, Modifier.weight(.8f))
            MetricCard("KEY", key ?: "—", Gold, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("QUICK ACTIONS")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("Quick Record", Icons.Default.Mic, Pink) { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.toggleRecording() else recordPermission.launch(Manifest.permission.RECORD_AUDIO) }
            QuickAction("Live Analyzer", Icons.Default.GraphicEq, Purple) { nav(Screen.CHORD) }
            QuickAction("Import Audio", Icons.Default.FolderOpen, Gold) { importLauncher.launch("audio/*") }
        }
        Spacer(Modifier.height(16.dp))
        SectionTitle("RECENT PROJECTS")
        if (sessions.isEmpty()) {
            GlassCard { Text("No projects yet. Import audio to create your first analysis session.", color = TextMuted, fontSize = 13.sp) }
        } else {
            sessions.take(5).forEach { SessionRow(it) }
        }
        Spacer(Modifier.height(18.dp))
        SectionTitle("RECOMMENDED WORKFLOW")
        WorkflowCard(nav)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable private fun SessionRow(session: ProjectSession) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MusicNote, null, tint = Cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(session.title, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${session.keySignature} • ${session.bpm} BPM", color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable private fun WorkflowCard(nav: (Screen) -> Unit) {
    GlassCard {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            WorkflowStep("1", "Import", Icons.Default.FolderOpen, Purple) { nav(Screen.DASHBOARD) }
            Arrow()
            WorkflowStep("2", "Stems", Icons.Default.Layers, Green) { nav(Screen.STEMS) }
            Arrow()
            WorkflowStep("3", "Chords", Icons.Default.GridView, Cyan) { nav(Screen.CHORD) }
            Arrow()
            WorkflowStep("4", "Transcribe", Icons.Default.MusicNote, Pink) { nav(Screen.TRANSCRIPTION) }
            Arrow()
            WorkflowStep("5", "Export", Icons.Default.Build, Gold) { nav(Screen.LIBRARY) }
        }
    }
}

@Composable private fun WorkflowStep(number: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = .13f)).border(1.dp, color.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(21.dp)) }
        Text(number, color = TextMuted, fontSize = 9.sp)
        Text(label, color = TextMain, fontSize = 9.sp)
    }
}
@Composable private fun Arrow() { Text("→", color = TextMuted, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 4.dp)) }

@Composable
private fun ModulesScreen(nav: (Screen) -> Unit) {
    val modules = listOf(
        Triple("CHORD DETECTOR", "Real-time chord recognition and visualization", Purple) to Screen.CHORD,
        Triple("ARPEGGIO DETECTOR", "Detect note sequences and playing patterns", Gold) to Screen.ARPEGGIO,
        Triple("BPM & KEY DETECTOR", "Measure tempo and estimate the musical key", Blue) to Screen.BPM_KEY,
        Triple("STEM SEPARATION", "On-device harmonic/percussive stem isolation", Green) to Screen.STEMS,
        Triple("TRANSCRIPTION", "Display detected chords and notes", Pink) to Screen.TRANSCRIPTION,
        Triple("TUNER", "Chromatic tuner from microphone input", Pink) to Screen.TUNER,
        Triple("THEORY ASSISTANT", "Chords, scales and music theory tools", Gold) to Screen.KEYBOARD,
        Triple("AUDIO TOOLS", "Keyboard, fretboard and playback controls", Cyan) to Screen.KEYBOARD
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ScreenHeader("HZ CHORD AI", "MODULE LAUNCHER")
        Text("ALL MODULES", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        for (row in modules.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (data, target) -> ModuleCard(data.first, data.second, data.third, Modifier.weight(1f)) { nav(target) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        SectionTitle("RECOMMENDED WORKFLOW")
        WorkflowCard(nav)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable private fun ModuleCard(title: String, desc: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.height(176.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Panel), border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = .35f)), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(color.copy(alpha = .10f)).border(1.dp, color.copy(alpha = .7f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, tint = color, modifier = Modifier.size(30.dp)) }
            Column {
                Text(title, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(desc, color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 3)
            }
        }
    }
}

@Composable private fun ChordDetectorScreen(vm: WorkstationViewModel, nav: (Screen) -> Unit) {
    val chord by vm.currentChord.collectAsStateWithLifecycle()
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val key by vm.globalKeySignature.collectAsStateWithLifecycle()
    val timeline by vm.chordTimeline.collectAsStateWithLifecycle()
    val metadata by vm.lastImportedAudioMetadata.collectAsStateWithLifecycle()
    val playing by vm.masterIsPlaying.collectAsStateWithLifecycle()
    val duration by vm.masterDurationMs.collectAsStateWithLifecycle()
    val position by vm.masterCurrentPositionMs.collectAsStateWithLifecycle()
    ModulePage("Chord Detector", "AI CHORD RECOGNITION", nav, Screen.CHORD) {
        ChordHero(chord)
        GlassCard { Waveform(metadata?.waveformAmplitudes, if (duration > 0) position.toFloat()/duration else 0f, Cyan) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("BPM", bpm.toString(), Purple, Modifier.weight(1f))
            MetricCard("KEY", key ?: "—", Gold, Modifier.weight(1f))
            MetricCard("CONFIDENCE", chord?.confidence?.let { "${(it*100).toInt()}%" } ?: "—", Green, Modifier.weight(1f))
        }
        GlassCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabPill("CHORDS", true, Cyan) { }
                TabPill("KEYBOARD", false, Purple) { nav(Screen.KEYBOARD) }
                TabPill("FRETBOARD", false, Gold) { nav(Screen.KEYBOARD) }
            }
            Spacer(Modifier.height(12.dp))
            if (timeline.isEmpty()) Text("Chord timeline will populate from real-time analysis while audio plays.", color = TextMuted, fontSize = 12.sp)
            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { timeline.takeLast(10).forEach { ChordChip(it.name, it.confidence) } }
        }
        PlayerControls(playing, { vm.toggleMasterPlayback() }, { vm.setPlaybackPosition((position - 5000).coerceAtLeast(0)) }, { vm.setPlaybackPosition((position + 5000).coerceAtMost(duration)) })
    }
}

@Composable private fun ChordHero(chord: DetectedChordInfo?) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CURRENT CHORD", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(chord?.name ?: "—", color = TextMain, fontSize = 36.sp, fontWeight = FontWeight.Light)
                Text(chord?.type ?: "Waiting for audio", color = TextMuted, fontSize = 12.sp)
            }
            CircularConfidence(chord?.confidence ?: 0f)
        }
    }
}

@Composable private fun CircularConfidence(value: Float) {
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawArc(Cyan.copy(alpha = .15f), -90f, 360f, false, style = Stroke(5f)); drawArc(Cyan, -90f, 360f * value.coerceIn(0f,1f), false, style = Stroke(5f)) }
        Text(if (value > 0) "${(value*100).toInt()}%" else "—", color = TextMain, fontSize = 12.sp)
    }
}

@Composable private fun KeyboardFretboardScreen(vm: WorkstationViewModel, nav: (Screen) -> Unit) {
    val chord by vm.currentChord.collectAsStateWithLifecycle()
    ModulePage("Chord", "KEYBOARD + FRETBOARD", nav, Screen.KEYBOARD) {
        Text(chord?.name ?: "No chord detected", color = Cyan, fontSize = 27.sp, fontWeight = FontWeight.Medium)
        Text(chord?.notes?.joinToString("  •  ") ?: "Import or record audio to show detected notes", color = TextMuted, fontSize = 12.sp)
        GlassCard { PianoKeyboard(chord?.notes ?: emptyList()) }
        GlassCard { Fretboard(chord?.notes ?: emptyList()) }
        PlayerControls(vm.masterIsPlaying.collectAsStateWithLifecycle().value, { vm.toggleMasterPlayback() }, {}, {})
    }
}

@Composable private fun PianoKeyboard(notes: List<String>) {
    val white = listOf("C","D","E","F","G","A","B","C","D","E","F","G","A","B")
    val black = mapOf(0 to "C#", 1 to "D#", 3 to "F#", 4 to "G#", 5 to "A#", 7 to "C#", 8 to "D#", 10 to "F#", 11 to "G#", 12 to "A#")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(116.dp), verticalAlignment = Alignment.Top) {
        Box {
            Row { white.forEach { note -> Box(Modifier.width(43.dp).height(112.dp).border(1.dp, Color(0xFF4B5563)).background(if (notes.any { it.startsWith(note) }) Blue.copy(alpha=.85f) else Color(0xFFE9EDF2)).padding(top = 84.dp), contentAlignment = Alignment.Center) { Text(note, color = if (notes.any { it.startsWith(note) }) TextMain else Color(0xFF263142), fontSize = 10.sp) } } }
            black.forEach { (index, note) -> Box(Modifier.offset(x = (index*43 + 27).dp).width(28.dp).height(65.dp).background(Color(0xFF101722), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)), contentAlignment = Alignment.BottomCenter) { Text(if (notes.any { it.startsWith(note) }) "●" else "", color = Cyan, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp)) } }
        }
    }
}

@Composable private fun Fretboard(notes: List<String>) {
    val strings = listOf("E","B","G","D","A","E")
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { strings.forEach { Text(it, color = TextMuted, fontSize = 10.sp) } }
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            for (s in 0..5) drawLine(Color(0xFF6E553F), Offset(0f, 12f+s*24f), Offset(size.width, 12f+s*24f), 2f)
            for (f in 0..12) drawLine(Color(0xFF303A45), Offset(f*size.width/12f,0f), Offset(f*size.width/12f,size.height), if (f==0) 5f else 1f)
            notes.forEachIndexed { i, _ -> val x = ((i % 6 + 2) * size.width/12f); val y = ((i % 6) * 24f)+12f; drawCircle(Purple, 10f, Offset(x,y)) }
        }
    }
}

@Composable private fun ArpeggioScreen(vm: WorkstationViewModel, nav: (Screen) -> Unit) {
    val chord by vm.currentChord.collectAsStateWithLifecycle()
    val arpeggio by vm.detectedArpeggio.collectAsStateWithLifecycle()
    val notes by vm.liveNotesBuffer.collectAsStateWithLifecycle()
    val metadata by vm.lastImportedAudioMetadata.collectAsStateWithLifecycle()
    ModulePage("Arpeggio Detector", "NOTE ORDER + PATTERN ANALYSIS", nav, Screen.ARPEGGIO) {
        GlassCard {
            Text(arpeggio ?: "No arpeggio detected", color = Purple, fontSize = 25.sp)
            Text(chord?.name ?: "Waiting for harmonic context", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp)); Waveform(metadata?.waveformAmplitudes, .45f, Purple)
        }
        GlassCard {
            SectionTitle("DETECTED NOTES")
            if (notes.isEmpty()) Text("Notes appear here from the live harmonic transcriber.", color = TextMuted, fontSize = 12.sp)
            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { notes.forEach { ChordChip(it, 1f) } }
        }
        PlayerControls(vm.masterIsPlaying.collectAsStateWithLifecycle().value, { vm.toggleMasterPlayback() }, {}, {})
    }
}

@Composable private fun BpmKeyScreen(vm: WorkstationViewModel, nav: (Screen) -> Unit) {
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val key by vm.globalKeySignature.collectAsStateWithLifecycle()
    val analyzingBpm by vm.isAnalyzingTempo.collectAsStateWithLifecycle()
    val analyzingKey by vm.isAnalyzingKey.collectAsStateWithLifecycle()
    ModulePage("BPM & Key", "REAL AUDIO ANALYSIS", nav, Screen.BPM_KEY) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GaugeCard("BPM", bpm.toString(), Purple, Modifier.weight(1f))
            GaugeCard("KEY", key ?: "—", Gold, Modifier.weight(1f))
        }
        GlassCard {
            Text(if (analyzingBpm || analyzingKey) "Analyzing decoded audio…" else "Analysis is performed from decoded PCM audio, not from the file name.", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            if (analyzingBpm) ProgressBar(vm.tempoDetectionProgress.collectAsStateWithLifecycle().value, Purple)
            if (analyzingKey) ProgressBar(vm.keyAnalysisProgress.collectAsStateWithLifecycle().value, Cyan)
        }
        GlassCard {
            Text("TAP TEMPO", color = TextMain, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text("Use the live analyzer for a manual tap tempo when no track is loaded.", color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top=6.dp))
        }
    }
}

@Composable private fun GaugeCard(label: String, value: String, color: Color, modifier: Modifier) {
    GlassCard(modifier) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Text(label, color = color, fontSize = 11.sp); Spacer(Modifier.height(14.dp)); Text(value, color = TextMain, fontSize = 30.sp); Text(if (label == "BPM") tempoLabel(value.toIntOrNull()) else "Detected", color = TextMuted, fontSize = 12.sp) } }
}

@Composable private fun StemScreen(vm: WorkstationViewModel, nav: (Screen) -> Unit) {
    val state by vm.stemSeparation.collectAsStateWithLifecycle()
    val file by vm.uploadedFileName.collectAsStateWithLifecycle()
    ModulePage("Stem Separation", "ON-DEVICE AUDIO SEPARATION", nav, Screen.STEMS) {
        GlassCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MusicNote, null, tint = Green); Spacer(Modifier.width(8.dp)); Text(file ?: "No audio loaded", color = TextMain, modifier = Modifier.weight(1f)); TextButton(onClick = { vm.runStemSeparation("balanced") }) { Text("RUN", color = Green) } } }
        when (state) {
            StemSeparationState.Idle -> GlassCard { Text("No stem separation run yet.", color = TextMuted) }
            is StemSeparationState.Processing -> { val p = state as StemSeparationState.Processing; GlassCard { Text("SEPARATION PROGRESS", color = Green, fontSize = 11.sp); ProgressBar(p.progress, Green); Text("${(p.progress*100).toInt()}%", color = TextMain, fontSize = 22.sp) } }
            is StemSeparationState.Success -> { val s = state as StemSeparationState.Success; StemMixerRows(s.mixerState.channels.map { it.name to it.volume }) }
            is StemSeparationState.Error -> GlassCard { Text((state as StemSeparationState.Error).message, color = Pink, fontSize = 12.sp) }
        }
    }
}

@Composable private fun StemMixerRows(channels: List<Pair<String, Float>>) {
    GlassCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { channels.take(8).forEach { (name, volume) -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Equalizer, null, tint = Green, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(name, color = TextMain, fontSize = 12.sp, modifier = Modifier.width(80.dp)); ProgressBar(volume.coerceIn(0f,1f), Green, Modifier.weight(1f)); Text("${(volume*100).toInt()}%", color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(38.dp)) } } } }
}

@Composable private fun TranscriptionScreen(vm: WorkstationViewModel, nav: (Screen) -> Unit) {
    val notes by vm.performanceNoteStream.collectAsStateWithLifecycle()
    val chord by vm.currentChord.collectAsStateWithLifecycle()
    val metadata by vm.lastImportedAudioMetadata.collectAsStateWithLifecycle()
    ModulePage("Transcription", "CHORDS + DETECTED NOTES", nav, Screen.TRANSCRIPTION) {
        GlassCard { Waveform(metadata?.waveformAmplitudes, .52f, Purple) }
        GlassCard {
            SectionTitle("CHORDS")
            Text(chord?.name ?: "—", color = Cyan, fontSize = 27.sp)
            Spacer(Modifier.height(12.dp)); SectionTitle("DETECTED NOTES (MELODY)")
            Text(if (notes.isEmpty()) "No note events yet. Play or record audio." else notes.takeLast(24).joinToString("   ") { it.noteName }, color = TextMain, fontSize = 13.sp, lineHeight = 24.sp)
        }
        PlayerControls(vm.masterIsPlaying.collectAsStateWithLifecycle().value, { vm.toggleMasterPlayback() }, {}, {})
    }
}

@Composable private fun TunerScreen(vm: WorkstationViewModel, nav: (Screen) -> Unit) {
    val tuner by vm.tunerState.collectAsStateWithLifecycle()
    val recording by vm.isRecording.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val recordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleRecording()
    }
    ModulePage("Tuner", "CHROMATIC TUNER", nav, Screen.TUNER) {
        GlassCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(tuner.noteName, color = Green, fontSize = 38.sp); Text(if (tuner.currentFreq > 0) "%.2f Hz".format(tuner.currentFreq) else "No signal", color = TextMuted, fontSize = 11.sp) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (tuner.currentFreq > 0) "%+.1f".format(tuner.deviationCents) else "—", color = Green, fontSize = 30.sp); Text("cents", color = TextMuted, fontSize = 11.sp) }
            }
        }
        GlassCard { TunerGauge(tuner) }
        Text(if (tuner.currentFreq > 0) if (tuner.isTuned) "In Tune" else "Adjust your instrument" else "Grant microphone access and play a note", color = if (tuner.isTuned) Green else TextMuted, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Button(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.toggleRecording()
            else recordPermission.launch(Manifest.permission.RECORD_AUDIO)
        }, colors = ButtonDefaults.buttonColors(containerColor = if (recording) Pink else Purple), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(if (recording) "STOP MICROPHONE" else "START MICROPHONE")
        }
    }
}

@Composable private fun TunerGauge(tuner: TuningNote) {
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val center = Offset(size.width/2, size.height*.88f)
        val radius = size.width*.38f
        for (i in -6..6) {
            val a = (-75 + i*12) * PI / 180.0
            val p1 = Offset(center.x + cos(a).toFloat()*radius, center.y + sin(a).toFloat()*radius)
            val p2 = Offset(center.x + cos(a).toFloat()*(radius-12), center.y + sin(a).toFloat()*(radius-12))
            drawLine(TextMuted, p1, p2, 2f)
        }
        val cents = tuner.deviationCents.coerceIn(-50f,50f)
        val a = (-90 + cents*1.5) * PI / 180.0
        val p = Offset(center.x + cos(a).toFloat()*(radius-20), center.y + sin(a).toFloat()*(radius-20))
        drawLine(if (tuner.currentFreq > 0) Green else TextMuted, center, p, 4f, cap = StrokeCap.Round)
    }
}

@Composable private fun LibraryScreen(vm: WorkstationViewModel, nav: (Screen) -> Unit) {
    val sessions by vm.allSessions.collectAsStateWithLifecycle()
    val licks by vm.allLicks.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        ScreenHeader("Library", "YOUR LOCAL PROJECTS")
        SectionTitle("PROJECTS")
        if (sessions.isEmpty()) GlassCard { Text("No projects saved yet.", color = TextMuted) } else sessions.forEach { SessionRow(it) }
        Spacer(Modifier.height(14.dp)); SectionTitle("SAVED LICKS")
        if (licks.isEmpty()) GlassCard { Text("No saved licks yet.", color = TextMuted) } else licks.take(20).forEach { lick -> GlassCard { Text(lick.title, color = TextMain, fontWeight = FontWeight.SemiBold); Text("${lick.genre} • ${lick.bpm} BPM • ${(lick.confidence*100).toInt()}%", color = TextMuted, fontSize = 11.sp); Text(lick.notes, color = TextMuted, fontSize = 11.sp) } }
    }
}

@Composable private fun SettingsScreen(nav: (Screen) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        ScreenHeader("Settings", "OFFLINE-FIRST")
        GlassCard { Text("Processing", color = Cyan, fontWeight = FontWeight.SemiBold); SettingRow("Audio analysis", "On-device PCM / DSP"); SettingRow("Cloud upload", "Disabled"); SettingRow("Minimum Android", "8.0 / API 26") }
        GlassCard { Text("Playback", color = Purple, fontWeight = FontWeight.SemiBold); SettingRow("Decoder", "Android Media3"); SettingRow("Speed", "Controlled by playback engine") }
        GlassCard { Text("About", color = Gold, fontWeight = FontWeight.SemiBold); Text("HZ CHORD AI", color = TextMain, fontSize = 18.sp); Text("Hear the Notes. Understand the Music. Powered by AI.", color = TextMuted, fontSize = 12.sp); Text("Designed & Built by Joseph Hilary Zulukwa.", color = Gold, fontSize = 12.sp, modifier = Modifier.padding(top=8.dp)) }
    }
}

@Composable private fun SettingRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(top=10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = TextMuted, fontSize = 12.sp); Text(value, color = TextMain, fontSize = 12.sp) } }

@Composable private fun ModulePage(title: String, subtitle: String, nav: (Screen) -> Unit, current: Screen, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ScreenHeader(title, subtitle) { nav(Screen.MODULES) }
        content()
        Spacer(Modifier.height(18.dp))
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp)) }

@Composable private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Card(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = Panel), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF18304A)), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(14.dp), content = content) } }

@Composable private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier) { GlassCard(modifier) { Text(label, color = color, fontSize = 9.sp); Text(value, color = TextMain, fontSize = 20.sp, modifier = Modifier.padding(top=5.dp)); Text(if (label == "BPM") tempoLabel(value.toIntOrNull()) else "", color = TextMuted, fontSize = 9.sp) } }

@Composable private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) { Card(Modifier.weight(1f).height(76.dp).clickable(onClick=onClick), colors=CardDefaults.cardColors(containerColor=Panel2), border=androidx.compose.foundation.BorderStroke(1.dp,color.copy(alpha=.3f)), shape=RoundedCornerShape(12.dp)) { Column(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) { Icon(icon,null,tint=color,modifier=Modifier.size(22.dp)); Text(label,color=TextMain,fontSize=9.sp,modifier=Modifier.padding(top=6.dp)) } } }

@Composable private fun SmallAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) { Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.clickable(onClick=onClick).padding(3.dp)){Icon(icon,null,tint=color,modifier=Modifier.size(20.dp));Text(label,color=TextMuted,fontSize=7.sp)} }

@Composable private fun TabPill(label:String, active:Boolean, color:Color, onClick:()->Unit){Text(label,color=if(active)color else TextMuted,fontSize=10.sp,fontWeight=if(active)FontWeight.SemiBold else FontWeight.Normal,modifier=Modifier.clip(RoundedCornerShape(8.dp)).background(if(active)color.copy(alpha=.10f) else Color.Transparent).clickable(onClick=onClick).padding(horizontal=10.dp,vertical=7.dp))}

@Composable private fun ChordChip(label:String, confidence:Float){Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(58.dp).clip(RoundedCornerShape(8.dp)).background(if(confidence>.75f)Purple.copy(alpha=.16f) else Panel2).padding(7.dp)){Text(label,color=TextMain,fontSize=12.sp);Text("${(confidence*100).toInt()}%",color=TextMuted,fontSize=8.sp)}}

@Composable private fun PlayerControls(playing:Boolean,onPlay:()->Unit,onPrev:()->Unit,onNext:()->Unit){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){IconButton(onClick=onPrev){Icon(Icons.Default.SkipPrevious,null,tint=TextMain)};Box(Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Purple,Blue))).clickable(onClick=onPlay),contentAlignment=Alignment.Center){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=TextMain)};IconButton(onClick=onNext){Icon(Icons.Default.SkipNext,null,tint=TextMain)}}}

@Composable private fun ProgressBar(value:Float,color:Color,modifier:Modifier=Modifier){Box(modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF142235))){Box(Modifier.fillMaxWidth(value.coerceIn(0f,1f)).fillMaxSize().background(color,RoundedCornerShape(6.dp)))}}

@Composable private fun Waveform(amplitudes:List<Float>?,progress:Float,accent:Color){Canvas(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF04101D))){if(amplitudes.isNullOrEmpty()){drawLine(TextMuted.copy(alpha=.35f),Offset(0f,size.height/2),Offset(size.width,size.height/2),1f)}else{val count=amplitudes.size;for(i in amplitudes.indices){val amp=amplitudes[i].coerceIn(.01f,1f);val x=size.width*i/(count-1).coerceAtLeast(1);val h=size.height*.42f*amp;drawLine(accent.copy(alpha=.35f+(i%5)*.10f),Offset(x,size.height/2-h),Offset(x,size.height/2+h),3f,StrokeCap.Round)}};val px=size.width*progress.coerceIn(0f,1f);drawLine(TextMain,Offset(px,6f),Offset(px,size.height-6f),2f,StrokeCap.Round)}}

private fun timeText(ms:Long):String { val sec=(ms/1000).coerceAtLeast(0); return "%02d:%02d".format(sec/60,sec%60) }
private fun tempoLabel(bpm:Int?):String = when(bpm ?: 0){in 0..39->"";in 40..59->"Largo";in 60..75->"Adagio";in 76..108->"Andante";in 109..120->"Moderato";in 121..168->"Allegro";in 169..200->"Presto";else->""}

@Composable
private fun rememberAudioLauncher(context: Context, vm: WorkstationViewModel): androidx.activity.result.ActivityResultLauncher<String> {
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val metadata = withContext(Dispatchers.IO) { RealAudioDecoder.extractMetadataAndWaveform(context, uri) }
            vm.importUniversalAudio(metadata)
        }
    }
}
