# Smriti — architecture and build spec

Offline camera-and-voice work memory. Everything runs on the phone; the app declares no
network permission at all.

Target: Android 26+, arm64-v8a. Verified build host: macOS arm64, JDK 21, AGP 8.13.2,
Gradle 8.14, compileSdk 36, build-tools 36.0.0.

> Provenance note: this repository is a reference implementation written outside the iQOO
> Hackathon event window. Competition rules require all submitted code to be written during
> the event. The venue build starts from an empty directory; this repo is not submitted and
> is not linked from the Phase 1 form.

---

## 1. Module layout (single Gradle module, `:app`)

```
com.smriti.app
├── MainActivity.kt              Compose host, nav graph, permission gate
├── data/
│   ├── SmritiDb.kt              Room database (v1)
│   ├── RecordEntity.kt          one captured moment
│   ├── TaskEntity.kt            an action item extracted from a record
│   ├── RecordDao.kt
│   └── Converters.kt            FloatArray <-> ByteArray, Instant <-> Long
├── capture/
│   ├── CameraController.kt      CameraX ImageCapture -> JPEG in filesDir/photos
│   ├── VoiceRecorder.kt         SpeechRecognizer wrapper behind Asr interface
│   └── Asr.kt                   interface { suspend fun transcribe(): String }
├── ai/
│   ├── LlmEngine.kt             MediaPipe LlmInference lifecycle, single shared instance
│   ├── Extractor.kt             photo OCR + transcript -> StructuredRecord
│   ├── Ocr.kt                   ML Kit TextRecognition (Latin + Devanagari)
│   ├── Embedder.kt              MediaPipe TextEmbedder -> FloatArray
│   └── Recall.kt                cosine top-k + answer synthesis
├── ui/
│   ├── CaptureScreen.kt         the only input surface
│   ├── TimelineScreen.kt        reverse-chronological records
│   ├── DetailScreen.kt          photo + transcript + fields + tasks
│   ├── AskScreen.kt             spoken question -> answer + evidence photo
│   └── theme/                   colours, type
└── ModelProvisioner.kt          locates the .task file, reports absence clearly
```

## 2. Data model

```kotlin
@Entity data class RecordEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val createdAt: Long,            // epoch millis
  val photoPath: String,          // absolute path in filesDir/photos
  val ocrText: String,            // ML Kit output, may be empty
  val transcript: String,         // ASR output, may be empty
  val title: String,              // LLM
  val summary: String,            // LLM
  val peopleJson: String,         // LLM, JSON array of strings
  val amountsJson: String,        // LLM, JSON array of {value, currency, label}
  val tagsJson: String,           // LLM, JSON array of strings
  val embedding: ByteArray?       // 1 x N float32, little-endian
)

@Entity data class TaskEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val recordId: Long,
  val text: String,
  val dueDateMillis: Long?,       // null when the model gave no date
  val done: Boolean = false
)
```

`embedding` is a raw float32 blob, not a foreign vector store. See §5.

## 3. Capture pipeline

One button. Press → photo; hold → photo plus voice. Sequence, all on `Dispatchers.IO`:

1. `CameraController.capture()` → JPEG at `filesDir/photos/<epoch>.jpg`, max 1600 px long edge.
2. `Ocr.read(jpeg)` → `ocrText`. ML Kit `TextRecognition` with `DevanagariTextRecognizerOptions`
   falling back to Latin when the Devanagari model reports low confidence.
3. `Asr.transcribe()` → `transcript`.
4. `Extractor.extract(ocrText, transcript)` → `StructuredRecord` (§4).
5. `Embedder.embed(title + summary + ocrText)` → FloatArray → blob.
6. Insert `RecordEntity` + N `TaskEntity`.

Steps 2 and 3 run concurrently. Step 4 cannot start until both finish.

## 4. Structured extraction

`LlmEngine` holds one `LlmInference` for the process lifetime — loading Gemma per call costs
seconds and is the single easiest way to make the app feel broken.

MediaPipe API as verified from `tasks-genai:0.10.35` bytecode:

```kotlin
LlmInference.LlmInferenceOptions.builder()
  .setModelPath(path).setMaxTokens(1024).setMaxTopK(40)
  .setPreferredBackend(LlmInference.Backend.GPU)   // falls back to CPU on refusal
  .build()
engine.generateResponseAsync(prompt, ProgressListener<String> { partial, done -> })
```

`Backend` is `DEFAULT | CPU | GPU`. There is no NPU value. Do not claim otherwise.

Extraction prompt returns JSON only. A 1B model will sometimes wrap it in prose or emit a
trailing comma, so `Extractor` must:
- slice from the first `{` to the last `}` before parsing,
- parse with a lenient reader,
- on failure, retry once with a shortened prompt,
- on second failure, store the record with `title = first 60 chars of transcript` and empty
  structured fields. **Never lose the capture because the model misbehaved.**

Schema:
```json
{ "title": "", "summary": "", "people": [], "amounts": [], "tags": [],
  "actions": [ { "text": "", "due": "YYYY-MM-DD or null" } ] }
```

## 5. Recall

Brute-force cosine in Kotlin over every record's embedding. At a few hundred records this is
sub-millisecond; a vector database here is a dependency we would spend a night debugging for
no user-visible gain. Revisit past ~10k records.

```
query -> Embedder.embed(query) -> cosine against all -> top 4
      -> prompt Gemma with those 4 records as context
      -> answer + the photoPath of the top-scoring record as evidence
```

The evidence photo is the product's whole trust story. Always show it.

## 6. Non-negotiables

- **No `android.permission.INTERNET` in the manifest.** This is the demo's proof, and it also
  makes an accidental cloud call impossible rather than merely unlikely.
- Model file is never bundled in the APK. `ModelProvisioner` looks in app-internal storage and
  `/data/local/tmp/llm/`, and when absent shows the exact expected path.
- `abiFilters += "arm64-v8a"` — keeps the APK near 35 MB instead of 113 MB.
- Every AI call is cancellable and reports which backend it actually used.

## 7. Build order

| # | Task | Depends on |
|---|------|-----------|
| 1 | Gradle skeleton, theme, nav, permission gate | — |
| 2 | Room schema, DAO, converters | 1 |
| 3 | CameraX capture + ML Kit OCR | 1 |
| 4 | ASR behind the `Asr` interface | 1 |
| 5 | `LlmEngine` + `Extractor` with the JSON-repair path | 2 |
| 6 | `Embedder` + `Recall` | 2, 5 |
| 7 | Capture / Timeline / Detail / Ask screens | 3, 4, 5, 6 |
| 8 | Airplane-mode demo rehearsal + seed data | 7 |
