package com.smriti.app.capture

import android.content.Context

/**
 * Offline flavor ASR factory.
 *
 * Prefers [VoskAsr] when a Vosk model is present on-device; falls back to
 * [PlatformAsr] otherwise.
 *
 * The platform recogniser (Android SpeechRecognizer) is known to fail with
 * LANGUAGE_PACK_ERROR (code 13) on devices without an offline speech language
 * pack installed — measured 2026-09-01 on Moto G05, stock Android 15: the
 * recogniser returns error 13 "Failed to get language pack of required locale"
 * and voice input is dead in that state. Vosk is therefore preferred whenever
 * a model is present because it provides fully offline recognition with no
 * language-pack dependency.
 */
object AsrFactory {
    fun create(context: Context): Asr {
        return if (VoskModelProvisioner.locate(context) != null) {
            VoskAsr(context)
        } else {
            PlatformAsr(context)
        }
    }
}
