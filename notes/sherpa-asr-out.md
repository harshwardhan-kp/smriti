I am installing the required Android SDK components and will proceed once the installation is complete.
Installing `build-tools/35.0.0` to ensure Gradle builds and tests can execute cleanly.
Building both `assembleOfflineDebug` and `assembleDevcloudDebug` flavors and running `testOfflineDebugUnitTest` to verify compilation, test passes, and the network permission assertion.
Compilation and packaging are progressing through Kotlin compilation and unit testing. Waiting for completion.
The build is in its final steps (compiling Java and packaging APKs). Waiting for completion.
### Summary of Changes

Whisper speech-to-text has been added to the repository using `sherpa-onnx` while preserving Vosk as an offline fallback.

#### 1. Added sherpa-onnx dependency
- **File modified**: [`app/build.gradle.kts`](file:///Users/harshwardhan/Claude/smriti/app/build.gradle.kts#L139-L142)
  - Added `implementation(files("libs/sherpa-onnx-1.13.7.aar"))` immediately after the Vosk dependency.

#### 2. Created Whisper model provisioner
- **File created**: [`app/src/main/java/com/smriti/app/capture/WhisperModelProvisioner.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/WhisperModelProvisioner.kt)
  - Defines `WhisperModelProvisioner` with `data class Paths(val encoder: File, val decoder: File, val tokens: File)`.
  - Searches `Context.filesDir/asr` and `/data/local/tmp/asr` in order.
  - Validates that readable files matching `*encoder*.onnx`, `*decoder*.onnx`, and `*tokens*.txt` exist, preferring `int8` variants when available.
  - Matches the KDoc tone and `missingMessage()` style of [`VoskModelProvisioner`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/VoskModelProvisioner.kt).

#### 3. Created Sherpa Whisper ASR engine
- **File created**: [`app/src/offline/java/com/smriti/app/capture/SherpaWhisperAsr.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/SherpaWhisperAsr.kt)
  - Implements [`Asr`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/Asr.kt) and [`PushToTalk`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/PushToTalk.kt) following [`VoskAsr`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/VoskAsr.kt).
  - Uses `OfflineRecognizer` with named constructor arguments across all config classes (`FeatureConfig`, `OfflineModelConfig`, `OfflineWhisperModelConfig`, `OfflineRecognizerConfig`).
  - Converts 16 kHz mono 16-bit PCM WAV output from [`AudioRecorder`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/AudioRecorder.kt) into a `FloatArray` normalized to `[-1.0, 1.0]` by skipping the 44-byte WAV header, parsing signed 16-bit little-endian samples, and dividing by `32768.0f`.
  - Caches the loaded `OfflineRecognizer` statically behind a coroutine `Mutex`.

#### 4. Updated ASR factory
- **File modified**: [`app/src/offline/java/com/smriti/app/capture/AsrFactory.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/AsrFactory.kt#L20-L28)
  - Updated preference order to:
    1. [`SherpaWhisperAsr`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/SherpaWhisperAsr.kt) when `WhisperModelProvisioner.locate(context) != null`
    2. [`VoskAsr`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/VoskAsr.kt) when `VoskModelProvisioner.locate(context) != null`
    3. [`PlatformAsr`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/PlatformAsr.kt) as fallback
  - Updated KDoc to document the role of Whisper for Hindi/English code-switching and Vosk as lightweight fallback.

#### 5. Added unit tests
- **File created**: [`app/src/test/java/com/smriti/app/capture/SherpaWhisperAsrTest.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/test/java/com/smriti/app/capture/SherpaWhisperAsrTest.kt)
  - Tests PCM WAV-to-FloatArray parsing, endianness, header offset, and normalization bounds.
  - Tests provisioner path resolution, partial file detection, and `int8` model preference.

---

### Verification
Executed:
```bash
./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
```
- Both `offlineDebug` and `devcloudDebug` APKs compiled and assembled cleanly.
- Unit tests passed.
- `:app:assertNoNetworkPermission` passed cleanly (no network permissions in the offline merged manifest).
