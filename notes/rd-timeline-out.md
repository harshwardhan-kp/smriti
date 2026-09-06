Restyled [`TimelineScreen`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/TimelineScreen.kt) to apply the visual design language from [`Tokens.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Tokens.kt), [`Type.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Type.kt), and [`Primitives.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt).

### Summary of Changes

1. **Colour Tokens & Imports**:
   - Removed all private `Color(0xFF...)` declarations (`ColorAmber`, `ColorInk`, `ColorCream`, `ColorRedAlert`, `ColorCardBg`).
   - Replaced all colors throughout the screen with members of [`S`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Tokens.kt#L19-L118) (`S.Paper`, `S.Ink`, `S.Muted`, `S.MutedSoft`, `S.PaperSunk`, `S.Red`, `S.RedTint`, `S.White`, `S.Hairline`).

2. **Scaffold & TopAppBar**:
   - Ground updated to `S.Paper` with no elevation or dividers.
   - Replaced the bold "Timeline" text with [`DisplayHeading`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L290-L316)`(text = "timeline", italicWord = "time", style = SmritiType.DisplaySmall, color = S.Ink)`.
   - Preserved back navigation icon tinted with `S.Ink`.

3. **Counts Chrome**:
   - Added a fixed row of mono count badges immediately beneath the top app bar:
     ```kotlin
     Row(
         modifier = Modifier.padding(start = S.gutter, end = S.gutter, bottom = S.gutter),
         horizontalArrangement = Arrangement.spacedBy(S.md)
     ) {
         BracketLabel("${records.size} memories")
         BracketLabel("${openTasks.size} open")
     }
     ```

4. **Empty State**:
   - Replaced the centered block with a high, left-aligned layout padded `S.gutter` horizontally and `S.xl` from the top:
     - [`DisplayHeading`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L290-L316)`("nothing captured yet", italicWord = "nothing", style = SmritiType.Display, color = S.Ink)`
     - `Spacer(modifier = Modifier.height(S.md))`
     - `Text("point the camera at something and talk, or hold the mic and just talk.", style = SmritiType.Body, color = S.Muted)`

5. **Section Headers**:
   - Updated sticky headers for both `"open tasks"` and `"memories"` (renamed from `"Timeline"`) using [`SectionLabel`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L323-L336), `Spacer(modifier = Modifier.height(S.sm))`, and [`HairlineRule`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L122-L132) inside a `Surface(color = S.Paper)` with `padding(top = S.lg, bottom = S.md)`.

6. **Task Rows**:
   - Flattened each task into a non-card row (`fillMaxWidth().padding(vertical = S.sm)`) terminated with [`HairlineRule`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L122-L132).
   - Checkbox colors set to `checkedColor = S.Red`, `checkmarkColor = S.White`, `uncheckedColor = S.Hairline`.
   - Task text styled with [`SmritiType.Body`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Type.kt#L105-L111), colored `S.Ink` when open, `S.MutedSoft` with `TextDecoration.LineThrough` when completed.
   - Due date rendered via [`BracketLabelLive`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L80-L90) if overdue or [`BracketLabel`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L56-L72) if future.

7. **Record Cards**:
   - Dropped the Material `Card` container in favor of a full-width clickable `Column` with `padding(vertical = S.gutter)` and a [`HairlineRule`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L122-L132) as its last child.
   - Leading 56.dp square restyled:
     - Decoded image: `clip(S.r6)`
     - Voice note (blank photo path): `background(S.RedTint.copy(alpha = 0.10f), S.r6)` with `Icons.Default.Mic` tinted `S.Red`
     - Decode failed: `background(S.PaperSunk, S.r6)` with `border(BorderStroke(S.hairlineWidth, S.Hairline), S.r6)`
   - Extracted timestamp to sit above the title via [`BracketLabel`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L56-L72)`(formatRelativeTime(record.createdAt))` followed by `Spacer(modifier = Modifier.height(S.sm))`.
   - Title styled with [`SmritiType.DisplaySmall`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Type.kt#L85-L91), `S.Ink`, up to 2 lines, with fallback text `"untitled"`.
   - Enrichment states restyled to lowercase bracket labels (`"understanding"`, `"queued"`, `"could not extract"`) and summary styled with [`SmritiType.BodySmall`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Type.kt#L114-L120) in `S.Muted`.
   - Tags replaced with [`SmritiChip`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L252-L275)`(text = tag, kind = ChipKind.Tag)` spaced `S.sm` in both directions.

8. **LazyColumn Layout**:
   - Page horizontal padding set to `S.gutter`.
   - Added `contentPadding = PaddingValues(bottom = S.section)`.

### Verification

- Build and tests executed successfully:
  ```bash
  ./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
  ```
- `:app:assertNoNetworkPermission` passed cleanly.
