Audio truncation at both ends of the recording window has been fixed.

### The Fixes Applied

1. **Loss 1 (Microphone opening too late)**
   In [`CapturePipeline.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/CapturePipeline.kt), camera capture and ASR are now launched concurrently inside a `coroutineScope`:
   ```kotlin
   emit(CaptureStage.Photo)
   if (withVoice) {
       emit(CaptureStage.Listening)
   }

   val (photoFile, transcript) = coroutineScope {
       val asrDeferred = async { if (withVoice) asr.transcribe() else "" }
       val photoDeferred = async { camera.capture() }
       Pair(photoDeferred.await(), asrDeferred.await())
   }
   ```
   `CaptureStage.Photo` is emitted first as before, and `CaptureStage.Listening` is emitted immediately when `withVoice` is true. The microphone opens concurrently with the camera shutter so initial words are captured. Both are awaited before persisting the record and calling `Enricher.request(context)`.

2. **Loss 2 (Microphone closing the instant finger lifts)**
   In [`SherpaWhisperAsr.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/SherpaWhisperAsr.kt) and [`VoskAsr.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/VoskAsr.kt), a trailing pad constant was added:
   ```kotlin
   private const val TRAILING_PAD_MS = 700L
   ```
   In `transcribe()`, after `withTimeoutOrNull` awaits the push-to-talk release signal and before calling `recorder.stop()`, a trailing pad is waited only if the completion was due to release:
   ```kotlin
   val released = withTimeoutOrNull(maxMillis) {
       deferred.await()
   } != null

   if (released) {
       // Humans release the button as they finish the last word, so cutting the
       // microphone at that exact instant clips it. Wait a short trailing pad
       // before stopping the recorder so the final syllable is captured.
       delay(TRAILING_PAD_MS)
   }
   ```
   If `withTimeoutOrNull` expires (15 s timeout), no pad is added because the entire window was already recorded.

---

### Summary of Files Changed

| File | Change & Rationale |
|---|---|
| [`app/src/main/java/com/smriti/app/capture/CapturePipeline.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/CapturePipeline.kt) | Runs ASR and camera capture concurrently via `coroutineScope` and `async`. Emits `CaptureStage.Photo` and immediately `CaptureStage.Listening` (if `withVoice` is true). Awaits both before database insertion and background enrichment dispatch. |
| [`app/src/offline/java/com/smriti/app/capture/SherpaWhisperAsr.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/SherpaWhisperAsr.kt) | Added `TRAILING_PAD_MS = 700L`. Waits 700 ms before `recorder.stop()` only when the user releases before timeout, preventing truncation of the last syllable. |
| [`app/src/offline/java/com/smriti/app/capture/VoskAsr.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/VoskAsr.kt) | Mirrors `SherpaWhisperAsr` with `TRAILING_PAD_MS = 700L` and the exact same conditional trailing pad before `recorder.stop()`, ensuring identical behavior across offline recognition backends. |

---

### Verification

Executed:
```bash
./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
```

- **`assembleOfflineDebug`**: Built cleanly.
- **`assertNoNetworkPermission`**: Passed (`clean - 3 merged manifest(s) carry no network permission.`).
- **`assembleDevcloudDebug`**: Built cleanly.
- **`testOfflineDebugUnitTest`**: All unit tests passed.
