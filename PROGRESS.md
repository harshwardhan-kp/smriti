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
