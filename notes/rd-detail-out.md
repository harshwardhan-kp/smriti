### Summary of Changes in [`DetailScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt)

The memory detail screen has been restyled according to the antimattr.one design language and house rules:

1. **Eliminated Literal Colours & Private Val Declarations:**
   - Deleted private colour declarations (`ColorAmber`, `ColorInk`, `ColorCream`, `ColorCardBg`, `ColorChipBg`) and removed all hardcoded dividers/borders.
   - Mapped all screen colours to tokens in `S` (`S.Paper`, `S.Ink`, `S.Muted`, `S.MutedSoft`, `S.Red`, `S.Hairline`, `S.White`).
   - **Confirmed:** Zero `Color(0xFF...)` literals remain anywhere in [`DetailScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt).

2. **Scaffold & Top App Bar:**
   - `Scaffold` container colour set to `S.Paper`.
   - `TopAppBar` themed with `S.Paper` background and `S.Ink` content/icon tinting.
   - Swapped title text to [`DisplayHeading`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt) showing `"memory"` (italicizing `"mem"`) in viewing mode, and `"editing"` (italicizing `"edit"`) in edit mode with `SmritiType.DisplaySmall`.
   - Re-tinted action icons: Edit icon to `S.Ink`, Discard (close) icon to `S.Muted`, and Save (check) icon to `S.Red`.

3. **Photograph & Voice Note Representation:**
   - Changed image clipping corner radius from `16.dp` to `S.r6`.
   - For voice-only memories (`photoBitmap == null && currentRecord.photoPath.isBlank()`), replaced the frame with a row containing [`NodeSquare()`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt) and [`BracketLabel("voice note")`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt) with no bounding box.

4. **Metadata Line & Copied Helper:**
   - Copied `formatRelativeTime(createdAt: Long): String` from [`TimelineScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/TimelineScreen.kt) as a private function in [`DetailScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt).
   - Added a metadata `Row` directly above the title displaying `[formatted relative time]` and `[model name]` (when present) using [`BracketLabel`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt) spaced by `S.md`.

5. **Title & Summary:**
   - In view mode: title set to `SmritiType.Display` in `S.Ink` (fallback `"untitled"`), summary set to `SmritiType.Body` in `S.Muted` without manual `lineHeight`.
   - In edit mode: title `OutlinedTextField` set to `textStyle = SmritiType.Display` and summary `OutlinedTextField` set to `textStyle = SmritiType.Body`.

6. **Enrichment States:**
   - Maintained state logic while replacing raw styled text: `ThinkingDots()` alongside `BracketLabel("understanding")` / `BracketLabel("queued")` with shimmer lines, and `BracketLabel("could not extract")` for failures.
   - Retinted loading `CircularProgressIndicator` to `S.Red`.

7. **Section Headings & Chips:**
   - Replaced all section headers ("amounts", "tasks", "people", "tags") with [`SectionLabel(...)`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt) in lowercase, preceded by `Spacer(S.lg)` and followed by `Spacer(S.sm)`.
   - Deleted all custom chip `Surface`s in view and edit modes; replaced with [`SmritiChip`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt) (`ChipKind.Person`, `ChipKind.Tag`, `ChipKind.Amount`), removing `"@ "` and `"# "` prefixes.
   - In edit mode, added trailing close icons sized `12.dp` with `S.MutedSoft` tint preserving existing item removal callbacks.
   - View mode amounts now render as an unboxed `FlowRow` of `ChipKind.Amount` chips spaced by `S.sm`.

8. **Tasks:**
   - Deleted `Card` wrappers. Each task renders in a `Row` padded with `vertical = S.sm` followed by a [`HairlineRule()`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt).
   - `Checkbox` styled with `checkedColor = S.Red`, `checkmarkColor = S.White`, `uncheckedColor = S.Hairline`.
   - Text styled `SmritiType.Body` (`S.Ink` when open, `S.MutedSoft` with `TextDecoration.LineThrough` when done).
   - Replaced the add task button with [`SmritiOutlineButton(label = "add task", ...)`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt).

9. **Inputs Treatment (`ListChipEditor`, Title, Summary, Task):**
   - Standardized all `OutlinedTextField` components with `shape = S.r6`, `colors = editorTextFieldColors` (`cursorColor = S.Red`, `focusedBorderColor = S.Red`, `unfocusedBorderColor = S.Hairline`), lowercased placeholders in `S.MutedSoft`, and add icon button in `S.Red`.

10. **Evidence Sections ([`CollapsibleRawSection`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/DetailScreen.kt)):**
    - Preceded the section with `Spacer(S.lg)`, [`DashedRule()`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt), and `Spacer(S.lg)`.
    - Removed `Card` wrappers, borders, and backgrounds in favor of a clean clickable `Column` with `padding(vertical = S.md)`.
    - Title converted to `SectionLabel(title.lowercase())` ("what the camera read", "what you said").
    - Chevron icon replaced with mono text `"−"` / `"+"` in `SmritiType.MetaMedium` tinted `S.Red` using `Modifier.semantics { contentDescription = ... }`.
    - Expanded machine output styled with `SmritiType.Mono` in `S.Muted`, separated by a [`HairlineRule()`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt).

11. **Page Padding & Footer:**
    - Main scrolling `Column` content padding set to `S.gutter`.
    - Added `Spacer(modifier = Modifier.height(S.section))` at the bottom.

---

### Verification

- Ran `./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest`:
  - `:app:assertNoNetworkPermission`: clean (no network permissions in manifests).
  - Both flavors built successfully.
  - All unit tests passed.
