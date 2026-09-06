### Summary of Changes

1. **[`DueDateResolver.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/DueDateResolver.kt)** (Created)
   - Implemented `DueDateResolver.resolve(actionText, transcript, modelDue, today)` to deterministically resolve due dates based on temporal tokens in the input rather than confabulated model dates.
   - Built haystack as lowercased `actionText + " " + transcript`.
   - Returns `null` if the haystack contains no temporal expressions (weekday names, relative days, relative week/month phrases, month names, 4-digit years, or ISO dates).
   - Computes deterministic target date for weekdays (next occurrence strictly after `today`, taking the first weekday if multiple occur) and relative days (`today`, `tonight`, `tomorrow`, `day after tomorrow`), overriding `modelDue`.
   - For other temporal expressions, validates `modelDue` as strict ISO-8601 `LocalDate` or returns `null`.
   - Rejects any date further than 2 years from `today` in either direction.
   - Included a TODO noting that Whisper emits Devanagari for Hindi speech and those tokens are not yet matched.

2. **[`Extractor.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Extractor.kt)** (Modified)
   - Added the explicit instruction to [`buildPrompt()`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Extractor.kt#L121-L136): `due must be null unless the person actually stated a day or date; never guess one.` Preserved the actions-first ordering and existing worked example.
   - Threaded `transcript` into `parseJson` from `extract()` and `retryShortened()`.
   - In [`parseJson`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ai/Extractor.kt#L240-L265), passed the extracted `rawDue` through `DueDateResolver.resolve(text, transcript, rawDue, LocalDate.now())` when constructing `Action` objects, preserving existing key aliases.

3. **[`ExtractorJsonTest.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/test/java/com/smriti/app/ai/ExtractorJsonTest.kt)** (Modified)
   - Added pinned unit tests verifying all specified scenarios against Saturday 2026-09-05 (`today`):
     - Next occurrence strictly after Saturday for `"by Friday"` -> `"2026-09-11"`
     - Non-temporal transcript returning `null` despite model date
     - `"tomorrow"` -> `"2026-09-06"`
     - Non-temporal words returning `null`
     - Out-of-range dates (> 2 years) returning `null`
     - Weekday that is today returning next week (`"2026-09-12"`)

### Verification
- Both build flavors and unit tests passed cleanly:
  ```bash
  ./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
  ```
