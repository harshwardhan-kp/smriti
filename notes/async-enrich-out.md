I have started building both flavors (`assembleOfflineDebug`, `assembleDevcloudDebug`) and executing `testOfflineDebugUnitTest`. Waiting for the build and tests to complete.
LLM extraction has been moved off the critical capture path into a debounced background enrichment pass, making capture instant while preserving on-device offline extraction.

### Summary of Changes

| File | Change & Rationale |
|---|---|
| [`RecordEntity.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordEntity.kt) | Added `enrichmentState` (`"PENDING"` default), `enrichmentAttempts` (default `0`), `enrichedAt`, `enrichmentModel`, and `enrichmentError`. Updated hand-written `equals()` and `hashCode()` to include these fields. |
| [`SmritiDb.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/SmritiDb.kt) | Bumped database version to `2` and registered non-destructive `MIGRATION_1_2` with `DEFAULT 'DONE'` for existing rows so they are preserved and never re-extracted. |
| [`RecordDao.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt) | Added `@Query` methods: `pendingEnrichment()`, `markEnrichment(...)`, `applyEnrichment(...)`, and `pendingCount()`. |
| [`CapturePipeline.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/CapturePipeline.kt) | Removed `BackendFactory` and `Extractor` calls and removed emitting `Thinking` (kept in sealed interface with compatibility comment). Records are inserted immediately with a provisional title and transcript embedding, emitting `CaptureStage.Done` in ~2 seconds instead of blocking ~40s on the LLM. Dispatches `Enricher.request(context)` asynchronously. |
| [`Enricher.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Enricher.kt) | Created background worker object. `request()` debounces incoming capture notifications (~20s window) on an application-scoped `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. `drain()` is protected by a mutex, checks for pending records before loading any model, reuses a single `BackendFactory` instance across the batch, extracts and re-embeds records, inserts tasks using shared `parseDue()`, catches per-record failures with retry tracking, and logs telemetry under tag `"SmritiEnrich"`. |
| [`MainActivity.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/MainActivity.kt) | Added `Enricher.request(this)` in `onCreate()` so any pending records left from a killed process are processed on the next launch. |
| [`EnricherTest.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/test/java/com/smriti/app/ai/EnricherTest.kt) | Added unit tests covering ISO-8601 date parsing (`parseDue`), title formatting and length limits (`titleFor`), and `RecordEntity` defaults/equality. |

### Verification
Executed:
```bash
./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
```
- Both `assembleOfflineDebug` and `assembleDevcloudDebug` built cleanly.
- `assertNoNetworkPermission` verified the offline flavor carries no network permissions.
- All unit tests passed (`testOfflineDebugUnitTest`).
