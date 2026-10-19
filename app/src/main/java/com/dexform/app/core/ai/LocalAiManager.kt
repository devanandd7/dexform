package com.dexform.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.dexform.app.BuildConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

sealed interface GenerationChunk {
    data class Token(val text: String) : GenerationChunk
    data class Done(val totalCharacters: Int, val elapsedMs: Long, val ttftMs: Long) : GenerationChunk
    data class Error(val throwable: Throwable) : GenerationChunk
}

class LocalAiManager(private val context: Context) {

    companion object {
        private const val TAG = "LocalAiManager"
        const val MODEL_DISPLAY_NAME = "Gemma-4-E2B-it"
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val MIN_MODEL_SIZE_BYTES = 500 * 1024 * 1024L // 500 MB minimum check
        const val DEFAULT_MAX_TOKENS = 2048
        const val DEFAULT_TIMEOUT_MS = 120_000L // 120 seconds timeout per answer
    }

    private val engineMutex = Mutex()

    private var engineInstance: Any? = null
    private var activeConversationInstance: Any? = null

    var realBackend: String = "GPU"
        private set

    val isLoaded: Boolean
        get() = engineInstance != null

    val modelFile: File?
        get() = findGemmaModelFile()

    /**
     * Finds only the Gemma 4 E2B .litertlm file and verifies size.
     */
    fun findGemmaModelFile(): File? {
        val candidates = listOf(
            File(context.filesDir, "models/$MODEL_FILE_NAME"),
            File(context.getExternalFilesDir("models"), MODEL_FILE_NAME),
            File("/sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/$MODEL_FILE_NAME"),
            File("/sdcard/Download/$MODEL_FILE_NAME")
        )

        for (candidate in candidates) {
            try {
                if (candidate.exists() && candidate.isFile && candidate.length() >= MIN_MODEL_SIZE_BYTES) {
                    return candidate
                }
            } catch (_: Exception) {}
        }

        // Check inside app models directory for case-insensitive match
        val modelDir = File(context.filesDir, "models")
        if (modelDir.exists() && modelDir.isDirectory) {
            val found = modelDir.listFiles()?.firstOrNull {
                it.isFile && it.name.equals(MODEL_FILE_NAME, ignoreCase = true) && it.length() >= MIN_MODEL_SIZE_BYTES
            }
            if (found != null) return found
        }

        val extDir = context.getExternalFilesDir("models")
        if (extDir != null && extDir.exists() && extDir.isDirectory) {
            val found = extDir.listFiles()?.firstOrNull {
                it.isFile && it.name.equals(MODEL_FILE_NAME, ignoreCase = true) && it.length() >= MIN_MODEL_SIZE_BYTES
            }
            if (found != null) return found
        }

        return null
    }

    /**
     * Initializes the LiteRT-LM engine.
     * Tries GPU acceleration first (matching Edge Gallery), and falls back to CPU if GPU fails.
     * Takes engineMutex. Does nothing if already loaded.
     */
    suspend fun load(
        preferredAccelerator: String = "GPU",
        maxTokens: Int = DEFAULT_MAX_TOKENS
    ): Boolean = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            if (engineInstance != null) {
                Log.d(TAG, "Engine already initialized. Skipping load.")
                return@withLock true
            }

            val targetFile = findGemmaModelFile()
            if (targetFile == null || !targetFile.exists() || targetFile.length() < MIN_MODEL_SIZE_BYTES) {
                Log.e(TAG, "Gemma 4 E2B model file not found or size < 500MB")
                return@withLock false
            }

            val modelPath = targetFile.absolutePath
            Log.i(TAG, "Loading model: $modelPath (${targetFile.length() / (1024 * 1024)} MB)")

            val cacheDirPath = File(context.cacheDir, "litertlm").apply { if (!exists()) mkdirs() }.absolutePath

            // 1. Try GPU acceleration first
            if (preferredAccelerator.equals("GPU", ignoreCase = true)) {
                try {
                    Log.d(TAG, "Attempting LiteRT-LM Engine initialization on GPU...")
                    val gpuConfig = EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.GPU(),
                        visionBackend = Backend.GPU(),
                        maxNumTokens = maxTokens,
                        cacheDir = cacheDirPath
                    )
                    val gpuEngine = Engine(gpuConfig)
                    gpuEngine.initialize()
                    engineInstance = gpuEngine
                    realBackend = "GPU"
                    Log.i(TAG, "LiteRT-LM Engine successfully loaded on GPU!")
                    return@withLock true
                } catch (gpuError: Throwable) {
                    Log.w(TAG, "GPU initialization failed (${gpuError.message}). Falling back to CPU...", gpuError)
                    try {
                        (engineInstance as? Engine)?.close()
                    } catch (_: Throwable) {}
                    engineInstance = null
                }
            }

            // 2. Fallback to CPU
            try {
                Log.d(TAG, "Attempting LiteRT-LM Engine initialization on CPU...")
                val cpuConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    visionBackend = Backend.CPU(),
                    maxNumTokens = maxTokens,
                    cacheDir = cacheDirPath
                )
                val cpuEngine = Engine(cpuConfig)
                cpuEngine.initialize()
                engineInstance = cpuEngine
                realBackend = "CPU"
                Log.i(TAG, "LiteRT-LM Engine successfully loaded on CPU!")
                return@withLock true
            } catch (cpuError: Throwable) {
                Log.e(TAG, "CPU initialization also failed: ${cpuError.message}", cpuError)
                try {
                    (engineInstance as? Engine)?.close()
                } catch (_: Throwable) {}
                engineInstance = null
                return@withLock false
            }
        }
    }

    /**
     * Unloads engine and conversation, taking engineMutex.
     */
    suspend fun unload(): Unit = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            try {
                (activeConversationInstance as? Conversation)?.cancelProcess()
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling conversation process: ${e.message}")
            }
            try {
                (activeConversationInstance as? Conversation)?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing conversation: ${e.message}")
            }
            activeConversationInstance = null

            try {
                (engineInstance as? Engine)?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing engine: ${e.message}")
            }
            engineInstance = null
            Log.i(TAG, "LiteRT-LM Engine and conversation closed and unloaded.")
        }
    }

    /**
     * Resets or creates a conversation session.
     */
    suspend fun resetConversation(
        temperature: Float = 0.8f,
        topK: Int = 40,
        topP: Float = 0.95f
    ): Unit = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            val eng = engineInstance as? Engine ?: throw IllegalStateException("Cannot reset conversation: engine is null")
            try {
                (activeConversationInstance as? Conversation)?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failed closing previous conversation: ${e.message}")
            }

            val samplerConfig = SamplerConfig(
                topK = topK,
                topP = topP.toDouble(),
                temperature = temperature.toDouble()
            )
            val config = ConversationConfig(
                samplerConfig = samplerConfig,
                systemInstruction = null
            )
            activeConversationInstance = eng.createConversation(config)
            Log.d(TAG, "Conversation created/reset with temp=$temperature, topK=$topK, topP=$topP")
        }
    }

    /**
     * Cancels any in-flight generation in the active conversation.
     */
    fun cancelGeneration() {
        try {
            (activeConversationInstance as? Conversation)?.cancelProcess()
            Log.i(TAG, "cancelGeneration called on active conversation")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel conversation: ${e.message}")
        }
    }

    /**
     * Sends a message with optional image file and streams response tokens as a Flow.
     * Takes engineMutex to ensure exclusive inference.
     */
    fun sendMessageStream(
        text: String,
        imageFile: File? = null,
        enableThinking: Boolean = false,
        temperature: Float = 0.8f,
        topK: Int = 40,
        topP: Float = 0.95f
    ): Flow<GenerationChunk> = callbackFlow {
        val startTime = System.currentTimeMillis()
        var firstTokenTime: Long? = null
        var totalChars = 0

        val job = launch(Dispatchers.IO) {
            try {
                engineMutex.withLock {
                    val eng = engineInstance as? Engine
                    if (eng == null) {
                        trySend(GenerationChunk.Error(IllegalStateException("AI Engine is not loaded.")))
                        close()
                        return@withLock
                    }

                    if (activeConversationInstance == null) {
                        val samplerConfig = SamplerConfig(
                            topK = topK,
                            topP = topP.toDouble(),
                            temperature = temperature.toDouble()
                        )
                        activeConversationInstance = eng.createConversation(
                            ConversationConfig(
                                samplerConfig = samplerConfig,
                                systemInstruction = null
                            )
                        )
                    }

                    val conversation = activeConversationInstance as Conversation

                    val contents = mutableListOf<Content>()
                    if (imageFile != null) {
                        if (!imageFile.exists() || !imageFile.canRead()) {
                            trySend(GenerationChunk.Error(IllegalStateException("Attached image file cannot be read: ${imageFile.absolutePath}")))
                            close()
                            return@withLock
                        }
                        if (BuildConfig.DEBUG) {
                            try {
                                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(imageFile.absolutePath, options)
                                Log.d(
                                    TAG,
                                    "Sending image: ${options.outWidth}x${options.outHeight}, bytes=${imageFile.length()}"
                                )
                            } catch (_: Exception) {}
                        }
                        contents.add(Content.ImageFile(imageFile.absolutePath))
                    }

                    val promptText = if (text.isBlank() && imageFile != null) "Extract details" else text
                    if (promptText.isNotBlank()) {
                        contents.add(Content.Text(promptText))
                    }

                    if (contents.isEmpty()) {
                        trySend(GenerationChunk.Error(IllegalArgumentException("Message content cannot be empty.")))
                        close()
                        return@withLock
                    }

                    val extraContext = if (enableThinking) {
                        mapOf<String, Any>("enable_thinking" to true)
                    } else {
                        emptyMap<String, Any>()
                    }

                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Starting inference on backend: $realBackend, prompt: '$promptText'")
                    }

                    val completionDeferred = CompletableDeferred<Unit>()

                    val callback = object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val tokenText = message.toString().ifEmpty {
                                message.channels.values.joinToString("")
                            }
                            if (tokenText.isEmpty()) return
                            if (firstTokenTime == null) {
                                firstTokenTime = System.currentTimeMillis()
                                if (BuildConfig.DEBUG) {
                                    val ttft = firstTokenTime!! - startTime
                                    Log.d(TAG, "Time to first token (TTFT): ${ttft}ms")
                                }
                            }
                            totalChars += tokenText.length
                            trySend(GenerationChunk.Token(tokenText))
                        }

                        override fun onDone() {
                            val elapsed = System.currentTimeMillis() - startTime
                            val ttft = (firstTokenTime ?: System.currentTimeMillis()) - startTime
                            if (BuildConfig.DEBUG) {
                                Log.d(
                                    TAG,
                                    "Inference finished. Total time: ${elapsed}ms, characters: $totalChars, backend: $realBackend"
                                )
                            }
                            trySend(GenerationChunk.Done(totalChars, elapsed, ttft))
                            completionDeferred.complete(Unit)
                            close()
                        }

                        override fun onError(throwable: Throwable) {
                            if (throwable is CancellationException) {
                                Log.i(TAG, "Inference cancelled by user.")
                            } else {
                                Log.e(TAG, "Inference error: ${throwable.message}", throwable)
                            }
                            trySend(GenerationChunk.Error(throwable))
                            completionDeferred.completeExceptionally(throwable)
                            close(throwable)
                        }
                    }

                    try {
                        conversation.sendMessageAsync(Contents.of(contents), callback, extraContext)

                        // Await completion or timeout
                        val completed = withTimeoutOrNull(DEFAULT_TIMEOUT_MS) {
                            completionDeferred.await()
                        }

                        if (completed == null && completionDeferred.isActive) {
                            Log.w(TAG, "Inference timed out after ${DEFAULT_TIMEOUT_MS}ms")
                            conversation.cancelProcess()
                            val timeoutEx = IllegalStateException("This is taking too long. Try again.")
                            trySend(GenerationChunk.Error(timeoutEx))
                            close(timeoutEx)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception during sendMessageAsync: ${e.message}", e)
                        trySend(GenerationChunk.Error(e))
                        close(e)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error in inference job: ${t.message}", t)
                trySend(GenerationChunk.Error(t))
                close(t)
            }
        }

        awaitClose {
            job.cancel()
            cancelGeneration()
        }
    }

    /**
     * One-shot generation helper (used for self-tests and synchronous checks).
     * Creates an ephemeral conversation session and closes it immediately when done,
     * ensuring activeConversationInstance is never polluted.
     */
    suspend fun generateOneShot(
        prompt: String,
        imageFile: File? = null,
        timeoutMs: Long = 30_000L
    ): String = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            val eng = engineInstance as? Engine ?: throw IllegalStateException("Engine not initialized")
            val ephemeral = eng.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                    systemInstruction = null
                )
            )
            try {
                val contents = mutableListOf<Content>()
                if (imageFile != null && imageFile.exists()) {
                    contents.add(Content.ImageFile(imageFile.absolutePath))
                }
                if (prompt.isNotBlank()) {
                    contents.add(Content.Text(prompt))
                }
                val sb = StringBuilder()
                val deferred = CompletableDeferred<String>()
                ephemeral.sendMessageAsync(
                    Contents.of(contents),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val token = message.toString().ifEmpty {
                                message.channels.values.joinToString("")
                            }
                            sb.append(token)
                        }
                        override fun onDone() {
                            deferred.complete(sb.toString().trim())
                        }
                        override fun onError(throwable: Throwable) {
                            deferred.completeExceptionally(throwable)
                        }
                    },
                    emptyMap()
                )
                val res = withTimeoutOrNull(timeoutMs) {
                    deferred.await()
                } ?: run {
                    ephemeral.cancelProcess()
                    throw IllegalStateException("One-shot generation timed out after ${timeoutMs}ms")
                }
                return@withLock res
            } finally {
                try {
                    ephemeral.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing ephemeral conversation: ${e.message}")
                }
            }
        }
    }

    // =========================================================================
    // Compatibility bridges for existing tools (ProfileExtractorEngine, SettingsScreen)
    // =========================================================================

    private val _status = kotlinx.coroutines.flow.MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded)
    val status: kotlinx.coroutines.flow.StateFlow<ModelStatus> = _status.asStateFlow()

    fun checkModelStatus() {
        val target = findGemmaModelFile()
        if (engineInstance != null) {
            val size = target?.length() ?: 0L
            _status.value = ModelStatus.LoadedInRam(size, 1400L)
        } else if (target != null && target.exists() && target.length() >= MIN_MODEL_SIZE_BYTES) {
            _status.value = ModelStatus.Downloaded(target.length(), target.absolutePath)
        } else {
            _status.value = ModelStatus.NotDownloaded
        }
    }

    suspend fun generateResponse(
        prompt: String,
        enableThinking: Boolean = false,
        temperature: Float = 0.8f,
        topK: Int = 40,
        topP: Float = 0.95f,
        onTokenStream: ((partialAnswer: String, partialThought: String?) -> Unit)? = null
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        val fullAnswer = StringBuilder()
        val deferred = CompletableDeferred<Pair<String, String?>>()

        val flow = sendMessageStream(
            text = prompt,
            imageFile = null,
            enableThinking = enableThinking,
            temperature = temperature,
            topK = topK,
            topP = topP
        )

        val job = CoroutineScope(Dispatchers.IO).launch {
            flow.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Token -> {
                        fullAnswer.append(chunk.text)
                        onTokenStream?.invoke(fullAnswer.toString(), null)
                    }
                    is GenerationChunk.Done -> {
                        deferred.complete(Pair(fullAnswer.toString().trim(), null))
                    }
                    is GenerationChunk.Error -> {
                        deferred.completeExceptionally(chunk.throwable)
                    }
                }
            }
        }

        try {
            deferred.await()
        } finally {
            job.cancel()
        }
    }

    suspend fun generateResponseWithImage(
        bitmap: Bitmap,
        prompt: String,
        enableThinking: Boolean = false,
        temperature: Float = 0.8f,
        topK: Int = 40,
        topP: Float = 0.95f,
        onTokenStream: ((partialAnswer: String, partialThought: String?) -> Unit)? = null
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        val tempImage = File(context.cacheDir, "temp_extractor_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(tempImage).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            val fullAnswer = StringBuilder()
            val deferred = CompletableDeferred<Pair<String, String?>>()

            val flow = sendMessageStream(
                text = prompt,
                imageFile = tempImage,
                enableThinking = enableThinking,
                temperature = temperature,
                topK = topK,
                topP = topP
            )

            val job = CoroutineScope(Dispatchers.IO).launch {
                flow.collect { chunk ->
                    when (chunk) {
                        is GenerationChunk.Token -> {
                            fullAnswer.append(chunk.text)
                            onTokenStream?.invoke(fullAnswer.toString(), null)
                        }
                        is GenerationChunk.Done -> {
                            deferred.complete(Pair(fullAnswer.toString().trim(), null))
                        }
                        is GenerationChunk.Error -> {
                            deferred.completeExceptionally(chunk.throwable)
                        }
                    }
                }
            }

            try {
                deferred.await()
            } finally {
                job.cancel()
            }
        } finally {
            if (tempImage.exists()) tempImage.delete()
        }
    }

    suspend fun loadModelIntoRam(): Boolean = load()

    suspend fun unloadFromRam() {
        unload()
    }

    fun downloadModel(userHfToken: String? = null) {
        // Model file should be placed in app_files/models/gemma-4-E2B-it.litertlm
        checkModelStatus()
    }

    fun deleteModel(): Boolean {
        val target = findGemmaModelFile()
        val deleted = target?.delete() ?: false
        checkModelStatus()
        return deleted
    }
}

sealed interface ModelStatus {
    object NotDownloaded : ModelStatus
    data class Downloading(val progressPct: Int, val downloadedBytes: Long, val totalBytes: Long) : ModelStatus
    data class Downloaded(val fileSizeBytes: Long, val filePath: String) : ModelStatus
    data class LoadedInRam(val fileSizeBytes: Long, val ramUsageMb: Long) : ModelStatus
    data class Error(val message: String) : ModelStatus
}

