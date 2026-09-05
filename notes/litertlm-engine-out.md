LiteRT-LM has been made the primary on-device LLM engine, with MediaPipe retained as the fallback.

### Summary of Changes

| File | Change & Rationale |
|---|---|
| [`LlmBackend.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/LlmBackend.kt) | Extended interface method signature to `suspend fun generate(prompt: String, maxTokens: Int = 512, jsonSchema: String? = null): String` to allow per-call constrained JSON decoding for extraction while keeping synthesis unconstrained. |
| [`LocalLlmBackend.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/LocalLlmBackend.kt) | Updated `generate` implementation to accept and ignore `jsonSchema: String?`, continuing to delegate prompt and maxTokens to MediaPipe `LlmEngine`. |
| [`MuseBackend.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/devcloud/java/com/smriti/app/ai/MuseBackend.kt) | Updated `generate` implementation in the `devcloud` flavor to accept and ignore `jsonSchema: String?`. |
| [`LiteRtLmBackend.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/ai/LiteRtLmBackend.kt) *(new)* | Implemented `LlmBackend` wrapping LiteRT-LM's `Engine`. Uses `Conversation` with `ConversationConfig(enableResponseFormat = true)`, creates a fresh conversation per `generate()` call and closes it after extracting text, passes `ResponseFormat.json(jsonSchema)` when provided, and closes the engine on `close()`. |
| [`LiteRtLmHolder.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/ai/LiteRtLmHolder.kt) *(new)* | Mutex-guarded singleton caching the initialized `Engine` / `LiteRtLmBackend`. Tries `Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)` -> `Backend.GPU()` -> `Backend.CPU()`, logging attempts with tag `"SmritiEngine"` before each attempt on `Dispatchers.IO`, and labels the winning backend (`"$backendName · ${model.label}"`). |
| [`BackendFactory.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/offline/java/com/smriti/app/ai/BackendFactory.kt) | Updated offline factory to attempt `LiteRtLmHolder.get(context)` first when no forced backend/resetPolicy is specified. If it throws or returns null, logs the fallback and runs the existing MediaPipe (`LlmHolder` + `LocalLlmBackend`) path. Forced backend / resetPolicy requests continue to route to the MediaPipe path. |
| [`Extractor.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Extractor.kt) | Added `EXTRACTION_SCHEMA` constant defining the required JSON schema (`actions`, `title`, `summary`, `people`, `amounts`, `tags` with `due` as string or null) matching `parseJson` keys. Passed `jsonSchema = EXTRACTION_SCHEMA` to `activeBackend.generate()` on both attempt 1 and the shortened retry. |
| [`RecordDao.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt) | Added `@Query("UPDATE records SET enrichmentState = 'PENDING' WHERE enrichmentState = 'RUNNING'") suspend fun resetRunningToPending()` to allow recovering records interrupted mid-pass. |
| [`Enricher.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Enricher.kt) | Called `dao.resetRunningToPending()` at the start of `drain()` so records stranded in `RUNNING` state due to process termination are reset to `PENDING` and retried (capped by `enrichmentAttempts < 3`). |
| [`ExtractorJsonTest.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/test/java/com/smriti/app/ai/ExtractorJsonTest.kt) | Added test asserting unconstrained `generate()` calls default `jsonSchema = null`. |

### Verification
Ran `./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest`:
- `:app:assembleOfflineDebug` built cleanly
- `:app:assertNoNetworkPermission` passed (zero network permissions in offline merged manifest)
- `:app:assembleDevcloudDebug` built cleanly
- `:app:testOfflineDebugUnitTest` completed with all 46 tests passing
