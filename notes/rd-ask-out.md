Restyled [`AskScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/AskScreen.kt) to conform to the visual design language of antimattr.one defined in [`Tokens.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Tokens.kt), [`Type.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Type.kt), and [`Primitives.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt).

### Summary of Changes

1. **Tokens & Colors**:
   - Deleted the four private `Color(0xFF...)` declarations (`ColorAmber`, `ColorInk`, `ColorCream`, `ColorCardBg`) and replaced all occurrences with tokens from [`S`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Tokens.kt) (`S.Paper`, `S.Ink`, `S.Muted`, `S.MutedSoft`, `S.Red`, `S.Hairline`).
   - Zero `Color(0xFF...)` literals remain in the file.

2. **Scaffold & Top App Bar**:
   - `Scaffold` container colour set to `S.Paper`.
   - `TopAppBar` themed using `TopAppBarDefaults.topAppBarColors(containerColor = S.Paper, titleContentColor = S.Ink, navigationIconContentColor = S.Ink)`.
   - Title set to [`DisplayHeading`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt)`(text = "ask", italicWord = "ask", style = SmritiType.DisplaySmall, color = S.Ink)`.
   - Back navigation icon preserved and tinted with `S.Ink`.

3. **Query Field**:
   - `OutlinedTextField` configured with `shape = S.r6`, `textStyle = SmritiType.Body`, and custom colors (`focusedTextColor = S.Ink`, `unfocusedTextColor = S.Ink`, `focusedBorderColor = S.Red`, `unfocusedBorderColor = S.Hairline`, `cursorColor = S.Red`, `focusedContainerColor = Color.Transparent`, `unfocusedContainerColor = Color.Transparent`).
   - Placeholder text lowercased to `"what did i commit to this week?"`, styled with [`SmritiType.Body`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Type.kt) in `S.MutedSoft`.
   - Mic `IconButton`'s icon tint set to `S.Muted` (the file and `AskViewModel` do not maintain an active listening state for this input).

4. **Ask Button**:
   - Replaced Material 3 `Button` with [`SmritiButton`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt)`(label = "ask", onClick = { vm.ask(query) }, primary = true, enabled = !busy && query.isNotBlank(), modifier = Modifier.fillMaxWidth())`.

5. **Thinking & Answer Area**:
   - Busy loading state restyled with `ThinkingDots()` alongside [`BracketLabelLive("thinking")`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt).
   - Answer `Card` removed in favor of a clean, unboxed `Column` preceded by [`HairlineRule()`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt) padded with `S.lg` above and below.
   - Header updated to [`SectionLabel("answer")`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt) with the answer text styled in [`SmritiType.Body`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Type.kt) and `S.Ink`.

6. **Evidence / Result Card**:
   - Evidence `Card` replaced with a clickable `Column` (`padding(vertical = S.gutter)`) terminated with [`HairlineRule()`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt).
   - Preceded by [`SectionLabel("evidence")`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt).
   - Evidence photo clipped to `S.r6` while preserving the `BitmapFactory.decodeFile` sample-sizing logic.
   - Supporting label lowercased to `"tap to view full record"`, styled in [`SmritiType.BodySmall`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Type.kt) and colored `S.Muted`.
   - `onOpenRecord` invocation and ID propagation kept exactly as they were.

7. **Spacing & Rhythm**:
   - Horizontal screen padding set to `S.gutter`, with `S.lg` spacing between blocks and `S.section` bottom padding.

---

### Verification

- Build and tests executed successfully:
  ```bash
  ./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
  ```
- `:app:assertNoNetworkPermission`: clean (no network permissions declared).
