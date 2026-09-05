# Smriti — progress log

Source of truth. Read this first on resume. Newest entries at the bottom of each section.

## Standing context

- Product: offline camera-and-voice work memory. See `ARCHITECTURE.md`.
- Supervisor: Claude Opus 5.

### Worker fleet (all smoke-tested 2026-09-01)

| Worker | Invocation | Status | Latency |
|---|---|---|---|
| agy, account 1 | `agy --model claude-opus-4-6-thinking -p "..."` | works | 13 s |
| agy, account 2 | `HOME="$HOME/.agy-profiles/account2" agy --model claude-opus-4-6-thinking -p "..."` | works | 75 s |
| agy, Gemini | `agy --model gemini-3.7-flash-high -p "..."` | works | ~30 s |
| OpenCode Muse Spark | `opencode run -m opencode/muse-spark-1.2-contributor-free "..."` | works, free | **210 s floor** |
| `muse` CLI | `muse exec --model muse-spark-1.2-contributor` | **BLOCKED** | 402 billing |

Prefer `claude-opus-4-6-thinking` on agy/agy2 — markedly stronger than Gemini 3.7 Flash at
Kotlin, and two accounts means two quota pools to alternate between.

**`agy2` is a zsh FUNCTION, not a binary.** `~/.zshrc` defines `agy-profile()` and aliases
`agy2="agy-profile account2"`. Shell functions do not exist in a non-interactive shell, so a
script must use the expanded form: `HOME="$HOME/.agy-profiles/account2" agy ...`

Worker prompt discipline that works: "Do NOT use tools and do NOT read files. Output ONLY file
contents: for each file a line `FILE: <path>` followed by one fenced block." Then extract with
a regex and BUILD IT YOURSELF before believing any of it. Every single dispatch this session
contained at least one error that compiled or looked fine.

- `muse` CLI is unusable: `API error 402 Billing verification failed` after 10 retries.
  Not substituting anything paid in its place.
- Submission assets live in a separate repo: `~/Claude/iqoo-hackathon`.
- User decision 2026-09-01: build the full product plus the 3-minute pitch, having been told
  the competition forbids carrying in a completed app. This repo is therefore NOT linked from
  the Phase 1 form and is NOT submitted. Venue build starts from an empty directory.

## Build host (verified working)

```
JDK 21          /opt/homebrew/opt/openjdk@21
Android SDK     /opt/homebrew/share/android-commandlinetools
platform        android-36     build-tools 36.0.0     platform-tools 37.0.1
AGP 8.13.2      Gradle 8.14 (wrapper only)
```

**Never run system `gradle` on an Android project.** Homebrew ships Gradle 9.7.1; Gradle >= 9.6
removed `org.gradle.api.problems.internal.InternalProblems`, which every AGP 8.x needs. To create
a wrapper, run `gradle wrapper` in a throwaway empty directory and copy `gradlew` +
`gradle/wrapper/gradle-wrapper.jar` across.

## Pinned versions (verified against Google's Maven, not guessed)

| Artifact | Version | Note |
|---|---|---|
| `com.google.mediapipe:tasks-genai` | 0.10.35 | `LlmInference` is **deprecated**; successor is LiteRT-LM |
| `com.google.mlkit:text-recognition` | latest stable | plus `text-recognition-devanagari` |
| `androidx.camera:camera-*` | latest stable | |
| `androidx.room:room-*` | latest stable | KSP, not KAPT |
| Compose BOM | 2024.09.03+ | |

## Test devices

| Device | SoC | RAM | Role |
|---|---|---|---|
| Samsung Galaxy A16 5G (SM-A166P) | MediaTek MT6835, Mali-G57 MC2 | 5.6 GB | dev testing, borrowed, not always available |
| iQOO 15 (loaner, at venue) | Qualcomm SM8850-AC | up to 16 GB | demo target; an AOT NPU `.litertlm` exists for this exact SoC |

## Status

### Done
- [x] Event rules, tracks and rubric extracted and verified from iqoo.reskilll.com
- [x] Android toolchain installed and proven by a successful APK build
- [x] MediaPipe `tasks-genai` API surface read from the AAR via `javap` (no NPU backend exists)
- [x] Feasibility spike compiles; APK 35 MB after `abiFilters`
- [x] Phase 1 deck (9 pages) and form copy drafted
- [x] Architecture spec for the full product

### Blocked
- [ ] Gemma `.task` model — licence still not accepted on HF account `harshw25`.
      Diagnosed precisely: the token is fine, an ungated file fetches with it (307). The 403
      body reads "Access to model litert-community/Gemma3-1B-IT is restricted and you are not
      in the authorized list." Purely a browser click at
      https://huggingface.co/litert-community/Gemma3-1B-IT
      NOTE: the fine-grained token is scoped to entity `harshw25` only. After accepting the
      licence it may ALSO need the global "Read access to contents of all public gated repos".
- [ ] Extraction latency after the lenient-parser fix — built and installed, NOT yet measured.

### Unblocked by substitution
- [x] An on-device LLM is running. `litert-community/Qwen2.5-0.5B-Instruct` is genuinely
      ungated (Apache-2.0) and ships MediaPipe `.task` files. 1.5B q8 (1.6 GB) also downloaded.

### Next
- [ ] Measure extraction latency with the lenient parser (was 21.9 s with the strict one)
- [ ] Try Qwen2.5-1.5B q8 — better schema adherence, ~3x slower per token
- [ ] Task 8: seed data for demo rehearsal
- [ ] Swap PlatformAsr for whisper.cpp if offline Hindi proves unreliable on the loaner
- [ ] Embedder model is Gemma-gated too (`embeddinggemma-300m`). Recall stays on keyword
      scoring until that licence is accepted, or find an ungated embedder.
- [ ] Re-measure everything on the iQOO 15 at the venue. The Helio G95 numbers are a floor.

## Log

### 2026-09-01 13:20 — Tasks 1 & 2 done, verified by build
Skeleton + Room schema generated by `agy gemini-3.7-flash-high`, 15 files.
`BUILD SUCCESSFUL`, APK 51 MB. Manifest audited: CAMERA and RECORD_AUDIO only, no INTERNET.

One fix needed, and it was my spec's fault not the worker's: I asked for
`ORDER BY dueDateMillis ASC NULLS LAST`. SQLite/Room's query parser rejects `NULLS LAST`.
Correct form is `ORDER BY dueDateMillis IS NULL ASC, dueDateMillis ASC`.

Private GitHub repos created: harshwardhan-kp/smriti and harshwardhan-kp/iqoo-hackathon-2026.

### 2026-09-01 13:35 — Tasks 3 to 7 done, app is functionally complete
All four screens built and wired. `BUILD SUCCESSFUL`, 11 unit tests green.

Worker errors caught and fixed by rebuilding rather than trusting "done":
- CapturePipeline guessed RecordEntity field names (`tasksJson`, missing `createdAt`)
- `ProgressListener` referenced as a nested class of `LlmInference`; it is top-level
- `Extractor` had a redundant secondary constructor clashing on JVM signature
- `AskScreen` called `asr.listen()`; the interface method is `transcribe()`
- `Icons.Default.Mic` is not in material-icons-core, needs material-icons-extended
- **The nav graph still pointed at PlaceholderScreen for all four routes.** The build was
  green and the app would have launched into empty screens. This is the one that would have
  shipped broken; only rebuilding and reading the nav graph caught it.

Also fixed my own missing `lifecycle-viewmodel-compose` dependency before the screens landed.

Start destination changed from "timeline" to "capture" — capture is the app.

`PITCH.md` written: 3-minute script, stage directions, contingency table, and the exact
answer to give if a judge asks about the NPU.

### 2026-09-01 15:10 — ON-DEVICE: the model runs, and three real defects surfaced

Test device: Redmi Note 10S (M2101K7BI), Helio G95, Mali-G76, 5.7 GB RAM, Android 13 / SDK 33.

**1. ML Kit smuggles in a network permission.** The installed package requested
`android.permission.INTERNET` while our manifest declared none. Traced with the manifest-merger
report to `com.google.android.datatransport:transport-backend-cct`, a Google telemetry uploader
pulled in transitively by ML Kit. The product's entire claim is that it cannot reach the
network, so this was the most important defect in the build.
Fixed with `tools:node="remove"`; guarded by `:app:assertNoNetworkPermission`, which reads the
MERGED manifest and fails the build. Negative-tested — removing the strip fails the build and
names all six offending entries. Confirmed against the compiled APK with
`aapt2 dump permissions`: only CAMERA, RECORD_AUDIO and one Compose signature permission.

**2. The GPU backend segfaults on Mali.** MediaPipe's GPU path initialises fine (model loaded in
12.8 s) and then dies mid-generation:

    Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
    in libllm_inference_engine_jni.so, tid DefaultDispatch

A native segfault takes the process with it — no Kotlin catch runs, so "try GPU, catch, fall
back to CPU" cannot work. Added `BackendPolicy`: a SharedPreferences sentinel written with
`commit()` before a GPU attempt and cleared only after a generation completes. If it is still
set at next launch, the previous run died mid-attempt and the GPU is quarantined permanently
on that device. Deliberately pessimistic — one unexplained death is enough.
CPU backend runs fine and the process survives.

**3. The 0.5B model will not follow a JSON schema.** Asked for `actions`, it returned
`{"actionItems": [...]}` with plain strings. Strict Gson binding produced an all-null record,
the parse "failed", and the retry pushed extraction from 5.3 s to 21.9 s.
Rewrote `parseJson` to be lenient: key aliases (actionItems / action_items / tasks / todos),
arrays of strings where objects were asked for, alias keys inside action objects, and a title
derived from the summary's first sentence when absent.
STATUS: built, installed, latency effect NOT yet measured.

**Measured on the Redmi, CPU backend, Qwen2.5-0.5B q8:**

| metric | value |
|---|---|
| first model load (cold) | 12 756 ms |
| subsequent load (warm page cache) | 1 630 – 1 892 ms |
| generate, 18 tokens | 5 329 ms |
| tokens/sec | 2.44 – 3.38 |
| extract, strict parser (two passes) | 21 858 ms |

These are a floor, not a forecast: Helio G95 is a 2021 mid-range part. The venue device is an
SM8850. Do NOT quote these numbers as the product's performance.

Extraction output was correct despite the weak model:
    actions: 2
      - Rohit ships the API by Friday (due=2026-09-05)
      - We need two hundred more units from Sharma Traders

**Deck fix:** slide 5 said "Fri 5 Sep". 5 Sep 2026 is a Saturday. Corrected to "Fri 4 Sep"
and re-rendered.

**Tooling notes for the venue:**
- MIUI blocks the FIRST `adb install` (`INSTALL_FAILED_USER_RESTRICTED`), including via
  `pm install` as the shell user. Push to /sdcard/Download and tap it once. UPDATES over
  `adb install -r` then work fine.
- MIUI denies `adb shell input tap` (INJECT_EVENTS). The UI cannot be driven over adb; hence
  `SelfTest`, triggered by
  `adb shell am start -n com.smriti.app/.MainActivity --ez smriti_selftest true --es backend cpu`
- `/data/local/tmp` is `drwxrwx--x`, so an app uid CAN traverse it and read a 0666 model there.

### 2026-09-01 20:25 — flavor split: devcloud (Muse Spark) vs offline (on-device)

The device loop was too slow to iterate on (12-60 s per generation), so the app now has two
product flavors sharing one `LlmBackend` interface:

| flavor | applicationId | model | INTERNET | network guard |
|---|---|---|---|---|
| `offline` (default, SHIPPING) | com.smriti.app | MediaPipe on-device | absent | armed |
| `devcloud` (dev only) | com.smriti.app.devcloud | Muse Spark 1.2 via api.meta.ai | present | skipped |

Different applicationIds, so both install side by side. Swapping back is
`./gradlew assembleOfflineDebug` — no code change.

Build commands:
    ./gradlew assembleOfflineDebug     # ships; guard runs and must stay clean
    ./gradlew assembleDevcloudDebug    # dev only
APKs:
    app/build/outputs/apk/offline/debug/app-offline-debug.apk
    app/build/outputs/apk/devcloud/debug/app-devcloud-debug.apk

Verified with `aapt2 dump permissions`: offline has CAMERA + RECORD_AUDIO only; devcloud adds
INTERNET and nothing else.

**Meta Messages API contract** (found by probing; the muse CLI wraps it):
    POST https://api.meta.ai/v1/messages
    x-api-key: <key>            (Authorization: Bearer also works)
    {"model":"muse-spark-1.2-contributor","max_tokens":N,
     "messages":[{"role":"user","content":"..."}]}
Response `content` is an ARRAY mixing {"type":"redacted_thinking"} and {"type":"text"}.
Concatenate the text entries; taking content[0] yields an empty string.

**The trap that cost an hour, and would have cost far more unlogged:**
Muse Spark is a thinking model and its reasoning tokens count against `max_tokens`.

| max_tokens | stop_reason | thinking tokens | text returned |
|---|---|---|---|
| 512 | max_tokens | 509 | **0 chars** |
| 4096 | end_turn | 800 | 175 chars |

Our `generate()` default of 512 guaranteed an empty string, SILENTLY — the API returns 200 with
an empty content array. MuseBackend now requests `maxOf(maxTokens * 4, 4096)` and logs a warning
when the text is empty, naming stop_reason.

**Measured, Moto G05, same prompt:**

| stage | on-device Gemma 3 1B (Redmi) | devcloud Muse Spark |
|---|---|---|
| load | 8 084 ms | 7 ms |
| generate | 12 217 ms | 4 754 ms |
| extract | 21 858 ms | 5 750 ms |

Output quality is also far better: correct title, a full summary, people, tags and both actions
with `due=2026-09-04` — the right Friday. And the raw JSON came back as
`{"action_items":[{"assignee":..,"task":..,"due":..}]}`, a THIRD key-alias variant we had not
seen before. The lenient parser absorbed it with no retry.

**Test devices**

| device | SoC | RAM | notes |
|---|---|---|---|
| Redmi Note 10S | Helio G95, Mali-G76 | 5.7 GB | MIUI blocks first adb install and denies INJECT_EVENTS |
| Moto G05 | Helio G81 (mt6768), Mali-G52 MC2 | 3.8 GB, **1.36 GB available** | near-stock A15: adb install and `pm grant` and `input tap` all work. Too little RAM for Gemma 1B; Qwen 0.5B only. |

### 2026-09-01 20:50 — voice input: root cause found, Groq Whisper wired into devcloud

**The long press was never broken.** Reproduced it over adb on the Moto G05
(`adb shell input swipe 360 1440 360 1440 2500`) and logcat showed the gesture firing
correctly:

    RecognitionService#onStartListening
    SodaSpeechRecognizer: Failed to get language pack of required locale: error 13
    Speech recognition error type LANGUAGE_PACK_ERROR with error code 13

There is **no offline speech language pack installed** on that phone, so Android's on-device
recogniser has nothing to run. This is the risk the research flagged for Hindi, except it is
failing for en-US on stock Android 15.

Consequence for the venue: **whisper.cpp is not a stretch goal for the offline build, it is
required.** The platform recogniser cannot be depended on. `AsrFactory` in the offline source
set is the seam where that swap happens.

**Groq Whisper in devcloud**, mirroring the LLM split:

| flavor | ASR |
|---|---|
| offline | `PlatformAsr` (Android SpeechRecognizer) — currently broken on both test phones |
| devcloud | `GroqWhisperAsr` → whisper-large-v3-turbo |

    POST https://api.groq.com/openai/v1/audio/transcriptions
    Authorization: Bearer <key>
    multipart: file=<wav>, model=whisper-large-v3-turbo, response_format=json
    -> {"text":"..."}

`AudioRecorder` records 16 kHz mono PCM and writes a canonical 44-byte RIFF/WAVE header,
patching bytes 4..7 (36+dataSize) and 40..43 (dataSize) afterwards with RandomAccessFile.

**The WAV writer is verified without a device.** Reconstructed the exact header the Kotlin
emits, wrapped real PCM in it, applied the same patch arithmetic, and posted it to Groq:
it transcribed correctly. The only difference from an `afconvert` reference file is that
afconvert inserts an optional `FLLR` padding chunk before `data`; ours uses the canonical
minimal layout, which is what decoders expect.

**Push-to-talk gap fixed:** CaptureScreen's `onPress` cleared the UI flag on release but never
stopped the recorder, so a Groq recording would have run to its 15 s timeout after the user
stopped speaking. Release now calls `vm.stopVoice()`, which reaches the Asr through a
`PushToTalk` interface declared in main — so main never references the devcloud class by name.

AskScreen also now takes its Asr from `AsrFactory` rather than constructing `PlatformAsr`.

**Keys** live in `local.properties` (gitignored) and reach devcloud through BuildConfig fields
`MUSE_API_KEY` and `GROQ_API_KEY`. Audited: no key appears in any tracked file.

**Worker note:** `muse exec` WRITES FILES DIRECTLY — it is an agent, not a text generator. A
run killed by a timeout can leave a partially-written tree with an empty stdout. Check
`git status` after any muse dispatch that does not exit cleanly.

**Not yet tested on hardware** (device was disconnected): the recorder against a real
microphone, and the Groq round trip from the phone. Everything compiles and the WAV format is
proven; what remains unverified is AudioRecord behaviour on the device itself.

### 2026-09-01 21:00 — semantic recall actually works now, and a demo corpus exists

**The embedder was never going to load.** `litert-community/embeddinggemma-300m` is Gemma-gated
like the language model. But MediaPipe publishes its own text embedders, ungated, from
`storage.googleapis.com/mediapipe-models`:

| model | size | verdict |
|---|---|---|
| universal_sentence_encoder.tflite | **5.8 MB** | chosen — small enough to bundle in the APK |
| bert_embedder.tflite | 26 MB | better quality, too heavy to bundle for now |
| mobilebert_embedder.tflite | 404 | not published at that path |

It now ships at `app/src/main/assets/embedder.tflite`.

**A gap that would have made the feature look broken:** `CapturePipeline` was storing
`embedding = null` on every record, and `Recall` requires at least three embedded records before
it uses semantic search. So the code was all present and the app would have quietly answered
every question by keyword matching. Captures now embed at capture time via `EmbedderHolder`
(one instance per process — the asset costs real time to load and rebuilding it per capture
would stall the one interaction that must feel instant).

`EmbeddingBackfill` fills in records that lack one — seeded rows, rows captured before the asset
shipped, or rows from a run where the embedder failed to load. Idempotent; only touches
`embedding IS NULL`. It runs after seeding.

**Demo corpus:** `DemoSeed` inserts 9 realistic records over six days — sprint sync, delivery
challan, invoice, compressor nameplate, site note, lab log, hiring whiteboard, a Devanagari site
note, and a DG-set meter reading — each with plausible OCR text, a spoken transcript, people,
amounts, tags and tasks, and a generated JPEG so thumbnails and the Evidence panel render.

    adb shell am start -n com.smriti.app/.MainActivity --ez smriti_seed true
    adb shell am start -n com.smriti.app/.MainActivity --ez smriti_seed_clear true

Every seeded row is tagged `seed`, so it is visibly seeded and `clear()` removes exactly those
rows: it counts first, deletes tasks before records, and no user input reaches the SQL.

Both flavors build; 74 MB debug APKs (the embedder asset plus material-icons-extended, both of
which R8 will shrink in release).

### 2026-09-01 21:50 — offline speech fixed with Vosk, not whisper.cpp

The offline flavor had NO working speech at all: Android's recogniser fails with
LANGUAGE_PACK_ERROR on devices with no offline pack, which was both test phones. whisper.cpp was
the planned answer but needs an NDK build, a JNI wrapper and a 148 MB model — days of work for a
30-hour event.

**Vosk is a plain Maven dependency.** `com.alphacephei:vosk-android:0.3.75` on Maven Central, no
NDK build, no JNI to write. Models from alphacephei.com:

| model | zipped | extracted |
|---|---|---|
| vosk-model-small-en-us-0.15 | 41 MB | 68 MB |
| vosk-model-small-hi-0.22 | 44 MB | 78 MB |

Both downloaded to `~/Claude/iqoo-hackathon/models/`. `VoskAsr` loads the model once per process
(loading is slow), feeds it the WAV from `AudioRecorder` in 4096-byte chunks past the 44-byte
header, and reads `getFinalResult()`. `AsrFactory` (offline) prefers Vosk when a model directory
is present and falls back to `PlatformAsr` otherwise.

`VoskModelProvisioner` validates a candidate directory by checking for an `am/` subdirectory or
`conf/model.conf` before handing it to the native layer — a half-extracted directory would
otherwise crash it rather than fail cleanly.

Two compile fixes: `AsrUnavailableException`'s second positional parameter is `errorCode: Int?`,
so the worker's `AsrUnavailableException(msg, e)` bound the cause to the wrong slot. Named
`cause = e` at both call sites.

`scripts/push-models.sh` in the hackathon repo now provisions both models and picks the language
model by the device's available memory: under ~1.8 GB it pushes Qwen 0.5B rather than Gemma 1B.

**State: 24 unit tests green, lint clean (0 errors), both flavors build, permissions audited.**

Still untested on hardware, because the device was disconnected: AudioRecord against a real mic,
Vosk recognition, the Groq round trip from the phone, and the back-button fix. Everything else
has been verified either on device earlier today or on the laptop.

### 2026-09-02 02:50 — flavor parity, credential leak closed, two releases published

**The offline timeline crash was a stale APK, not a missing fix.** `TimelineScreen.kt` lives in
`main` and is shared by both flavors; the namespaced LazyColumn keys were already in it. The
offline APK on the device had been built at ~21:5x, before the 22:13 fix commit. Confirmed by
decompressing `classes*.dex` from both distribution APKs and finding the `task-` and `record-`
string literals in each.

**One genuine asymmetry existed and is fixed.** `GroqWhisperAsr` and `VoskAsr` both implemented
`PushToTalk`; `PlatformAsr` did not. On the offline flavor's fallback path — a device with no
Vosk model — releasing the shutter therefore did nothing, and the user waited on
SpeechRecognizer's own end-of-speech detection instead of getting an immediate result. That is
the same defect fixed for the Groq path earlier, still live on one branch. `PlatformAsr` now
implements `PushToTalk` and calls `stopListening()` (not `cancel()`, which would discard audio).

Otherwise the flavors are structurally identical: 36 shared source files, and only the two
intended seams differ — `BackendFactory` and `AsrFactory`, plus their implementations.

**The devcloud APK was leaking both API keys.** A raw `grep` of the `.apk` finds nothing, which
is misleading — the dex is deflated inside the zip. Decompress `classes*.dex` and both the Groq
and Muse keys are plainly present as `BuildConfig` string constants. Publishing that as a release
asset would have published the credentials.

`RuntimeKeys` (devcloud source set) now resolves keys in order: `BuildConfig` →
`/data/local/tmp/smriti-keys.properties` → app-private storage. `-PdistributionBuild=true` blanks
the compiled-in fields. Re-verified against the built artefacts: **zero key hits in either
distribution APK.**

**Releases published.** The repo is **PUBLIC** (changed by the user on the morning of 2 Sep;
an earlier note in this log calling it private is stale). Release assets are therefore
anonymously downloadable — verified with an unauthenticated request returning HTTP 200.

That makes the credential fix immediately above load-bearing rather than merely tidy: publishing
the pre-fix devcloud APK would have put both API keys on a world-readable URL. Audited after
publishing — git history contains 0 commits touching either key, `local.properties` was never
tracked, and both published APK assets scan clean (0 hits for either key in `classes*.dex`).

Assets:
- `v0.1.0-offline` — smriti-offline-v0.1.0.apk, 83 MB
- `v0.1.0-devcloud` — smriti-devcloud-v0.1.0.apk, 83 MB

Both debug-signed, which is what makes them installable without a keystore.

**Not verified on hardware.** The device was disconnected for all of the above. The timeline fix
is confirmed present in both binaries by dex inspection, and everything builds with 24 tests
green and lint clean — but `PlatformAsr.stopListening()` and the runtime-key path have not been
exercised on a phone. Plug in and run `./scripts/verify-on-device.sh offline`.

---

### 2026-09-05 — iQOO 15 (SM8850): capture rebuilt, engine swapped, three earlier claims falsified

First session on the target hardware. Two iQOO 15 units, Android 16, SM8850, 15.6 GB RAM.
Branch: `offline-pipeline-rework`, 12+ commits, `main` untouched.

**Corrections to claims above in this file. Read these before trusting the older entries.**

**"MediaPipe's GPU LLM path is tuned for Adreno" is wrong.** It crashes on Adreno too, and the
crash reproduces on two independent handsets. Device 1: `SIGSEGV`, fault addr 0x0. Device 2:
`SIGABRT`. Both inside `Java_..._LlmTaskRunner_nativePredictSync`, both after the model loaded
successfully. The USB link was polled throughout and never dropped, so this is not a
disconnection artefact. Treat MediaPipe's GPU path as broken on Snapdragon 8 Elite Gen 5, not
as a Mali-specific defect. `BackendPolicy`'s sentinel worked correctly on a fresh device: it
quarantined the GPU after one crash with no intervention.

**"devcloud exists because a generation takes 12–60 s on a handset" no longer holds here.**
On this phone the on-device CPU path beat the cloud round trip: Muse Spark 4750 ms vs Gemma 1B
961 ms for the same prompt. devcloud is still useful for prompt work, but not for the stated
reason. Note also that this device's numbers drift with temperature — Gemma 1B measured
12.5 tok/s on a cool handset and 7.5–9.0 on one that had been running models for hours.

**The `muse` CLI 402 billing block is gone.** `muse exec --model muse-spark-1.3-contributor`
works. Note the effort is a separate `--reasoning-effort` flag, not part of the model id.
That said, muse stalled for 25 minutes on one dispatch with no output and no files written;
`agy --model gemini-3.8-flash-high` was faster and more reliable across ~12 dispatches. Set
`--print-timeout` on agy — the default is 5 minutes and a large spec will silently time out
(it writes nothing on timeout, so retrying is safe).

**Architecture changes**

Capture no longer runs any model. It was: photo → OCR → ASR → LLM → insert, and the record
only appeared after the language model finished — about 40 s of the user staring at a spinner
for a transcript that had been ready in two. It is now: photo and ASR concurrently → insert →
show, with all model work in a background `Enricher` pass. Measured shutter-release to visible
record: **~1.4 s**.

ASR is Whisper small int8 through sherpa-onnx, replacing Vosk (which remains as a fallback).
Verified against reference WAVs with known transcripts: word-perfect, 852 ms load, ~4.5×
faster than real time. sherpa-onnx is *not* on Maven Central — k2-fsa ship prebuilt AARs on
GitHub releases; `scripts/fetch-sherpa.sh` retrieves it and the AAR is gitignored.

OCR was removed from the capture path, then reinstated as an **isolated background channel**.
It runs in enrichment, lands in `RecordEntity.ocrText`, and is deliberately NOT fed to the
extraction prompt or the embedding. It exists for future features.

LiteRT-LM is now the primary LLM engine with MediaPipe as fallback, and extraction uses
constrained decoding (`ResponseFormat.json`, which requires the *Conversation* API and
`enableResponseFormat = true` — the Session API will not accept it).

**Hard-won facts**

**NPU is a property of the model file, not the chip.** `Backend.NPU()` fails with
`Model requires one of [cpu,gpu] but Main backend is NPU`. The generic `.litertlm` is not an
NPU build. Google publishes per-SoC AOT artefacts — `gemma-3n-E2B-it-int4.mediatek.mt6993.litertlm`
exists; nothing for Qualcomm. The Qualcomm route is Gemma 4 E4B via **Genie/QAIRT 2.45.0**,
whose bundle is public (5 GB zip, 7.1 GB extracted, includes `vision_encoder.bin`) but ships
**no runtime libraries** — those need the QAIRT SDK via Qualcomm Package Manager, plus a JNI
bridge over Genie's C API. `genie-t2t-run` in that SDK can validate the NPU from adb before any
code is written. Gemma 4 **E2B has no Qualcomm chipset assets at all**, so E4B is the only
option there.

**LiteRT-LM is 3× faster than MediaPipe on the identical model and backend** — 16.4 s vs
45–50 s — because MediaPipe emits `<end_of_turn>` as literal text and regenerates the answer
about ten times. LiteRT-LM applies Gemma's chat template.

**litertlm-android 0.17.0 carries Kotlin 2.4.0 metadata** and KSP (needed by Room) tops out at
2.3.11, so Kotlin cannot be bumped to match. Pinning the transitives down fails because
litertlm's own classes carry the metadata. `-Xskip-metadata-version-check` on the existing
2.0.21 toolchain works and avoids a migration.

**Removing ML Kit did not free the manifest guard.** The `tools:node="remove"` lines are still
load-bearing: `transport-backend-cct` now arrives via `mediapipe:tasks-text`, the embedder.
Tested by deleting them — `assertNoNetworkPermission` failed. Moving embeddings to LiteRT-LM's
`EmbeddingEngine` should finally free it.

**A greedy `sed 's/^.*: /  /'` in a logcat filter destroys JSON.** It strips through the *last*
`": "`, so `"actions": [` becomes `[`. This produced a false diagnosis of "malformed model
output" that was entirely a filter artefact. `verify-on-device.sh` still contains this sed —
use `adb logcat -v raw` when reading structured output.

**Bugs found and fixed, none of which a green build would have caught**

- Escaped JSON lost entire records. The model returned single-line `\n`/`\"`-escaped JSON;
  `repairJson` had no unescape step, so both attempts failed and the capture fell back to raw
  text — discarding a correct title, summary, two people and an amount. Fixed with a
  parse-first unescape fallback.
- The six-key schema prompt suppressed `actions` entirely. Reordered actions-first with an
  explicit required-keys instruction; E4B went to 2/2, the smaller models 0 → 1.
- Due dates were confabulated: "by Friday" became a Wednesday, and an action with no stated
  deadline was given one. `DueDateResolver` now derives dates with `java.time` and returns null
  when the transcript carries no temporal expression. English only so far.
- Audio was clipped at both ends — the mic opened only after the camera finished, and closed
  the instant the finger lifted. Now concurrent with the camera, plus a 700 ms trailing pad.
- `SelfTest`'s `--es backend` / `--ez reset_backend` extras were inert, and `LlmHolder` returned
  a cached engine even when a backend was forced.
- Records stranded in `RUNNING` were never retried; `drain()` now resets them.

**Known-open**

- `Ocr.read()` keeps whichever recognizer returned *more* characters. On a Latin-only photo the
  Devanagari model hallucinated more and won, yielding a Bengali zero and stray glyphs. Needs
  script detection or confidence, tested against real bilingual paper.
- No UI loader for `PENDING` records — "still enriching" and "produced nothing" look identical.
- Everything after the phones were disconnected is unverified on hardware: the LiteRT-LM engine
  swap, the date resolver, the audio-window fix, and the entire screen-capture bubble.

### 2026-09-05 — edit mode, voice-only capture, and the antimattr visual language

Three pieces of work, all on `offline-pipeline-rework`, none of it verified on hardware —
no device was available. Everything below is verified only by a clean build of both flavours,
67 unit tests green, and `assertNoNetworkPermission` clean.

**1. A memory can be corrected.** A pencil in the detail top bar turns the screen into itself,
editable in place: title, summary, people, tags, amounts, tasks. Transcript and OCR stay
read-only — they are what the microphone heard and the camera saw, evidence rather than a
draft.

Schema v3 adds `userEdited`. The guard matters more than the column: `applyEnrichment` carries
`AND userEdited = 0`, so a correction typed while a record is still enriching cannot be
replaced by the model. That guard alone created a defect — a user-edited row matched zero rows,
never reached DONE, stayed RUNNING, was reset to PENDING on the next drain and re-run through
the model up to three times, inserting duplicate tasks each time. Fixed by having
`applyUserEdit` set `enrichmentState = 'DONE'`, and by adding the same guard to
`pendingEnrichment` and `resetRunningToPending`.

Consequence worth remembering: **saving an edit finalises the record.** If enrichment had not
finished, it will not come back to add OCR or extract tasks from it.

**2. The photograph is optional.** A 56dp mic beside the 84dp shutter, and a second smaller mic
bubble below the capture bubble in the overlay. Both hold-to-talk, release to stop. A
voice-only record is `photoPath = ""` — no column, no placeholder file. A blank transcript is
never saved: no photo and no words is a row you can neither read nor delete.

The two bubbles share one `Asr`, so one `@Volatile` flag stops both recording at once. A failed
mic-bubble attach is logged, not fatal — the capture bubble is the primary feature.

**3. The app is on paper.** antimattr.one's language ported deliberately, not approximated. The
site's own `--token-*` values, read out of its live DOM: ground `#EEEEEE`, ink `#0D0D0D`, muted
`#5E5E5E`, hairline `#BABABA4D`, and the token its CSS names Red-primary, `#E10909`.

Three typefaces, one job each, bundled as static instances because the offline flavour has no
INTERNET permission and downloadable fonts were never an option (644 KB, OFL, licences in
`notes/licenses/`):
  Instrument Serif  display only, with one word per headline in italic
  Schibsted Grotesk anything read as prose or tapped as a control
  Geist Mono        all metadata, lowercase, wrapped in red [brackets]

Red takes the accent, so the semantic states moved off it: amber is work-in-progress, green is
settled. All UI copy is lowercase. 6dp radii, hairlines instead of cards, no shadows anywhere.

`ui/theme/Tokens.kt`, `ui/theme/Type.kt` and `ui/components/Primitives.kt` are the system; every
screen was migrated onto them and **no `Color(0xFF...)` literal survives anywhere in the UI**.
Design proof, rendered at 360x740 with the real faces:
https://claude.ai/code/artifact/50515d9b-6799-476c-9d80-0203faf982d5

**Worker fleet note.** All eight tasks went to `agy --model gemini-3.8-flash-high`, run strictly
one at a time — two workers running `./gradlew` in the same tree fight over the Gradle lock and
compile each other's half-written files. Specs in `notes/task-*.txt`, with the shared visual
rules in `notes/redesign-preamble.txt`. One dispatch died on a network error and was re-run.

The model was right and I was wrong once: my capture-screen spec told it to use both
`Arrangement.spacedBy(S.lg)` and a `56.dp + S.lg` trailing spacer, which double-counts the gap
and pushes the shutter 12dp left of centre. It implemented what I asked, flagged the error in
its summary, and I fixed the value.

### 2026-09-05 later — verified on the iQOO 15 (SM8850, Android 16, 384dp @ 600dpi)

Everything above was written blind. It has now been installed and exercised on the demo device.

**Passed**
- **Room 2->3 migration against the real database.** It held 31 records at v2; after the first
  Room access it was v3 with all 31 intact and `userEdited` present and 0. No crash.
- **Edit mode end to end.** Edited a title and saved: `userEdited` 0->1, state DONE, text
  persisted and visible in the timeline.
- **The enrichment guard genuinely guards.** Forced an edited record AND a control record back
  to PENDING, restarted, waited for a drain. The enricher logged `Pending enrichment: 1
  record(s)` — it saw only the control, enriched it in 21.5 s, and never touched the edited one,
  whose `enrichmentAttempts` stayed at 0.
- **Both voice-only paths.** In-app mic and mic bubble each produced `AudioRecord start(N)` /
  `stop(N)` in logcat, and the stop lands on release rather than at the 15 s timeout — so
  `stopListening()` does terminate `transcribe()` promptly. Both went red while live. Neither
  saved a record from silence, so `isWorthSaving` holds on device.
- **Both overlay windows attach on OriginOS** — "Bubble overlay attached" and "Mic bubble
  overlay attached" — and read as intended over another app: a pale disc with a red ring and a
  smaller dark disc below it.

**Four defects found by looking, all fixed in 3ca3ac8**
- a #EEEEEE band above and below the camera preview: `SmritiApp`'s outer Scaffold was insetting
  every screen and the paper window background showed through. Removing it also removed a double
  inset the other three screens were paying twice for.
- status bar icons unreadable over the preview once it ran edge to edge
- the hint pill bleeding off both screen edges, and its 49-character string orphaning "only"
- the detail metadata line colliding with itself when the model label wrapped

**Still unverified**
- Devanagari in the new faces. None of the three carries it, so Hindi relies on Android's
  per-glyph system fallback. Expected to work; no Hindi record was captured to prove it.
- A voice-only record end to end WITH speech. Silence was tested (correctly saves nothing);
  nobody spoke into the device, so no voice-only row has ever been written.
- The bubble's screen-capture path after the recolour, and bubble survival over hours.
- Re-enrichment inserts a duplicate set of tasks. Not reachable in normal use — a record is
  enriched once — but forcing a DONE record back to PENDING duplicates its tasks. Pre-existing,
  out of scope, noted here because a demo reset could hit it.
