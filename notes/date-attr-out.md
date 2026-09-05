### Summary of Changes

#### 1. Clause-Based Temporal Attribution in [`DueDateResolver.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/DueDateResolver.kt)
- **Eliminated Whole-Transcript Concatenation**: Replaced the previous `actionText + " " + transcript` haystack construction that erroneously attached deadlines in one clause (such as `"by Friday"`) to unrelated actions in other clauses.
- **Rule 1 (Action Text Priority)**: If the `actionText` itself contains a temporal expression, resolution proceeds using `actionText` alone with no transcript involvement.
- **Rule 2 (Clause Splitting)**: Transcript is split into ordered, lowercased clauses delimited by `","`, `";"`, `" and "`, `" then "`, and `" after that "`.
- **Rule 3 (Temporal Expression Presence)**: If no clause in the transcript contains a temporal expression, returns `null`.
- **Rule 4 & 6 (Clause Disambiguation & Overlap)**:
  - Multi-clause transcripts find the best overlapping clause via case-insensitive word-set intersection against `actionText`, excluding 19 stop words (`the`, `a`, `an`, `to`, `of`, `for`, `from`, `we`, `i`, `you`, `it`, `and`, `more`, `need`, `needs`, `by`, `is`, `are`, `be`).
  - Requires the best overlap to be $\ge 1$ real word.
  - Returns `null` if the best overlap ties across multiple clauses (ambiguity guard).
- **Rule 5 (Attribution Target Check)**:
  - If the winning clause contains a temporal expression, resolution proceeds from that clause.
  - If the winning clause does not contain a temporal expression, returns `null` (preventing actions like `"Need two hundred units"` from inheriting dates from neighboring clauses like `"Rohit ships the API by Friday"`).
- **Preserved Existing Date Calculation Rules**:
  - Weekdays resolve to strictly the next occurrence after `today` using `TemporalAdjusters.next()`.
  - Relative tokens (`today`, `tonight`, `tomorrow`, `day after tomorrow`) compute relative to `today`.
  - Strict ISO-8601 validation for other temporal tokens via `modelDue`.
  - $\pm 2$ year range guard.
- **Exposed Attribution Helper**: Extracted `attributeClause(actionText, transcript): String?` (and alias `attribute`) as `internal` functions for direct unit testing while keeping `resolve(actionText, transcript, modelDue, today)` signature intact.

#### 2. Tests Added in [`ExtractorJsonTest.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/test/java/com/smriti/app/ai/ExtractorJsonTest.kt)
- Added `testDueDateClauseAttribution()` with pinned date Saturday 2026-09-05 (`today`):
  - `resolve("Rohit ships the API", "Rohit ships the API by Friday and we need two hundred more units from Sharma Traders", "2026-09-09", today)` $\rightarrow$ `"2026-09-11"`
  - `resolve("Need two hundred units from Sharma Traders", "Rohit ships the API by Friday and we need two hundred more units from Sharma Traders", "2026-09-09", today)` $\rightarrow$ `null` (verifies defect fix)
  - `resolve("call the supplier tomorrow", "call the supplier tomorrow", null, today)` $\rightarrow$ `"2026-09-06"` (verifies Rule 1)
  - `resolve("pay Sharma", "pay Sharma on Monday", null, today)` $\rightarrow$ `"2026-09-07"` (verifies single-action transcript)
  - Direct tests for `attributeClause` verifying clause selection, overlap tie rejection (`assertNull`), and other delimiters (`","`, `";"`, `" then "`, `" after that "`).
- Preserved all existing date tests in `testDueDateResolverPinnedCases()` without modification.

---

### Verification
Executed the required build and test tasks:
```bash
./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
```
- **`assembleOfflineDebug`**: BUILD SUCCESSFUL (all 3 manifests clean of network permissions)
- **`assembleDevcloudDebug`**: BUILD SUCCESSFUL
- **`testOfflineDebugUnitTest`**: BUILD SUCCESSFUL (all 53 tests passed)
