I have launched the Gradle build to compile both flavors (`offlineDebug`, `devcloudDebug`) and execute the unit tests. I will review the results once the build completes.
Screen capture via `MediaProjection` as a foreground service has been implemented.

### Summary of Changes

#### 1. Permissions & Components Declared
- [AndroidManifest.xml](file:///Users/harshwardhan/Claude/smriti/app/src/main/AndroidManifest.xml#L6-L10):
  - Added permissions:
    - `android.permission.FOREGROUND_SERVICE`
    - `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION`
    - `android.permission.POST_NOTIFICATIONS`
  - Retained `tools:node="remove"` for `INTERNET` and `ACCESS_NETWORK_STATE` intact.
  - Declared [`ScreenCaptureService`](file:///Users/harshwardhan/Claude/smriti/app/src/main/AndroidManifest.xml#L53-L56) with `android:foregroundServiceType="mediaProjection"` and `android:exported="false"`.
  - Registered [`ScreenCaptureConsentActivity`](file:///Users/harshwardhan/Claude/smriti/app/src/main/AndroidManifest.xml#L48-L51) with a transparent theme and `android:exported="false"`.
- [themes.xml](file:///Users/harshwardhan/Claude/smriti/app/src/main/res/values/themes.xml#L5-L12):
  - Added `Theme.Smriti.Transparent` so `ScreenCaptureConsentActivity` draws no UI while the system consent dialog is displayed.

#### 2. ScreenCaptureService
- Created [`ScreenCaptureService.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/ScreenCaptureService.kt):
  - **Foreground Lifecycle**: Runs with a low-importance notification channel (`"smriti_screencap"`, `"Screen capture armed"`).
  - **Single-use Consent Token**: Holds a single `MediaProjection` and a single `VirtualDisplay` bound to an `ImageReader` (`PixelFormat.RGBA_8888`, `maxImages = 2`) at display resolution.
  - **Android 14+ Order Requirement**: Registers `MediaProjection.Callback` prior to calling `createVirtualDisplay()`. Tears down and stops the service on `onStop()`.
  - **RowStride Padding & Color Fix**: Extracts bitmap pixels by handling `rowStride` padding when `rowStride > width * pixelStride` to prevent image skewing, writes JPEG quality 90 to `filesDir/photos/`, and always calls `image.close()` inside a `finally` block. Broadcasts `ACTION_CAPTURED` and triggers `onCaptureCallback`.
  - **Idle Auto-Release**: Sets a 5-minute inactivity timer (`IDLE_TIMEOUT_MS = 300_000L`) that resets on each capture and releases the projection, virtual display, and image reader if idle.
  - **State & Logging**: Exposes `@Volatile fun isArmed(): Boolean` and logs arming, capture (with byte size), and release reasons under tag `SmritiScreenCap`.

#### 3. ScreenCaptureConsentActivity
- Created [`ScreenCaptureConsentActivity.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/ScreenCaptureConsentActivity.kt):
  - Transparent activity that requests `MediaProjection` consent via `ActivityResultContracts.StartActivityForResult()`.
  - On approval, starts `ScreenCaptureService` with `ACTION_START` passing the consent result data and finishes.

#### 4. Debug Hooks
- Modified [`MainActivity.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/MainActivity.kt#L85-L113):
  - Added debug hooks in `onCreate` and `onNewIntent`:
    - `--ez smriti_screencap true` launches `ScreenCaptureConsentActivity`
    - `--ez smriti_screengrab true` calls `ScreenCaptureService.capture(this)`

#### 5. Verification
- Built both variants and ran unit tests:
  ```bash
  ./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
  ```
- Result: **BUILD SUCCESSFUL**, all unit tests passed, and `:app:assertNoNetworkPermission` confirmed clean (0 network permissions leaked into the offline variant).
