I have launched the full build and test verification (`clean assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest`) and will await its completion.
Waiting for the clean build and unit test suite to complete.
The build is in its final packaging and dex-merging stage. Waiting for completion.
### Implementation Summary

The floating capture bubble feature (Part 2 of 2) has been implemented and verified.

#### 1. Manifest Configuration
* **[`app/src/main/AndroidManifest.xml`](file:///Users/harshwardhan/Claude/smriti/app/src/main/AndroidManifest.xml#L10)**:
  * Added `android.permission.SYSTEM_ALERT_WINDOW`.
  * Declared `<service android:name=".capture.BubbleService" android:exported="false" />`.
  * Preserved the `tools:node="remove"` lines for `INTERNET` and `ACCESS_NETWORK_STATE`, passing `:app:assertNoNetworkPermission`.

#### 2. Bubble Capture Pipeline
* **[`app/src/main/java/com/smriti/app/capture/BubbleCapturePipeline.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/BubbleCapturePipeline.kt)**:
  * Mirrors the post-transcript half of [`CapturePipeline`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/CapturePipeline.kt) for existing screenshot files:
    * Obtains speech transcript via `asr.transcribe()`.
    * Inserts [`RecordEntity`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordEntity.kt) with `photoPath`, `transcript`, provisional title via [`Enricher.titleFor`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Enricher.kt#L138), empty arrays for structured entities, transcript-only embedding via [`Enricher.embedFor`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Enricher.kt#L155), and `enrichmentState = "PENDING"`.
    * Dispatches background enrichment via [`Enricher.request(context)`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Enricher.kt#L35).

#### 3. Overlay Bubble Service
* **[`app/src/main/java/com/smriti/app/capture/BubbleService.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/BubbleService.kt)**:
  * Injects a draggable overlay View into `WindowManager` using `TYPE_APPLICATION_OVERLAY` and `FLAG_NOT_FOCUSABLE`.
  * Normal state: translucent circular amber drawable matching `ColorAmber` (`#F2B705`) at 80% opacity with a subtle border.
  * Dragging: movement beyond touch slop cancels any pending long-press, dynamically repositioning the overlay via `WindowManager.updateViewLayout` and saving coordinates in `SharedPreferences`.
  * Tap: short press shows a Toast hint (`"Hold to capture"`).
  * Long-press (400 ms hold):
    * Requests the screenshot **first** via [`ScreenCaptureService.capture(context)`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/ScreenCaptureService.kt#L75).
    * Starts voice recording via [`BubbleCapturePipeline`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/BubbleCapturePipeline.kt) and Whisper ASR from [`AsrFactory.create(context)`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/AsrFactory.kt#L24).
    * Visually expands the bubble and switches it to solid amber with an active recording border.
  * Release (`ACTION_UP`): signals release to [`PushToTalk.stopListening()`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/PushToTalk.kt#L4), transcribes audio, saves the record, triggers background enrichment, and resets bubble appearance.

#### 4. Entry Point Integration
* **[`app/src/main/java/com/smriti/app/MainActivity.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/MainActivity.kt#L97-L151)**:
  * Handled extras in `onCreate` and `onNewIntent`:
    * `--ez smriti_bubble true`: checks `Settings.canDrawOverlays(this)`, prompts with `ACTION_MANAGE_OVERLAY_PERMISSION` if missing; verifies [`ScreenCaptureService.isArmed()`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/ScreenCaptureService.kt#L61) and launches [`ScreenCaptureConsentActivity`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/ScreenCaptureConsentActivity.kt) if consent is needed, then starts [`BubbleService`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/BubbleService.kt).
    * `--ez smriti_bubble_off true`: stops [`BubbleService`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/BubbleService.kt).

#### 5. Tests & Verification
* **[`app/src/test/java/com/smriti/app/capture/BubbleServiceTest.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/test/java/com/smriti/app/capture/BubbleServiceTest.kt)**:
  * Added unit test verifying initial state and actions.
* Verified build and test suite:
  ```bash
  ./gradlew clean assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
  ```
  * `assembleOfflineDebug`: **PASSED**
  * `:app:assertNoNetworkPermission`: **PASSED** (all merged manifests carry 0 network permissions)
  * `assembleDevcloudDebug`: **PASSED**
  * `testOfflineDebugUnitTest`: **PASSED** (all 8 tests green)
