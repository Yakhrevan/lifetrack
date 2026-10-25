package com.yakhrevan.lifetrack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceBotAudioEngine(
    private val context: Context,
    private val onTranscriptResult: (String) -> Unit,
    private val onPartialTranscript: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onRmsChangedCallback: (Float) -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    var isTtsEnabled = true

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                tts?.language = Locale("en", "IN")
            }
        }
    }

    fun startListening(languageCode: String = "en-IN"): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition service is not available on this device.")
            return false
        }

        stopListening()

        return try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        onRmsChangedCallback(rmsdB)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(errorCode: Int) {
                        val message = when (errorCode) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking closer or louder."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected in time."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network issue while contacting speech provider."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                            else -> "Could not process speech (Code: $errorCode)."
                        }
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            onTranscriptResult(text)
                        } else {
                            onError("No words detected.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { onPartialTranscript(it) }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
            true
        } catch (e: Exception) {
            onError("Failed to start speech listener: ${e.message}")
            false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun speak(text: String, languageCode: String = "en-IN") {
        if (!isTtsEnabled || !isTtsInitialized || tts == null) return
        val loc = when (languageCode) {
            "ta-IN", "ta-Tanglish" -> Locale("ta", "IN")
            "hi-IN" -> Locale("hi", "IN")
            else -> Locale("en", "IN")
        }

        try {
            tts?.language = loc
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VoiceBotResponse")
        } catch (_: Exception) {}
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun release() {
        stopListening()
        stopSpeaking()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
    }
}
