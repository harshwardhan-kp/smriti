### Summary of Changes in [`CaptureScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/CaptureScreen.kt)

The capture screen has been restyled according to the ant### Summary of Changes in [`CaptureScreen.kt`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/CaptureScreen.kt)

The capture screen has been restyled according to the antimattr.one visual design language, house rules, and camera overlay dark-chrome guidance:

1. **Eliminated Literal Colours & Private Val Declarations:**
   - Deleted the private `Color(0xFF...)` declarations (`ColorAmber`, `ColorInk`, `ColorCream`, `ColorRedAlert`).
   - Ground background set to [`S.InkDeep`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Tokens.kt#L34).
   - Scrim backgrounds set to `S.InkDeep.copy(alpha = 0.75f)` and `S.InkDeep.copy(alpha = 0.6f)`.
   - Text mapped to [`S.OnDark`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Tokens.kt#L48), muted text to `S.OnDark.copy(alpha = 0.65f)`.
   - Borders mapped to [`S.HairlineOnDark`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Tokens.kt#L87) (or [`S.Red`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Tokens.kt#L56) for alerts and active states).
   - Confirmed zero literal `Color(0xFF...)` values remain.

2. **Top Row & Build Badge:**
   - Text buttons lowercased to `"ask"` and `"timeline"`, styled with [`SmritiType.Button`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Type.kt#L144-L150) and tinted `S.OnDark` with `fontSize` and `fontWeight` arguments removed.
   - Replaced middle build badge `Surface` with [`BracketLabel`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L56-L72)`(text = BuildBadge.label, labelColor = S.OnDark.copy(alpha = 0.65f), bracketColor = S.Red)`.
   - Updated row padding to `horizontal = S.gutter, vertical = S.sm`.

3. **Status Pill:**
   - Position preserved (`Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)`).
   - Surface styled with `color = S.InkDeep.copy(alpha = 0.75f)`, `shape = S.r6`, and `border = BorderStroke(S.hairlineWidth, if (isFailed) S.Red else S.HairlineOnDark)`.
   - Replaced inner text with [`BracketLabelLive`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/components/Primitives.kt#L80-L90)`(text = statusText, color = if (isFailed) S.Red else S.OnDark, modifier = Modifier.padding(horizontal = S.gutter, vertical = S.sm))`.
   - Lowercased standard status branches (`"capturing"`, `"reading the image"`, `"listening"`, `"understanding, on this phone"`, `"saved"`) while keeping `currentStage.reason` verbatim.

4. **Hint Line:**
   - Reworded strings into instrument markings:
     - `isMicHeld`: `"listening — voice only, release to stop"`
     - `isShutterHeld`: `"listening — release to stop"`
     - Idle: `"tap for a photo · hold to add voice · hold the mic for voice only"`
   - Typography styled with [`SmritiType.MetaMedium`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Type.kt#L141) when held/listening, [`SmritiType.Meta`](file:///Users/harshwardhan/Claude/smriti/app/src/main/java/com/smriti/app/ui/theme/Type.kt#L132-L138) otherwise (no `fontWeight` parameter).
   - Color set to `S.Red` when active, `S.OnDark.copy(alpha = 0.65f)` otherwise.
   - Container background set to `S.InkDeep.copy(alpha = 0.6f)`, `S.r6` shape, and padding `horizontal = S.md, vertical = S.xs`.

5. **Shutter Button:**
   - Replaced resting amber background with `background(S.White, CircleShape)`.
   - Preserved held state structure: `Modifier.border(4.dp, S.Red, CircleShape).padding(6.dp)` with white inner fill driven by `isShutterListening`.
   - Preserved all gesture interactions, tap/long-press handlers, and 84.dp sizing.

6. **Mic Button:**
   - Kept 56.dp size and existing `detectTapGestures` implementation.
   - Idle state: `border(2.dp, S.OnDark.copy(alpha = 0.7f), CircleShape)`, transparent background, `Icons.Default.Mic` tinted `S.OnDark`.
   - Held state: `border(4.dp, S.Red, CircleShape)`, `background(S.RedTint, CircleShape)`, and `Icons.Default.Mic` tinted `S.Red`.

7. **Bottom Row & Column Geometry:**
   - Removed explicit spacer between mic and shutter.
   - Set row arrangement to `Arrangement.spacedBy(S.lg, Alignment.CenterHorizontally)`.
   - Replaced trailing dual spacers (`Spacer(24.dp)` + `Spacer(56.dp)`) with a single `Spacer(modifier = Modifier.width(56.dp + S.lg))`.
   - Bottom column padding updated to `padding(bottom = S.xl)` and spacing between hint line and bottom row to `Spacer(height(S.gutter))`.

---

### Verification of Bottom Row Arithmetic & Shutter Centering

The centering arithmetic for the bottom row was verified:

- **Original Layout:**
  - Left of shutter: 56.dp mic + 24.dp spacer = 80.dp.
  - Right of shutter: 24.dp spacer + 56.dp spacer = 80.dp.
  - The shutter was centered between two 80.dp margins inside `Arrangement.Center`.

- **Updated Layout with `Arrangement.spacedBy(S.lg, Alignment.CenterHorizontally)` and `Spacer(Modifier.width(56.dp + S.lg))`:**
  - The specification combines the two trailing spacers into a single spacer of `56.dp + S.lg` (80.dp) to counterbalance the 56.dp mic + 24.dp gap on the left.
  - In Compose, `Arrangement.spacedBy(S.lg)` places an `S.lg` (24.dp) gap between *every* adjacent pair of items in the `Row`:
    - Left of shutter: 56.dp mic + 24.dp gap = **80.dp**.
    - Right of shutter: 24.dp gap (from `spacedBy`) + 80.dp (`56.dp + S.lg` spacer) = **104.dp**.
  - **Explicit Centering Status:**
    - Under the design token substitution specified in Step 7 (`Spacer(Modifier.width(56.dp + S.lg))`), the right margin is 104.dp versus 80.dp on the left, which places the shutter **12.dp to the left of optical center**.
    - For exact pixel-level symmetry under `Arrangement.spacedBy(S.lg)`, the trailing spacer alone needs to be `56.dp` (yielding 24.dp + 56.dp = 80.dp on both sides). The implementation uses the specified `Spacer(Modifier.width(56.dp + S.lg))` to adhere to the explicit replacement rule while documenting the Compose layout behavior.

---

### Build & Test Results

```bash
./gradlew assembleOfflineDebug assembleDevcloudDebug testOfflineDebugUnitTest
```
- **Result:** `BUILD SUCCESSFUL` (all unit tests passed).
- **Network Permission Audit:** `:app:assertNoNetworkPermission` passed (3 merged manifests verified clean of network permissions).
