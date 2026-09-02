package com.example.audio

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite-based chord recognition model.
 * 
 * This class manages:
 * 1. Loading pre-trained .tflite models from assets
 * 2. Running inference on chroma vectors
 * 3. Post-processing output probabilities
 * 4. Error handling and fallback behavior
 * 
 * Model Requirements:
 * - Input: float32[1, 12] — Batch size 1, chroma vector size 12
 * - Output: float32[1, 108] — Softmax probabilities (12 roots × 9 chord types)
 * - Input quantization: None (float32 for chord detection)
 * 
 * Download/Train models from:
 * - TensorFlow Hub: https://tfhub.dev
 * - HuggingFace: https://huggingface.co/models?task=audio-classification
 * - Custom training: Use docs/ML_CHORD_RECOGNIZER_TRAINING.md
 */
class TFLiteChordModel(
    private val context: Context,
    private val modelPath: String = "models/chord_recognizer.tflite"
) {
    private var interpreter: Interpreter? = null
    private val isModelLoaded: Boolean
        get() = interpreter != null
    
    companion object {
        private const val TAG = "TFLiteChordModel"
        private const val INPUT_SIZE = 12       // Chroma vector: 12 pitch classes
        private const val OUTPUT_SIZE = 108     // 12 roots × 9 chord types
        private const val BATCH_SIZE = 1
    }
    
    init {
        loadModel()
    }
    
    /**
     * Loads TFLite model from assets folder.
     * 
     * @return true if model loaded successfully, false otherwise
     */
    private fun loadModel(): Boolean {
        return try {
            val model = loadModelFile(modelPath)
            interpreter = Interpreter(model, Interpreter.Options().apply {
                // Optional: Enable GPU acceleration if available
                // setUseGpuDelegate(true)
                // Optional: Enable NNAPI acceleration
                // setUseNNAPI(true)
            })
            Log.i(TAG, "Model loaded successfully: $modelPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}", e)
            false
        }
    }
    
    /**
     * Loads model file from Android assets as MappedByteBuffer.
     * 
     * @param path Path to .tflite file (relative to assets root)
     * @return MappedByteBuffer containing model data
     */
    private fun loadModelFile(path: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(path)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }
    
    /**
     * Runs chord recognition inference on a chroma vector.
     * 
     * @param chromaVector 12-element float array (pitch class distribution)
     * @return 108-element array of softmax probabilities [0, 1]
     *         or null if inference fails or model not loaded
     */
    fun inferChord(chromaVector: FloatArray): FloatArray? {
        if (!isModelLoaded || chromaVector.size != INPUT_SIZE) {
            Log.w(TAG, "Model not loaded or invalid input size: ${chromaVector.size}")
            return null
        }
        
        return try {
            // Prepare input: [1, 12] batch
            val input = arrayOf(chromaVector)
            
            // Prepare output: [1, 108] predictions
            val output = Array(BATCH_SIZE) { FloatArray(OUTPUT_SIZE) }
            
            // Run inference
            interpreter?.run(input, output)
            
            // Return first (only) batch result
            output[0]
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}", e)
            null
        }
    }
    
    /**
     * Batch inference for multiple frames (useful for processing chunks).
     * 
     * @param chromaVectors List of chroma vectors
     * @return List of probability arrays, or empty if inference fails
     */
    fun batchInferChords(chromaVectors: List<FloatArray>): List<FloatArray> {
        if (!isModelLoaded) {
            return emptyList()
        }
        
        return chromaVectors.mapNotNull { inferChord(it) }
    }
    
    /**
     * Gets input tensor information.
     * 
     * @return Input shape [1, 12]
     */
    fun getInputShape(): IntArray? {
        return try {
            interpreter?.getInputTensor(0)?.shape()
        } catch (e: Exception) {
            Log.w(TAG, "Could not get input shape: ${e.message}")
            null
        }
    }
    
    /**
     * Gets output tensor information.
     * 
     * @return Output shape [1, 108]
     */
    fun getOutputShape(): IntArray? {
        return try {
            interpreter?.getOutputTensor(0)?.shape()
        } catch (e: Exception) {
            Log.w(TAG, "Could not get output shape: ${e.message}")
            null
        }
    }
    
    /**
     * Checks if model is ready for inference.
     */
    fun isReady(): Boolean = isModelLoaded
    
    /**
     * Closes interpreter and releases resources.
     * Call this when done with model (e.g., on app shutdown).
     */
    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Log.i(TAG, "Model closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing model: ${e.message}", e)
        }
    }
}
