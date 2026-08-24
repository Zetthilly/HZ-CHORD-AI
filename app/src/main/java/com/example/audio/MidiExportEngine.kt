package com.example.audio

import android.content.Context
import com.example.data.ProjectSession
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MidiExportEngine {
    companion object {
        fun exportSessionToMidi(context: Context, session: ProjectSession): File {
            val safeTitle = session.title.replace(Regex("[^a-zA-Z0-9-]"), "_")
            val fileName = "HZ_Chord_AI_Export_$safeTitle.mid"
            val midiFile = File(context.cacheDir, fileName)
            
            val trackStream = ByteArrayOutputStream()
            
            // Set Tempo Meta Event
            val microsecondsPerQuarterNote = 60_000_000 / session.bpm.coerceAtLeast(1)
            writeTempoEvent(trackStream, microsecondsPerQuarterNote)
            
            // Setup Track defaults
            val ticksPerBeat = 480
            val noteDuration = ticksPerBeat * 2 // Default: 2 beats (Half Note)
            
            // Parse detected chords from the session
            val chords = session.detectedChords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            
            for (chord in chords) {
                val notes = getNotesForChord(chord)
                
                // Write Note ON events
                for ((index, note) in notes.withIndex()) {
                    val delta = if (index == 0) 0 else 0
                    writeDelay(trackStream, delta)
                    trackStream.write(0x90) // Note ON on channel 0
                    trackStream.write(note)
                    trackStream.write(0x64) // Velocity 100
                }
                
                // Write Note OFF events
                for ((index, note) in notes.withIndex()) {
                    val delta = if (index == 0) noteDuration else 0 // Apply duration to the first note off
                    writeDelay(trackStream, delta)
                    trackStream.write(0x80) // Note OFF on channel 0
                    trackStream.write(note)
                    trackStream.write(0x00) // Velocity 0
                }
            }
            
            // Write End of Track Meta Event
            writeDelay(trackStream, 0)
            trackStream.write(0xFF)
            trackStream.write(0x2F)
            trackStream.write(0x00)
            
            val trackBytes = trackStream.toByteArray()
            
            FileOutputStream(midiFile).use { fileStream ->
                // Write Header Chunk "MThd"
                fileStream.write("MThd".toByteArray())
                fileStream.write(byteArrayOf(0, 0, 0, 6)) // Header length
                fileStream.write(byteArrayOf(0, 0)) // Format 0 (single track)
                fileStream.write(byteArrayOf(0, 1)) // 1 track
                fileStream.write(byteArrayOf((ticksPerBeat shr 8).toByte(), (ticksPerBeat and 0xFF).toByte())) // Division
                
                // Write Track Chunk "MTrk"
                fileStream.write("MTrk".toByteArray())
                val trackLen = trackBytes.size
                fileStream.write(byteArrayOf(
                    (trackLen shr 24).toByte(),
                    (trackLen shr 16).toByte(),
                    (trackLen shr 8).toByte(),
                    (trackLen and 0xFF).toByte()
                ))
                fileStream.write(trackBytes)
            }
            
            return midiFile
        }
        
        fun exportLickToMidi(context: Context, lick: com.example.data.GuitarLick): File {
            val safeTitle = lick.title.replace(Regex("[^a-zA-Z0-9-]"), "_")
            val fileName = "HZ_Lick_AI_Export_$safeTitle.mid"
            val midiFile = File(context.cacheDir, fileName)
            
            val trackStream = ByteArrayOutputStream()
            
            // Set Tempo Meta Event
            val microsecondsPerQuarterNote = 60_000_000 / lick.bpm.coerceAtLeast(1)
            writeTempoEvent(trackStream, microsecondsPerQuarterNote)
            
            // Setup Track defaults
            val ticksPerBeat = 480
            val noteDuration = ticksPerBeat / 2 // Default: eighth note
            
            // Parse melodic phrases from lick (e.g. "C E G C' E'", "C4 E4 G4")
            val parsedNotesStr = lick.notes.split(" ", ",", "->").map { it.trim() }.filter { it.isNotEmpty() }
            
            for (noteStr in parsedNotesStr) {
                val midiPitch = getNotePitch(noteStr)
                if (midiPitch > 0) {
                    writeDelay(trackStream, 0)
                    trackStream.write(0x90) // Note ON
                    trackStream.write(midiPitch)
                    trackStream.write(0x64) // Vel 100
                    
                    writeDelay(trackStream, noteDuration)
                    trackStream.write(0x80) // Note OFF
                    trackStream.write(midiPitch)
                    trackStream.write(0x00) // Vel 0
                }
            }
            
            // Write End of Track Meta Event
            writeDelay(trackStream, 0)
            trackStream.write(0xFF)
            trackStream.write(0x2F)
            trackStream.write(0x00)
            
            val trackBytes = trackStream.toByteArray()
            
            FileOutputStream(midiFile).use { fileStream ->
                fileStream.write("MThd".toByteArray())
                fileStream.write(byteArrayOf(0, 0, 0, 6))
                fileStream.write(byteArrayOf(0, 0))
                fileStream.write(byteArrayOf(0, 1))
                fileStream.write(byteArrayOf((ticksPerBeat shr 8).toByte(), (ticksPerBeat and 0xFF).toByte()))
                
                fileStream.write("MTrk".toByteArray())
                val trackLen = trackBytes.size
                fileStream.write(byteArrayOf(
                    (trackLen shr 24).toByte(),
                    (trackLen shr 16).toByte(),
                    (trackLen shr 8).toByte(),
                    (trackLen and 0xFF).toByte()
                ))
                fileStream.write(trackBytes)
            }
            
            return midiFile
        }

        private fun getNotePitch(noteName: String): Int {
            if (noteName.isEmpty()) return 0
            
            var baseName = noteName.replace("'", "").replace("+", "").replace("-", "")
            
            // Extract octave digit if present
            val octaveMatch = Regex("\\d+").find(baseName)
            var octave = 4 // Default octave
            if (octaveMatch != null) {
                octave = octaveMatch.value.toInt()
                baseName = baseName.replace(octaveMatch.value, "")
            }
            
            val rootChar = baseName.firstOrNull()?.uppercaseChar() ?: return 0
            
            var pitch = when(rootChar) {
                'C' -> 12
                'D' -> 14
                'E' -> 16
                'F' -> 17
                'G' -> 19
                'A' -> 21
                'B' -> 23
                else -> return 0
            }
            
            if (baseName.contains("#")) pitch += 1
            if (baseName.contains("b") && rootChar != 'B' || baseName.count { it == 'b' } > 0 && rootChar == 'B' && baseName.length > 1) {
                pitch -= 1
            }
            
            return Math.min(127, Math.max(0, pitch + (octave * 12)))
        }

        private fun writeTempoEvent(out: ByteArrayOutputStream, mpqn: Int) {
            writeDelay(out, 0)
            out.write(0xFF)
            out.write(0x51)
            out.write(0x03)
            out.write((mpqn shr 16) and 0xFF)
            out.write((mpqn shr 8) and 0xFF)
            out.write(mpqn and 0xFF)
        }
        
        // Writes a variable length quantity representing Delta Time
        private fun writeDelay(out: ByteArrayOutputStream, delay: Int) {
            var buffer = delay and 0x7F
            var value = delay shr 7
            while (value > 0) {
                buffer = (buffer shl 8) or 0x80 or (value and 0x7F)
                value = value shr 7
            }
            while (true) {
                out.write(buffer and 0xFF)
                if ((buffer and 0x80) != 0) {
                    buffer = buffer shr 8
                } else {
                    break
                }
            }
        }
        
        private fun getNotesForChord(chordSymbol: String): List<Int> {
            val baseMidi = 60 // Middle C
            
            if (chordSymbol.isEmpty()) return emptyList()

            // Simplistic root note mapping
            val rootChar = chordSymbol[0].uppercaseChar()
            val rootOffset = when (rootChar) {
                'C' -> 0
                'D' -> 2
                'E' -> 4
                'F' -> 5
                'G' -> 7
                'A' -> 9
                'B' -> 11
                else -> 0
            }
            
            var offset = rootOffset
            if (chordSymbol.contains("#")) offset += 1
            if (chordSymbol.contains("b")) offset -= 1
            
            val rootNote = baseMidi + offset
            
            // Build the chord array based on extensions
            return if (chordSymbol.contains("m") && !chordSymbol.contains("maj")) {
                if (chordSymbol.contains("7")) {
                    listOf(rootNote, rootNote + 3, rootNote + 7, rootNote + 10) // minor 7th
                } else if (chordSymbol.contains("9")) {
                    listOf(rootNote, rootNote + 3, rootNote + 7, rootNote + 10, rootNote + 14) // minor 9th
                } else {
                    listOf(rootNote, rootNote + 3, rootNote + 7) // minor triad
                }
            } else if (chordSymbol.contains("7")) {
                if (chordSymbol.contains("maj7")) {
                    listOf(rootNote, rootNote + 4, rootNote + 7, rootNote + 11) // major 7th
                } else {
                    listOf(rootNote, rootNote + 4, rootNote + 7, rootNote + 10) // dominant 7th
                }
            } else if (chordSymbol.contains("13")) {
                listOf(rootNote, rootNote + 4, rootNote + 7, rootNote + 10, rootNote + 21) // 13th extension
            } else {
                listOf(rootNote, rootNote + 4, rootNote + 7) // major triad
            }
        }
    }
}
