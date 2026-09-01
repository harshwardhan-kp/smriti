# Smriti — progress log

Source of truth. Read this first on resume. Newest entries at the bottom of each section.

## Standing context

- Product: offline camera-and-voice work memory. See `ARCHITECTURE.md`.
- Supervisor: Claude Opus 5. Workers: `agy --model gemini-3.7-flash-high` (primary),
  `opencode -m opencode/muse-spark-1.2-contributor-free` (free, ~210 s latency floor, bulk only).
- `muse` CLI is **unusable**: `API error 402 Billing verification failed` after 10 retries
  (2026-09-01). Not substituting anything paid in its place.
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
- [ ] Gemma `.task` model — needs the user to accept the licence on HuggingFace (`gated: auto`)
- [ ] On-device inference numbers — needs the borrowed phone plugged back in

### Next
- [ ] Task 1: Gradle skeleton, theme, nav, permission gate
- [ ] Task 2: Room schema
- [ ] Tasks 3-8 per `ARCHITECTURE.md` §7
- [ ] 3-minute pitch script

## Log
