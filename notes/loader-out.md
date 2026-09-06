### Summary of Changes

#### 1. Timeline Record Card Working Indicator ([`TimelineScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/TimelineScreen.kt#L358-L397))
* Imported [`CircularProgressIndicator`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/TimelineScreen.kt#L30).
* In [`RecordCard`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/TimelineScreen.kt#L280), replaced the static summary display with a branch on `record.enrichmentState`:
  * **`RUNNING`**: Displays a 14.dp `CircularProgressIndicator` (stroke width 2.dp, tinted with theme [`ColorAmber`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/TimelineScreen.kt#L64)) accompanied by `"Understanding…"` in muted `bodySmall` typography (`ColorCream.copy(alpha = 0.55f)`).
  * **`PENDING`**: Displays the same spinner accompanied by `"Queued"`.
  * **`FAILED`**: Displays a quiet, non-alarming `"Could not extract"` text in muted `bodySmall` typography without a spinner.
  * **`DONE` / default**: Retains the existing summary rendering behavior untouched.
* Card layout, thumbnail, title, timestamps, and tags remain untouched.

#### 2. Detail Screen Model-Derived Working Indicator ([`DetailScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt#L217-L256))
* Replaced the summary location in [`DetailScreen`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt#L122) with the same state handling:
  * **`RUNNING`** / **`PENDING`**: Displays a 14.dp `CircularProgressIndicator` (stroke width 2.dp, tinted [`ColorAmber`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt#L78)) beside `"Understanding…"` or `"Queued"`.
  * **`FAILED`**: Displays `"Could not extract"` in muted text.
  * **`DONE` / default**: Displays the full extracted summary as before.
* Fast capture elements (photo, title, *"What you said"* raw transcript, and camera OCR sections) continue to render immediately and normally.

#### 3. Live-Updating & DetailScreen Observing Query ([`RecordDao.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt#L23-L24) & [`DetailScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt#L94-L107))
* **Verified `TimelineViewModel` query**: [`RecordDao.observeRecords()`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt#L11) executes `SELECT * FROM records ORDER BY createdAt DESC`, which selects the full row and automatically propagates updates to `enrichmentState`.
* **DetailScreen Investigation**: **`DetailScreen` was previously fetching the record once** using the suspend function `dao.getRecord(recordId)` in `DetailViewModel.load()`, rather than observing it. As a result, detail views opened immediately after capture would have remained stuck on `"Understanding…"` until navigating away and back.
* **Added Observing Query**:
  * Added `@Query("SELECT * FROM records WHERE id = :id") fun observeRecord(id: Long): Flow<RecordEntity?>` to [`RecordDao`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt#L23-L24).
  * Updated [`DetailViewModel.load(recordId)`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt#L96-L107) to observe `dao.observeRecord(recordId)` via a cancellable coroutine `Job`, enabling real-time UI transitions from `"Queued"` $\rightarrow$ `"Understanding…"` $\rightarrow$ `"DONE"`.

---

### Verification
Ran `./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest`:
* `assembleOfflineDebug`: **Passed** (verified `assertNoNetworkPermission` clean: 0 network permissions).
* `assembleDevcloudDebug`: **Passed**.
* `testOfflineDebugUnitTest`: **Passed** (all unit tests green).
