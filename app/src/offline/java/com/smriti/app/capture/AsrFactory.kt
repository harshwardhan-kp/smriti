package com.smriti.app.capture

import android.content.Context

/**
 * Offline flavor ASR factory.
 *
 * Measured 2026-09-01 on Moto G05 (stock Android 15): [PlatformAsr] fails with
 * `LANGUAGE_PACK_ERROR` / error code 13 — "Failed to get language pack of required locale" —
 * when no offline speech language pack is installed. Voice input is dead in that state.
 *
 * The intended shipping replacement is whisper.cpp — a fully offline, on-device Whisper
 * implementation — which will replace [PlatformAsr] behind this same factory.
 */
object AsrFactory {
    fun create(context: Context): Asr = PlatformAsr(context)
}
