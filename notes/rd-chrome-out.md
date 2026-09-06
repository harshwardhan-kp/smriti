### Summary of Changes

#### 1. [`EnrichingIndicator.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/EnrichingIndicator.kt)
- **Imports & Tokens**: Replaced imports of deprecated `ColorAmber` and `Cream` with `com.smriti.app.ui.theme.S`. Swapped `ColorAmber` with `S.Amber` to represent work in progress, and eliminated cream-based fallbacks.
- **`ThinkingDots`**:
  - Resized Canvas from `26x8 dp` to `26x6 dp`.
  - Replaced circle drawing with rounded squares using `drawRoundRect` (`CornerRadius(2.dp.toPx())`).
  - Size interpolates from `3.5.dp` to `5.dp` (`minSize = 3.5.dp.toPx()`, `maxSize = 5.dp.toPx()`), maintaining the existing timing, stagger, alpha (`0.35f` to `1.0f`), and `isReducedMotionEnabled` behavior.
  - Centering: Maintained dot centers at `dot0X = 4.5.dp.toPx()`, `dot1X = 13.dp.toPx()`, and `dot2X = 21.5.dp.toPx()`, which symmetrically places the 5.dp squares across the 26.dp width (leaving 2.0.dp outer margins and 3.5.dp gaps between squares).
- **`ShimmerLine`**:
  - Updated gradient sweep brush to transition between `S.PaperSunk` and `S.Hairline.copy(alpha = 0.9f)`.
  - Added `RoundedCornerShape(2.dp)` clipping and adjusted Canvas `CornerRadius` to `2.dp.toPx()`.

---

#### 2. [`MainActivity.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/MainActivity.kt) (PermissionGate Only)
- Touched nothing above [`PermissionGate`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/MainActivity.kt#L208) except for removing unused imports (`Button`, `TextAlign`) and adding required imports (`Row`, `background`, [`BracketLabel`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L56), [`DisplayHeading`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L290), [`SmritiButton`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L177), [`S`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Tokens.kt#L19), [`SmritiType`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Type.kt#L70)).
- Restyled the permission failure state:
  - Top-aligned and left-aligned container: `Box` with `.background(S.Paper)`, `contentAlignment = Alignment.TopStart`, and padding `horizontal = S.gutter`, `top = S.section`.
  - Heading: `DisplayHeading(text = "smriti needs two things", italicWord = "two", style = SmritiType.Display, color = S.Ink)`.
  - Body: Lowercase description of permissions' purpose (`"the camera, to read what is in front of you. the microphone, to hear what you say. nothing leaves this phone."`) using `SmritiType.Body` and `S.Muted`.
  - Labels: Two [`BracketLabel`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L56) components on a Row spaced by `S.md`: `BracketLabel("camera")` and `BracketLabel("microphone")`.
  - Button: Replaced Material Button with `SmritiButton(label = "grant", onClick = { launcher.launch(requiredPermissions) }, primary = true)`.

---

#### 3. [`BubbleService.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/capture/BubbleService.kt)
- **Drawables**:
  - `normalDrawable`: Idle capture bubble set to `0xE6EEEEEE.toInt()` (`S.Paper` at 90%) fill with `0xFFE10909.toInt()` (`S.Red`) stroke.
  - `liveDrawable`: Active capture bubble set to `0xFFE10909.toInt()` (`S.Red`) fill with `0xFFEEEEEE.toInt()` (`S.Paper`) stroke.
  - `micNormalDrawable`: Idle mic bubble set to `0xE60D0D0D.toInt()` (`S.Ink` at 90%) fill with `0xFFEEEEEE.toInt()` (`S.Paper`) stroke.
  - `micLiveDrawable`: Active mic bubble set to `0xFFE10909.toInt()` (`S.Red`) fill with `0xFFEEEEEE.toInt()` (`S.Paper`) stroke.
- **Notification Text**: Updated notification string in `createNotification()` to:
  `"hold the light bubble for the screen · hold the dark one for a voice note"`

---

### Verification
- **No references to `ColorAmber` or `Cream`**: Confirmed via grep across `app/src` that neither symbol is referenced anywhere outside their `@Deprecated` declarations in [`Theme.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Theme.kt#L31-L35).
- **Build & Tests**: Executed `./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest`. Both build variants assembled cleanly, unit tests passed, and `:app:assertNoNetworkPermission` succeeded with zero network permissions in merged manifests.
