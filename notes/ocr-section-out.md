I have launched the Gradle build and unit tests (`assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest`) and will await completion.
I am monitoring the background Gradle build. I will update you as soon as it completes.
I am running `testOfflineDebugUnitTest` with `--rerun-tasks` to verify all tests execute cleanly. I will update you when it completes.
OCR has been re-introduced as an isolated background data channel during enrichment, strictly abiding by both design rules:
1. **Rule 1 (Capture path is untouched & instant)**: [CapturePipeline.kt](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/CapturePipeline.kt) was not modified. The capture path remains entirely OCR-free and LLM-free.
2. **Rule 2 (OCR is isolated in its own channel)**: OCR text is stored directly in `RecordEntity.ocrText`. It is **not** passed to [`Extractor.extract()`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Extractor.kt), **not** fused into any LLM prompt, **not** used to build the title or summary, and **not** included in the embedding calculation.

---

### Summary of Files Changed

| File | Change & Rationale |
|---|---|
| [`app/build.gradle.kts`](file:///Users/harshwardhan/Claude/smriti/app/build.gradle.kts#L133-L134) | Re-added `com.google.mlkit:text-recognition:16.0.1` and `com.google.mlkit:text-recognition-devanagari:16.0.1`. Manifest was untouched because existing `tools:node="remove"` rules already strip the transitive network permissions. |
| [`app/src/main/java/com/smriti/app/ai/Ocr.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Ocr.kt) | Restored the exact [`Ocr`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Ocr.kt) object deleted in commit `847a626`. Preserved its bilingual dual-recognizer logic (running Devanagari and Latin models and picking whichever returns more characters) and exact method signature (`suspend fun read(file: File, context: Context): String`). |
| [`app/src/main/java/com/smriti/app/data/RecordDao.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt#L50-L55) | Updated [`applyEnrichment(...)`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt#L55) to include `ocr: String` parameter and write `ocrText = :ocr` in the `UPDATE` query. Added [`setOcrText(id: Long, ocr: String)`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt#L51) query so OCR results can be saved independently even if LLM backend initialization or extraction fails. Database version remains unchanged at 2 without migrations. |
| [`app/src/main/java/com/smriti/app/ai/Enricher.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Enricher.kt#L60-L108) | In [`drain()`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Enricher.kt#L49), runs `Ocr.read(...)` over each pending record's photo and persists it via `dao.setOcrText(...)`. Logs character count under tag `SmritiEnrich` (e.g. `record 21 ocr: 143 chars`). Runs OCR even if the language-model backend fails to load. Keeps `ocrText = ""` for extraction, titling, and embedding. Passes `ocr` to `dao.applyEnrichment(...)`. |

---

### Verification Results

Ran:
```bash
./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
```

- **`assembleOfflineDebug`**: Built successfully.
- **`assertNoNetworkPermission`**: **PASSED** (`clean - 3 merged manifest(s) carry no network permission.`). ML Kit did not leak any network permission past the manifest merger rules.
- **`assembleDevcloudDebug`**: Built successfully.
- **`testOfflineDebugUnitTest`**: All unit tests ran and passed (verified with full rerun).
