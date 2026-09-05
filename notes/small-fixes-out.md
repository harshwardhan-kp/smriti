### Summary of Changes

#### Fix 1: Flavor-Aware Build Badge
The hardcoded `"OFFLINE · ON-DEVICE"` badge in the shared [`CaptureScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/CaptureScreen.kt) falsely claimed on-device execution on `devcloud` builds. The codebase's per-flavor seam was used to define flavor-specific badge labels:
- **Created [`BuildBadge.kt` (offline)](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/ui/BuildBadge.kt)**: Defines [`BuildBadge`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/ui/BuildBadge.kt#L3) with `label = "OFFLINE · ON-DEVICE"`.
- **Created [`BuildBadge.kt` (devcloud)](file:///Users/harshwardhan/Claude/smriti/app/src/devcloud/java/com/smriti/app/ui/BuildBadge.kt)**: Defines [`BuildBadge`](file:///Users/harshwardhan/Claude/smriti/app/src/devcloud/java/com/smriti/app/ui/BuildBadge.kt#L3) with `label = "DEV · CLOUD MODELS"`.
- **Modified [`CaptureScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/CaptureScreen.kt#L118)**: Replaced the hardcoded string literal with [`BuildBadge.label`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/ui/BuildBadge.kt#L4). Preserved the surrounding `Surface`, layout, colors, shape, padding, and typography exactly as they were.

---

#### Fix 2: Stripping Whisper Silence Markers
Whisper outputs subtitle-derived non-speech markers like `[INAUDIBLE]`, `(inaudible)`, `[BLANK_AUDIO]`, and `[ Silence ]` when given silence. This prevented fallback to `"Untitled capture · HH:MM"`:
- **Modified [`SherpaWhisperAsr.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/SherpaWhisperAsr.kt#L88-L122)**:
  - Added `SILENCE_MARKERS` documenting Whisper's special tokens/hallucinations emitted on silence.
  - Added [`cleanTranscript`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/SherpaWhisperAsr.kt#L103) (and [`stripSilenceMarkers`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/SherpaWhisperAsr.kt#L118)) in the companion object. It trims the input, checks case-insensitively for bracketed (`[...]`) and parenthesized (`(...)`) forms (allowing internal/surrounding whitespace), and returns `""` if the transcript consists only of a silence marker while preserving legitimate text.
  - Applied [`cleanTranscript`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/SherpaWhisperAsr.kt#L103) in both [`transcribeFile`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/SherpaWhisperAsr.kt#L120) and [`transcribe`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/SherpaWhisperAsr.kt#L156).
- **Vosk ASR Check ([`VoskAsr.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/capture/VoskAsr.kt))**:
  - Left unchanged. Vosk uses Kaldi HCLG decoding graphs over a standard word lexicon; on silence it yields empty output (`{"text": ""}`) and does not emit Whisper subtitle tokens.
- **Modified [`SherpaWhisperAsrTest.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/test/java/com/smriti/app/capture/SherpaWhisperAsrTest.kt#L107-L134)**:
  - Added unit tests verifying stripping of `"[INAUDIBLE]"`, `"  [inaudible]  "`, `"(inaudible)"`, `"[BLANK_AUDIO]"`, `"[ Silence ]"`, and `"  ( silence )  "`.
  - Added test verifying that transcripts containing the word "inaudible" in sentences are preserved intact.
  - Added tests for standard text and empty/whitespace inputs.

---

### Verification

Executed:
```bash
./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
```
- **`assembleOfflineDebug`**: Built successfully.
- **`assembleDevcloudDebug`**: Built successfully.
- **`:app:assertNoNetworkPermission`**: Passed (`clean - 3 merged manifest(s) carry no network permission`).
- **`testOfflineDebugUnitTest`**: All tests passed including the new silence marker tests.
