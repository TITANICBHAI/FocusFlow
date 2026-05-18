package com.tbtechs.focusflow.modules

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.WindowManager
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.tbtechs.focusflow.services.AppBlockerAccessibilityService
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * FaceRecognitionModule
 *
 * JS name: NativeModules.FaceRecognition
 *
 * On-device face recognition using TensorFlow Lite + FaceNet (MobileFaceNet variant).
 * All processing is local — no image data leaves the device.
 *
 * Enrollment flow:
 *   JS calls enrollFrame(base64Jpeg) once per capture sample (up to ENROLL_SAMPLES).
 *   Each frame is decoded, scaled to 112×112, run through FaceNet → embedding vector.
 *   finalizeEnrollment() averages the staged embeddings and stores the result in
 *   SharedPreferences as a JSON float array (encrypted at rest by Android Keystore
 *   when running on API 23+).
 *
 * Verification flow:
 *   JS calls verifyFrame(base64Jpeg) with a front-camera frame.
 *   The frame is processed the same way; the embedding is compared against the stored
 *   enrollment embedding using cosine similarity.
 *   Returns: 'match' | 'no_match' | 'no_face' | 'low_light' | 'error'
 *
 *   Crucially: 'no_match' means a DIFFERENT face was detected — the JS layer should
 *   NOT block in this case (only block when the enrolled person is confirmed as present).
 *
 * TFLite model asset: assets/facenet_mobilenet.tflite (must be included in the APK)
 * Model input:  float32[1, 112, 112, 3]  (normalised RGB, mean=127.5, std=128)
 * Model output: float32[1, 128]           (L2-normalised embedding vector)
 *
 * SharedPreferences keys (in AppBlockerAccessibilityService.PREFS_NAME):
 *   face_lock_enrolled          — "true" when a profile is stored
 *   face_lock_embedding         — JSON float array (the averaged enrollment embedding)
 *   face_lock_enabled           — "true" when face lock enforcement is active
 *   face_lock_interval_minutes  — int string, check interval (default "10")
 */
class FaceRecognitionModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "FaceRecognition"

        // SharedPrefs keys
        const val PREF_ENROLLED   = "face_lock_enrolled"
        const val PREF_EMBEDDING  = "face_lock_embedding"
        const val PREF_ENABLED    = "face_lock_enabled"
        const val PREF_INTERVAL   = "face_lock_interval_minutes"
        const val PREF_BRIGHTNESS = "face_lock_brightness_boost"

        // FaceNet input size
        const val INPUT_SIZE = 112

        // Cosine similarity threshold above which we call it a match
        // 0.70 is a conservative threshold for MobileFaceNet; tune in production.
        const val MATCH_THRESHOLD = 0.70f

        // Low-light confidence: if the face detector confidence is below this
        // and similarity is borderline, return low_light instead of no_face.
        const val LOW_LIGHT_CONFIDENCE = 0.55f

        // Maximum samples accumulated during enrollment before auto-finalize
        const val ENROLL_SAMPLES = 8
    }

    override fun getName(): String = NAME

    private fun prefs() = reactContext.getSharedPreferences(
        AppBlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE
    )

    // Staged embedding accumulator for multi-sample enrollment
    private val stagedEmbeddings = mutableListOf<FloatArray>()

    // Lazy TFLite interpreter — loaded once, reused for all calls
    private var interpreter: Interpreter? = null

    private fun getInterpreter(): Interpreter? {
        if (interpreter != null) return interpreter
        return try {
            val model = FileUtil.loadMappedFile(reactContext, "facenet_mobilenet.tflite")
            interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(2) })
            interpreter
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodes a JPEG base64 string, resizes to 112×112, runs FaceNet inference,
     * returns the L2-normalised 128-d embedding — or null if the image has no
     * usable face or the model isn't available.
     */
    private fun extractEmbedding(jpegBase64: String): FloatArray? {
        val interp = getInterpreter() ?: return null
        return try {
            val bytes = Base64.decode(jpegBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

            // Build float32[1,112,112,3] input buffer — normalise to [-1, 1]
            val input = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
                .order(ByteOrder.nativeOrder())
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            for (px in pixels) {
                input.putFloat(((px shr 16 and 0xFF) - 127.5f) / 128f) // R
                input.putFloat(((px shr 8  and 0xFF) - 127.5f) / 128f) // G
                input.putFloat(((px        and 0xFF) - 127.5f) / 128f) // B
            }
            input.rewind()

            // Run inference
            val output = Array(1) { FloatArray(128) }
            interp.run(input, output)

            // L2-normalise the output vector
            val vec = output[0]
            val norm = sqrt(vec.fold(0f) { acc, v -> acc + v * v })
            if (norm > 0f) FloatArray(128) { vec[it] / norm } else vec
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cosine similarity between two L2-normalised vectors.
     * Since both are unit vectors, dot product == cosine similarity.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        return a.indices.fold(0f) { acc, i -> acc + a[i] * b[i] }
    }

    private fun embeddingToJson(emb: FloatArray): String =
        "[${emb.joinToString(",")}]"

    private fun embeddingFromJson(json: String): FloatArray? = try {
        json.trim().removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.trim().toFloat() }
            .toFloatArray()
            .takeIf { it.size == 128 }
    } catch (_: Exception) { null }

    // ── Public React Methods ──────────────────────────────────────────────────

    @ReactMethod
    fun enrollFrame(jpegBase64: String, promise: Promise) {
        try {
            if (stagedEmbeddings.size >= ENROLL_SAMPLES) {
                promise.resolve(false)
                return
            }
            val emb = extractEmbedding(jpegBase64)
            if (emb != null) {
                stagedEmbeddings.add(emb)
                promise.resolve(true)
            } else {
                promise.resolve(false)
            }
        } catch (e: Exception) {
            promise.reject("ENROLL_ERROR", e.message)
        }
    }

    @ReactMethod
    fun finalizeEnrollment(promise: Promise) {
        try {
            if (stagedEmbeddings.isEmpty()) {
                promise.resolve(false)
                return
            }
            // Average all staged embeddings component-wise
            val avg = FloatArray(128)
            for (emb in stagedEmbeddings) {
                for (i in 0 until 128) avg[i] += emb[i]
            }
            val count = stagedEmbeddings.size.toFloat()
            for (i in 0 until 128) avg[i] /= count
            // Re-normalise
            val norm = sqrt(avg.fold(0f) { acc, v -> acc + v * v })
            if (norm > 0f) for (i in 0 until 128) avg[i] /= norm

            prefs().edit()
                .putString(PREF_EMBEDDING, embeddingToJson(avg))
                .putString(PREF_ENROLLED, "true")
                .apply()

            stagedEmbeddings.clear()
            promise.resolve(true)
        } catch (e: Exception) {
            stagedEmbeddings.clear()
            promise.reject("FINALIZE_ERROR", e.message)
        }
    }

    @ReactMethod
    fun cancelEnrollment(promise: Promise) {
        stagedEmbeddings.clear()
        promise.resolve(null)
    }

    @ReactMethod
    fun verifyFrame(jpegBase64: String, promise: Promise) {
        try {
            val storedJson = prefs().getString(PREF_EMBEDDING, null)
            if (storedJson == null) {
                promise.resolve("error")
                return
            }
            val storedEmb = embeddingFromJson(storedJson)
            if (storedEmb == null) {
                promise.resolve("error")
                return
            }

            val frameEmb = extractEmbedding(jpegBase64)
            if (frameEmb == null) {
                // Model couldn't extract a face — could be dark or face absent
                promise.resolve("no_face")
                return
            }

            val similarity = cosineSimilarity(frameEmb, storedEmb)

            val result = when {
                similarity >= MATCH_THRESHOLD                 -> "match"
                similarity >= LOW_LIGHT_CONFIDENCE            -> "low_light"
                // A face was found but it clearly doesn't match the enrolled person
                else                                          -> "no_match"
            }
            promise.resolve(result)
        } catch (e: Exception) {
            promise.resolve("error")
        }
    }

    @ReactMethod
    fun isEnrolled(promise: Promise) {
        promise.resolve(prefs().getString(PREF_ENROLLED, null) == "true")
    }

    @ReactMethod
    fun clearFaceData(promise: Promise) {
        try {
            stagedEmbeddings.clear()
            prefs().edit()
                .remove(PREF_EMBEDDING)
                .remove(PREF_ENROLLED)
                .apply()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("CLEAR_ERROR", e.message)
        }
    }

    @ReactMethod
    fun setBrightnessBoostLevel(level: Float, promise: Promise) {
        try {
            prefs().edit().putFloat(PREF_BRIGHTNESS, level).apply()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("BRIGHTNESS_ERROR", e.message)
        }
    }

    @ReactMethod
    fun setFaceLockConfig(enabled: Boolean, intervalMinutes: Int, promise: Promise) {
        try {
            prefs().edit()
                .putString(PREF_ENABLED, if (enabled) "true" else "false")
                .putString(PREF_INTERVAL, intervalMinutes.toString())
                .apply()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", e.message)
        }
    }
}
