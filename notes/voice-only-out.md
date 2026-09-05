I have launched the build and test command (`./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest :app:assertNoNetworkPermission`) in the background and will verify the results as soon as it completes.
I have started the final build and test run in the background and will report the results once complete.
### Verification of Storage Convention Claims

Before implementing any changes, both claims regarding downstream handling of `photoPath = ""` were verified by inspecting the codebase:

1. **`BitmapFactory.decodeFile("")` safely returning null**:
   - In [`TimelineScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/TimelineScreen.kt#L284-L293), `BitmapFactory.decodeFile(record.photoPath, options)` runs within a `try/catch (e: Exception)` block that returns `null` when given an empty path `""`.
   - In [`DetailScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt#L178-L184), `BitmapFactory.decodeFile(currentRecord.photoPath)` similarly runs inside a `try/catch (e: Exception)` block returning `null`, causing the image preview to be cleanly omitted.
   - **Result**: **Held.**
2. **`Enricher.drain` handling missing file for OCR**:
   - In [`Enricher.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Enricher.kt#L73-L77), `Ocr.read(java.io.File(record.photoPath), appContext)` is wrapped in `try { ... } catch (t: Throwable) { "" }`. Furthermore, [`Ocr.read`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Ocr.kt#L41-L44) itself catches exceptions and returns `""`.
   - **Result**: **Held.**

---

### Files Changed and Why

1. [`app/src/main/java/com/smriti/app/capture/CapturePipeline.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/CapturePipeline.kt)
   - Added `runVoiceOnly(): Flow<CaptureStage>` to support voice-only capture without interacting with the camera.
   - Emits `CaptureStage.Listening` and transcribes using `asr.transcribe()`.
   - Checks the transcript with `isWorthSaving(transcript)`. If blank after trimming, emits `CaptureStage.Failed("Nothing heard")` and aborts without saving.
   - Extracted common record creation and database insertion into a private helper `saveRecord(photoPath: String, transcript: String): Long`, which inserts the `RecordEntity` and calls `Enricher.request(context)`. Kept `run(withVoice)` concurrency intact.
   - Added `internal fun isWorthSaving(transcript: String): Boolean` to the companion object so the validation predicate can be unit tested and reused.

2. [`app/src/main/java/com/smriti/app/ui/CaptureViewModel.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/CaptureViewModel.kt)
   - Added `fun captureVoiceOnly()` mirroring `capture()`: cancels any existing `captureJob`, launches a coroutine collecting `pipeline.runVoiceOnly()`, updating `_stage`, and setting `_lastRecordId` when `CaptureStage.Done` is received.
   - Reused existing `stopVoice()` for release handling.

3. [`app/src/main/java/com/smriti/app/ui/CaptureScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/CaptureScreen.kt)
   - Added the 56.dp secondary mic button to the left of the 84.dp shutter in a centered `Row: [mic] [shutter] [Spacer(56.dp)]`, keeping the shutter horizontally centered on screen.
   - Designed the mic button with a 2.dp `ColorCream` border at 0.7 alpha, transparent background, centered `Icons.Default.Mic` tinted `ColorCream`, and `contentDescription = "Record voice only"`.
   - Configured hold-to-talk gestures with `detectTapGestures`:
     - Long press starts `vm.captureVoiceOnly()` and sets `isMicHeld = true`.
     - Release triggers `if (isMicHeld) vm.stopVoice()` and resets `isMicHeld = false`.
     - Short tap does nothing.
     - While held, swaps the border to 4.dp `ColorRedAlert` and background to `ColorRedAlert` at 0.15 alpha.
   - Separated the mic held state from the shutter listening indicator (`isShutterListening`) so the shutter does not light up when the mic is held.
   - Updated the hint text and styling:
     - Mic held: `"LISTENING — voice only, release to stop"` (`ColorRedAlert`, bold).
     - Shutter held: `"LISTENING — release to stop"` (`ColorRedAlert`, bold).
     - Idle: `"Tap for a photo · Hold to add your voice · Hold the mic for voice only"` (`ColorCream`, normal).

4. [`app/src/main/java/com/smriti/app/ui/TimelineScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/TimelineScreen.kt)
   - In `RecordCard`, added a branch for `record.photoPath.isBlank()` rendering a 56.dp rounded box with `ColorAmber` at 0.15 alpha background and a centered `Icons.Default.Mic` tinted `ColorAmber` with `contentDescription = "Voice note"`.
   - Preserved the existing grey fallback box for non-blank photo paths that fail decoding.

5. [`app/src/test/java/com/smriti/app/capture/VoiceOnlyCaptureTest.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/test/java/com/smriti/app/capture/VoiceOnlyCaptureTest.kt)
   - Added unit tests for `CapturePipeline.isWorthSaving` covering:
     - Empty string `""`
     - Whitespace string `"   "`
     - Whitespace with tabs/newlines `"\n\t "`
     - Real sentence `"remember that Sharma Traders wants two hundred more units"`
     - Sentence with leading and trailing spaces

---

### Verification and Test Runs

Executed:
```bash
./gradlew testOfflineDebugUnitTest assembleOfflineDebug assembleDevcloudDebug :app:assertNoNetworkPermission
```
- Both `offlineDebug` and `devcloudDebug` APKs assembled successfully.
- All unit tests passed, including `VoiceOnlyCaptureTest`.
- `:app:assertNoNetworkPermission` confirmed clean (no network permissions in merged manifests).
