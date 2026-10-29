package com.example.service

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

/**
 * Monitors real device connectivity and checks whether a genuine local on-device LLM model file
 * is installed. Never claims cloud Gemini works offline.
 */
class NetworkAndOfflineModelMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = checkCurrentConnectivity()
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkCurrentConnectivity()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _isOnline.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    init {
        runCatching {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(req, callback)
        }
    }

    fun checkCurrentConnectivity(): Boolean {
        return runCatching {
            val active = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    fun isLocalOnDeviceModelAvailable(): Boolean {
        val modelDir = File(appContext.filesDir, "local_llm")
        return modelDir.exists() && (modelDir.listFiles()?.any { it.length() > 10_000_000L } == true)
    }

    fun shutdown() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}

class VoiceSpeechController(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    init {
        runCatching {
            tts = TextToSpeech(appContext) { status ->
                _ttsReady.value = (status == TextToSpeech.SUCCESS)
            }
        }
    }

    fun speakText(text: String, bcp47Tag: String, onError: (String) -> Unit = {}) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null || !_ttsReady.value) {
            onError("Text-to-speech engine is not available on this device.")
            return
        }
        val locale = Locale.forLanguageTag(bcp47Tag)
        val langResult = engine.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallbackResult = engine.setLanguage(Locale.forLanguageTag(locale.language))
            if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                onError("Voice data for ${locale.displayLanguage} is not installed on this device.")
                return
            }
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "interna_utt_${System.currentTimeMillis()}")
    }

    fun playRemoteAudioUrl(url: String, onError: (String) -> Unit = {}) {
        runCatching {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ ->
                    onError("Unable to play verified dictionary audio stream.")
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            onError("Audio playback error: ${it.localizedMessage ?: "stream unavailable"}")
        }
    }

    fun startSpeechRecognition(
        bcp47Tag: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("Speech recognition service is not installed on this device/emulator. You can type your response instead.")
            return
        }
        runCatching {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    val reason = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly and try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Network error during speech recognition."
                        else -> "Speech recognition unavailable (code $error)."
                    }
                    onError(reason)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val top = matches?.firstOrNull().orEmpty()
                    if (top.isNotBlank()) {
                        onResult(top)
                    } else {
                        onError("Could not transcribe speech.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47Tag)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
        }.onFailure {
            _isListening.value = false
            onError("Could not start microphone recognition: ${it.localizedMessage ?: "unknown error"}")
        }
    }

    fun stopSpeechRecognition() {
        _isListening.value = false
        runCatching { speechRecognizer?.stopListening() }
    }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        runCatching { mediaPlayer?.release() }
        runCatching { speechRecognizer?.destroy() }
    }
}
