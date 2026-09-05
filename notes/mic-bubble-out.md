### Summary of Changes

A second, smaller (44.dp) mic bubble was added to the floating overlay service to allow voice-only capture without requiring a screenshot or MediaProjection consent.

#### 1. [`BubbleCapturePipeline.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/BubbleCapturePipeline.kt)
- Added `suspend fun captureVoiceOnly(): Long`.
- Factored shared record construction and persistence into a private helper `saveRecord(photoPath: String): Long` so `capture(photoFile: File)` and `captureVoiceOnly()` share the exact same entity construction, title generation, embedding, and `Enricher.request(context)` calls without drift.
- Reused [`CapturePipeline.isWorthSaving(transcript)`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/CapturePipeline.kt#L113-L115) to prevent saving empty/blank transcripts, returning `-1L` when nothing is heard.

#### 2. [`BubbleService.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/BubbleService.kt)
- **Second Overlay View**: Added `micBubbleView` (computed at 44.dp) managed by the same foreground service and notification.
- **Styling**: Created an inverted `GradientDrawable` pair:
  - Idle: Oval, translucent ink `0xCC0B0B0B`, 2.dp stroke in amber `0x99F2B705`.
  - Live: Oval, solid red `0xFFE53935`, 4.dp stroke in cream `0xFFFBF8F1`.
  - `contentDescription = "Record a voice note"`.
- **Positioning & Clamping**:
  - Default position placed directly below the capture bubble offset by `captureHeight + (8 * density).toInt()`.
  - Position saved in `SharedPreferences` under `"mic_bubble_x"` and `"mic_bubble_y"`.
  - Generalized `clampLayoutParams(params: WindowManager.LayoutParams)` using pure function `clampPosition`.
- **Touch & Gesture Handling**:
  - Independent dragging with touch slop detection.
  - Hold to record using `LONG_PRESS_TIMEOUT_MS`. A short tap shows `"Hold to record a voice note"`.
- **Recording & Concurrency**:
  - Requires no MediaProjection or ScreenCaptureService consent.
  - Guarded against concurrent recordings using a single `@Volatile private var isRecording = false` flag. If either bubble is already in flight, starting another shows `"Already recording"` and does nothing.
  - On long-press: sets the mic bubble live, launches on `serviceScope + Dispatchers.IO` into its own `micCaptureJob`, and invokes `pipeline.captureVoiceOnly()`.
  - On release: calls `(asr as? PushToTalk)?.stopListening()`.
  - On finish: resets mic bubble state on `Dispatchers.Main`, logs the record ID, and shows `"Nothing heard"` Toast if `-1L` was returned.
- **Lifecycle Management**:
  - Tracks attachment using `@Volatile private var micBubbleAttached = false`.
  - Attaches alongside `bubbleView` (including sticky restart). If adding the mic bubble fails, the service logs the error and continues running with the primary bubble instead of calling `stopSelf()`.
  - Removes `micBubbleView` safely in `onDestroy()` within its own try/catch block.
  - Updated foreground notification text to `"Hold the amber bubble to capture the screen · hold the red one for a voice note"`.

#### 3. [`BubbleServiceTest.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/test/java/com/smriti/app/capture/BubbleServiceTest.kt)
- Extended plain JVM tests without Android framework dependencies to verify:
  - Preference keys (`"mic_bubble_x"` and `"mic_bubble_y"`).
  - Pure default offset arithmetic across different display densities.
  - Coordinate clamping behavior for in-bounds, negative, overflow, and oversized bounds.

---

### Verification
- Both flavors and unit tests passed cleanly:
  ```bash
  ./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
  ```
- `:app:assertNoNetworkPermission` passed (clean with no network permissions).
