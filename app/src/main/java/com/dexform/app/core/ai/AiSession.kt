package com.dexform.app.core.ai

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

sealed interface ModelState {
    object Unloaded : ModelState
    data class Loading(val step: String) : ModelState
    object Ready : ModelState
    object Busy : ModelState
    data class Error(val message: String) : ModelState
}

class AiSession(
    val ai: LocalAiManager,
    private val appScope: CoroutineScope,
    private val ctx: Context
) {
    companion object {
        private const val TAG = "AiSession"
        const val RUN_VISION_SELFTEST = true
        private const val MIN_REQUIRED_RAM_BYTES = 1_000_000_000L // 1 GB free RAM requirement
    }

    private val sessionMutex = Mutex()
    private var loadJob: Job? = null
    private var genJob: Job? = null

    @Volatile
    private var isScreenActive = false

    private val _state = MutableStateFlow<ModelState>(ModelState.Unloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    // Test results tracking for debug UI
    var lastTextTestLatencyMs: Long = 0L
        private set
    var lastVisionTestLatencyMs: Long = 0L
        private set
    var lastTextTestResult: String = ""
        private set
    var lastVisionTestResult: String = ""
        private set

    /**
     * Called when the screen opens or comes to the foreground.
     */
    fun onEnter() {
        Log.d(TAG, "onEnter() called")
        isScreenActive = true

        if (_state.value is ModelState.Ready && ai.isLoaded) {
            return
        }

        if (_state.value is ModelState.Loading || _state.value is ModelState.Busy) {
            return
        }

        startLoadAndSelfTest()
    }

    /**
     * Called when the screen closes or goes to background.
     */
    fun onLeave() {
        Log.d(TAG, "onLeave() called")
        isScreenActive = false

        // Cancel in-flight generation
        ai.cancelGeneration()
        genJob?.cancel()
    }

    /**
     * Manual reload or retry triggered by user.
     */
    fun retryLoad() {
        isScreenActive = true
        startLoadAndSelfTest()
    }

    /**
     * Explicit unload from RAM (for debugging or manual control).
     */
    fun unloadModel() {
        Log.d(TAG, "unloadModel() called explicitly")
        loadJob?.cancel()
        genJob?.cancel()
        ai.cancelGeneration()

        appScope.launch(Dispatchers.Default) {
            sessionMutex.withLock {
                try {
                    ai.unload()
                } catch (e: Exception) {
                    Log.w(TAG, "Exception during unloadModel: ${e.message}")
                }
                _state.value = ModelState.Unloaded
                Log.d(TAG, "Unload complete")
            }
        }
    }

    private fun startLoadAndSelfTest() {
        loadJob?.cancel()
        loadJob = appScope.launch(Dispatchers.Default) {
            sessionMutex.withLock {
                if (!isScreenActive) {
                    _state.value = ModelState.Unloaded
                    return@withLock
                }

                try {
                    // Step 1: Memory & file check
                    _state.value = ModelState.Loading("Checking available RAM and model file...")
                    checkAvailableRam(MIN_REQUIRED_RAM_BYTES)

                    val modelFile = ai.findGemmaModelFile()
                    if (modelFile == null || !modelFile.exists() || modelFile.length() < LocalAiManager.MIN_MODEL_SIZE_BYTES) {
                        throw IllegalStateException(
                            "Gemma 4 E2B model file (${LocalAiManager.MODEL_FILE_NAME}) not found or size < 500MB. Please place it in app storage."
                        )
                    }

                    if (!isScreenActive) {
                        _state.value = ModelState.Unloaded
                        return@withLock
                    }

                    // Step 2: Initialize engine
                    _state.value = ModelState.Loading("Initializing LiteRT-LM engine (${ai.realBackend})...")
                    val loaded = ai.load(preferredAccelerator = "GPU")
                    if (!loaded) {
                        throw IllegalStateException("Failed to initialize LiteRT-LM engine on both GPU and CPU fallback.")
                    }

                    ai.resetConversation()
                    _state.value = ModelState.Ready
                    Log.i(TAG, "AiSession is now Ready! Backend: ${ai.realBackend}")

                    // Optional warm-up / self-test in background if not already measured
                    if (lastTextTestLatencyMs == 0L) {
                        try {
                            val textStart = System.currentTimeMillis()
                            val textAnswer = ai.generateOneShot("Reply with the single word OK.", timeoutMs = 25_000L)
                            lastTextTestLatencyMs = System.currentTimeMillis() - textStart
                            lastTextTestResult = textAnswer
                            Log.i(TAG, "Warm-up self-test response: '$textAnswer' in ${lastTextTestLatencyMs}ms")
                        } catch (t: Throwable) {
                            Log.w(TAG, "Warm-up text check note: ${t.message}")
                        }
                    }

                } catch (e: CancellationException) {
                    Log.i(TAG, "Loading job cancelled")
                    _state.value = ModelState.Unloaded
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed during model load: ${e.message}", e)
                    ai.unload()
                    _state.value = ModelState.Error(e.message ?: "Failed to initialize AI model")
                }
            }
        }
    }

    fun setBusy() {
        if (_state.value is ModelState.Ready) {
            _state.value = ModelState.Busy
        }
    }

    fun setReady() {
        if (isScreenActive && (_state.value is ModelState.Busy || _state.value is ModelState.Ready)) {
            _state.value = ModelState.Ready
        }
    }

    private fun checkAvailableRam(neededBytes: Long) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val freeBytes = memInfo.availMem
        if (freeBytes < neededBytes) {
            val freeMb = freeBytes / (1024 * 1024)
            throw IllegalStateException("Insufficient free RAM ($freeMb MB available). At least 1 GB is required.")
        }
    }

    private fun getOrCreateVisionTestFile(): File {
        val cacheFile = File(ctx.cacheDir, "vision_test.png")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile
        }

        try {
            ctx.assets.open("vision_test.png").use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return cacheFile
            }
        } catch (_: Exception) {}

        // Fallback: generate programmatically if asset missing
        val bmp = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 36f
            isAntiAlias = true
        }
        canvas.drawText("DEXFORM", 50f, 60f, paint)
        FileOutputStream(cacheFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return cacheFile
    }
}
