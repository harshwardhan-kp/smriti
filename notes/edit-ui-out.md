### Summary of Changes

#### 1. UI & ViewModel: Memory Detail Edit Mode
**File:** [`DetailScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt)

* **`TaskDraft` & `DetailViewModel.save`**:
  * Added [`TaskDraft`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt#L100) (`val id: Long`, `val text: String`, `val done: Boolean`).
  * Implemented [`DetailViewModel.save(...)`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt#L133-L200) executing in `viewModelScope.launch`:
    * Trims `title`, `summary`, and all entries in `people`, `tags`, and `amounts`. Blank titles are stored as `""` (allowing view mode to fall back to `"Untitled"`).
    * Serializes lists with `Gson().toJson(...)`.
    * Recomputes embedding via [`Enricher.embedFor`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Enricher.kt#L155) using the record's raw `transcript`.
    * Calls [`RecordDao.applyUserEdit(...)`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt#L70) passing `at = System.currentTimeMillis()`.
    * Handles tasks according to specifications:
      * Existing task with blank text $\rightarrow$ [`RecordDao.deleteTask`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt#L76).
      * Existing task with text $\rightarrow$ [`RecordDao.updateTask`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt#L73) (preserving existing `dueDateMillis`) and [`RecordDao.setTaskDone`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt#L33).
      * New task (`id == 0L`) with text $\rightarrow$ [`RecordDao.insertTask`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt#L79).
      * New task (`id == 0L`) with blank text $\rightarrow$ ignored.
    * **No date picker was built** — existing task due dates retain whatever `dueDateMillis` value they already have in the database.

* **TopAppBar Interaction**:
  * View mode displays an `Icons.Default.Edit` pencil icon (tinted `ColorCream`, contentDescription `"Edit"`).
  * In edit mode, the actions swap to `Icons.Default.Close` (tinted `ColorCream`, contentDescription `"Discard changes"`) and `Icons.Default.Check` (tinted `ColorAmber`, contentDescription `"Save"`).
  * Pressing the back arrow while in edit mode discards the draft without confirmation dialog.
  * Screen structure and section order remain identical between view and edit modes.

* **Independent Draft State**:
  * The editing draft is held in `remember(recordId) { mutableStateOf<MemoryDraft?>(null) }` and seeded once upon entering edit mode, preventing background Room flow emissions (such as `ocrText` or enrichment state changes) from overriding user edits mid-keystroke.

* **In-Place Editors**:
  * **Title**: Single-line `OutlinedTextField` using `editorTextFieldColors` (`ColorCream` text, `ColorAmber` focused border/cursor, `ColorCream.copy(alpha = 0.3f)` unfocused border, transparent container).
  * **Summary**: Multi-line `OutlinedTextField` (`minLines = 3`) using `editorTextFieldColors`. Replaces summary / thinking shimmer in edit mode.
  * **People, Tags, and Amounts**: Rendered as three distinct blocks labelled `"People"`, `"Tags"`, and `"Amounts"` (`titleMedium`, bold, `ColorAmber`), visible even when empty.
    * Each block displays its chips inside a `FlowRow` keeping their distinctive styles: `@` chips in `ColorChipBg`, `#` chips in amber tint, and amounts with `•` bullet and cream text in `ColorCardBg` with amber border.
    * Each chip features a trailing 14.dp `Icons.Default.Close` (`contentDescription = "Remove <value>"`) to remove it from the draft.
    * Below each row, a single-line `OutlinedTextField` with placeholder `"Add person"`, `"Add tag"`, or `"Add amount"` and an `Icons.Default.Add` IconButton (and IME Done action) appends trimmed text while silently ignoring blanks and exact duplicates.
  * **Tasks**:
    * Rendered inside the existing `Card` with `[Checkbox] [OutlinedTextField] [Icons.Default.Delete, "Delete task"]`.
    * A `"＋ Add task"` TextButton (`ColorAmber`) appends a blank draft row and requests focus via `FocusRequester`.
  * **Evidence Preservation**:
    * Both `CollapsibleRawSection` calls ("What the camera read" and "What you said") remain untouched and read-only in both view and edit modes.

---

#### 2. DAO Guard & AI Drain Defect Fix
**File:** [`RecordDao.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/data/RecordDao.kt)

* **`applyUserEdit`**: Added `enrichmentState = 'DONE', enrichedAt = :at` to the SET clause and added parameter `at: Long`. When a user saves an edit, the record is immediately finalized, halting extraction shimmers and preventing AI enrichment from processing it.
* **`pendingEnrichment`**: Added `AND userEdited = 0` to ensure user-edited records are never re-queued for background model runs.
* **`resetRunningToPending`**: Added `AND userEdited = 0` to prevent records edited while in `RUNNING` state from getting reset to `PENDING` and stuck indefinitely in a queued state.

---

#### 3. Query Unit Tests
**File:** [`RecordDaoQueriesTest.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/test/java/com/smriti/app/data/RecordDaoQueriesTest.kt)

* Updated bytecode constant pool extraction to distinguish `applyEnrichment` by `enrichmentModel = :model` and assert that `applyUserEdit` contains `userEdited = 1`, `enrichmentState = 'DONE'`, and `enrichedAt = :at`.

---

### Verification
* Ran `./gradlew testOfflineDebugUnitTest` — all unit tests passed.
* Ran `./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest` — compiled both flavors and passed tests.
* Ran `./gradlew :app:assertNoNetworkPermission` — confirmed zero network permissions in merged manifests.
