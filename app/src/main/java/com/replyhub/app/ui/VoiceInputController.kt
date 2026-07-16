package com.replyhub.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.replyhub.app.data.AppLanguage

class VoiceInputController(
    context: Context,
    private val language: AppLanguage,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) : RecognitionListener {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        .also { it.setRecognitionListener(this) }

    fun start() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                if (language == AppLanguage.KOREAN) "ko-KR" else "en-US",
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)
    }

    fun destroy() = recognizer.destroy()

    override fun onReadyForSpeech(params: Bundle?) = onListeningChanged(true)
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = onListeningChanged(false)
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    override fun onError(error: Int) {
        onListeningChanged(false)
        val label = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> language.text(
                "음성을 알아듣지 못했습니다.",
                "Could not understand the speech.",
            )
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> language.text(
                "음성이 들리지 않았습니다.",
                "No speech was detected.",
            )
            SpeechRecognizer.ERROR_NETWORK -> language.text(
                "음성 인식 네트워크를 확인해 주세요.",
                "Check the speech recognition network connection.",
            )
            else -> language.text(
                "음성 인식을 다시 시도해 주세요.",
                "Try speech recognition again.",
            )
        }
        onError(label)
    }

    override fun onResults(results: Bundle?) {
        onListeningChanged(false)
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
        if (text.isNullOrBlank()) {
            onError(language.text("인식된 문장이 없습니다.", "No text was recognized."))
        } else {
            onResult(text)
        }
    }
}
