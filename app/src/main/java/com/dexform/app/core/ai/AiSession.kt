package com.dexform.app.core.ai

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream

sealed interface ModelState {
    object Unloaded : ModelState
    data class Loading(val step: String) : ModelState
    object Ready : ModelState
    object Busy : ModelState
    data class Error(val message: String) : ModelState
}

class AiSession(
    private val ai: LocalAiManager,
    private val appScope: CoroutineScope,
    private val ctx: Context
) {
    private val mutex = Mutex()
    private var loadJob: Job? = null
    private var genJob: Job? = null

    @Volatile
    private var wantLoaded = false

    private val _state = MutableStateFlow<ModelState>(ModelState.Unloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    fun onEnter() {
        if (ai.isLoadedInRam) {
            _state.value = ModelState.Ready
        } else if (_state.value !is ModelState.Loading && _state.value !is ModelState.Busy) {
            _state.value = ModelState.Unloaded
        }
    }

    fun onLeave() {
        // Can remain loaded or unloaded per user preference; cancel active operations if leaving
        genJob?.cancel()
    }

    fun loadModelToRam() {
        if (_state.value is ModelState.Loading || _state.value is ModelState.Busy) return
        wantLoaded = true
        startLoad()
    }

    fun unloadFromRam() {
        wantLoaded = false
        genJob?.cancel()
        loadJob?.cancel()
        appScope.launch(Dispatchers.Default) {
            mutex.withLock {
                ai.unloadFromRam()
                _state.value = ModelState.Unloaded
            }
        }
    }

    private fun startLoad() {
        loadJob = appScope.launch(Dispatchers.Default) {
            mutex.withLock {
                try {
                    _state.value = ModelState.Loading("Checking available RAM")
                    requireFreeRam(ctx, neededBytes = 1_000_000_000L)

                    _state.value = ModelState.Loading("Loading model weights into RAM...")
                    val loaded = ai.loadModelIntoRam()
                    if (!loaded) {
                        val status = ai.status.value
                        val msg = if (status is ModelStatus.Error) status.message else "Failed to load model file into RAM."
                        throw IllegalStateException(msg)
                    }

                    if (wantLoaded) {
                        _state.value = ModelState.Ready
                    } else {
                        ai.unloadFromRam()
                        _state.value = ModelState.Unloaded
                    }
                } catch (e: CancellationException) {
                    ai.unloadFromRam()
                    _state.value = ModelState.Unloaded
                    throw e
                } catch (e: Exception) {
                    ai.unloadFromRam()
                    _state.value = ModelState.Error(e.message ?: "Model failed to load")
                }
            }
        }
    }

    fun runExclusive(block: suspend () -> Unit) {
        if (_state.value != ModelState.Ready) return
        genJob = appScope.launch(Dispatchers.Default) {
            mutex.withLock {
                _state.value = ModelState.Busy
                try {
                    block()
                } finally {
                    if (wantLoaded && _state.value == ModelState.Busy) {
                        _state.value = ModelState.Ready
                    }
                }
            }
        }
    }

    private fun requireFreeRam(context: Context, neededBytes: Long) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        if (memInfo.availMem < neededBytes) {
            throw IllegalStateException("Insufficient free RAM (${memInfo.availMem / (1024 * 1024)} MB available). Close other apps and retry.")
        }
    }

    private fun getOrCreateVisionTestPng(): ByteArray {
        try {
            ctx.assets.open("vision_test.png").use { input ->
                return input.readBytes()
            }
        } catch (_: Exception) {}

        val bmp = Bitmap.createBitmap(200, 60, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            isAntiAlias = true
        }
        canvas.drawText("DEXFORM", 20f, 40f, paint)
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
