# Hidden Launcher

Minimal, lightweight custom app launcher for Android 11–14 (API 30–34), designed to be triggered by [Keymapper](https://github.com/keymapperorg/Keymapper).

## Features

- **Displays ONLY selected apps** — never shows all apps by default. Empty state if nothing selected.
- **App picker** with search, icons, package names, and checkboxes.
- **Persists selection** via `SharedPreferences` across restarts and reboots (no DB).
- **Compact responsive grid** (3–5 columns depending on screen width) — tap to launch.
- **Grouped folders with polished UI**: long-press an app and drag it onto another — creates a named group. **Closed folder (home screen):** icon-sized rounded/circular `MaterialCardView` container showing a mini-preview of 2–4 app icons (optionally up to 9) arranged in a balanced grid (2×2 for 2–4, 3×3 for 5–9), with folder name below — acts as a single tappable home-screen item (tap **anywhere** on icon/preview/label to open). **Tap → open:** triggers an animated expansion from the folder icon’s position (circular reveal) into a focused overlay with dimmed/blurred background. **Open folder view:** apps laid out in a scrollable grid (3 columns on phones, 4 on tablets, configurable to 3×3/4×4/5×5) with full-sized icons+labels on a customizable popup card panel (rounded 20dp, elevation 8dp); optional “large folder” pattern noted for future direct-on-home reveal. Drag app onto group / group onto app / group onto group to add/merge; long-press group to rename/delete/ungroup. **Remove from group:** while open, long-press an app inside the folder and drag it outside (onto dimmed background) — same drag interaction as creation — to remove; alternatively long-press shows “Remove from group” dialog; groups with ≤1 member dissolve to individual tiles.
- **Graceful handling** of uninstalled/disabled/non-launchable apps (greyed-out, toast, no crash).
- **Discreet settings access**: overflow menu (⋮ → Settings), button in empty state, and long-press on background.
- **Keymapper-ready**: stable `MAIN/LAUNCHER` component, no root/service required.
- **Offline, no permissions**, no background services, no analytics, minimal APK.
- **Signed release APK** available — built with committed keystore for direct install.

## Package & Component

- **Package**: `com.hidden.launcher`
- **Main Activity**: `com.hidden.launcher.MainActivity`
- **Keymapper component string**: `com.hidden.launcher/com.hidden.launcher.MainActivity`

### Keymapper configuration

1. Install the APK (see Releases / Actions artifacts).
2. Open Keymapper → create a trigger (e.g., key, shortcut).
3. Add action: **Launch app** → choose **Hidden Launcher** (or enter component manually: `com.hidden.launcher/com.hidden.launcher.MainActivity`).
4. Save and test.

> **Trebuchet hiding**: You will manually hide this launcher from Trebuchet (or your default launcher). This app does **not** attempt to hide its icon or disable its launcher activity.

## Groups — How to Use

1. Select at least 2 apps in Settings (⋮ → Settings → check apps → Save).
2. On main grid, **long-press one app** — drag shadow appears.
3. **Drag it onto another app** and drop — a dialog prompts for group name (default: "Chrome + Maps"). Tap Create.
4. The grid now shows a **folder tile**: rounded container (56dp, 16dp radius, elevation 2dp) showing a mini-preview grid of member icons balanced (2→2×1, 3→2×2 with placeholder, 4→2×2, 5–6→3×2, 7–9→3×3) with folder name below.
5. **Tap folder** → animated circular-reveal expansion from icon position into dimmed overlay (`#99000000`) + centered card panel; inside a scrollable grid (3 columns phone / 4 tablet) with full icons+labels; tap app to launch (overlay stays for multi-launch), tap dim or ✕ or Back to close with reverse reveal. Footer hint “Tap an app to launch”.
6. **Drag another app onto the group** → adds to that group.
7. **Drag group icon onto another app** → adds that app to the group.
8. **Drag group onto group** → merges.
9. **Long-press group (icon or tile)** → Rename / Delete / Ungroup.
10. If a grouped app is deselected in picker, it is automatically removed from its group; groups with <2 members are dissolved back to individuals (preserves selection, no data loss).

Groups are stored in `SharedPreferences` as JSON (`app_groups`) and survive reboots.

## Permissions

**None.** No `INTERNET`, no `QUERY_ALL_PACKAGES`, no location, etc.

### Package visibility (Android 11+)

Android 11 enforces package visibility restrictions. This app declares in `AndroidManifest.xml`:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
    </intent>
</queries>
```

**Trade-offs evaluated:**

- **`<queries>` with `MAIN` intent (used here)**: Compliant with Play Store policies, no extra permission, sufficient to enumerate all apps that have a launcher activity (i.e., all apps you can actually launch). Recommended by Google. Limitations: apps without a launcher activity are not discoverable — but they cannot be launched anyway, so they are intentionally excluded.
- **`QUERY_ALL_PACKAGES`**: Would guarantee visibility of *all* installed packages including non-launchable ones, but is classified as sensitive by Play Store and requires a declaration/approval. It would also expose all packages unnecessarily. Not needed for this use case.

If your device/OEM launcher uses a non-standard category, and an app is missing from the picker, document it — do not silently add `QUERY_ALL_PACKAGES` without explicit user consent.

The app uses `PackageManager.queryIntentActivities(Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER))` to list launchable apps and `getLaunchIntentForPackage()` to launch, with null checks and `SecurityException` handling.

## SDK versions

- `minSdk = 30` (Android 11)
- `compileSdk = 34`
- `targetSdk = 34`
- Tested range: Android 11, 12, 13, 14
- JDK: 17
- Gradle: 8.7
- Android Gradle Plugin: 8.4.2
- Kotlin: 1.9.24

## Build

### Local

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# Signed release APK: app/build/outputs/apk/release/app-release.apk
# Keystore: app/release.keystore (alias=launcher, storePass=hidden123, keyPass=hidden123)
# Note: This is a demo keystore, not for production — committed for CI convenience
```

Requires JDK 17 and Android SDK (compileSdk 34).

### Signed release

- Keystore at `app/release.keystore` (PKCS12, generated via OpenSSL, friendlyName=launcher)
- Config in `app/build.gradle.kts`:
  ```kotlin
  signingConfigs { create("release") { storeFile = file("release.keystore"); storePassword="hidden123"; keyAlias="launcher"; keyPassword="hidden123" } }
  buildTypes { release { signingConfig = signingConfigs.getByName("release") } }
  ```
- Verify: `apksigner verify --verbose app-release.apk` → verified with v1/v2
- **Production note:** Replace with your own keystore and inject passwords via `~/.gradle/gradle.properties` or env vars — don't reuse demo keystore.

### GitHub Actions

Workflow `.github/workflows/build.yml` runs on push/PR/manual:

- Sets up JDK 17 + Android SDK (sdkmanager platform 34 + build-tools 34.0.0)
- Verifies release keystore
- Gradle cache
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug` → `hidden-launcher-debug-apk`
- `./gradlew assembleRelease` → `hidden-launcher-signed-release-apk` (signed)

Download APKs from **Actions → latest run → Artifacts**.

## Implementation notes

- **Storage**: `SharedPreferences` file `hidden_launcher_prefs` with keys `selected_packages` (StringSet) and `app_groups` (JSONArray JSON). Lightweight, no DB.
- **Groups model**: `AppGroup(id=UUID, name, packageNames)` + `MainItem` sealed (App vs Group). Display builder `PrefsHelper.buildDisplayItems()` keeps groups sorted alphabetically, remainder apps sorted.
- **Drag-and-drop**: Each `item_app` starts `startDragAndDrop(ClipData("app:packageName"))` on long-press; each tile registers `OnDragListener` highlighting drop target (cyan) and calling `handleDrop()` via `PrefsHelper` persistence. Group creation prompts for name; other operations toast and refresh.
- **Selection flow**: Picker loads launchable apps via `AppRepository`, pre-checks saved set, Save persists; Main refreshes in `onResume()`, prunes groups whose members were deselected.
- **Icon caching**: `LruCache<String, Drawable>(100)` in `AppRepository` avoids redundant `loadIcon()` calls. Queries run off UI thread (background Thread) to avoid ANR.
- **No background work**: No `Service`, `BroadcastReceiver`, `WorkManager`, or polling. Battery impact negligible.
- **Offline**: No network permissions, no ads/analytics.

## Project structure

```
app/
  src/main/
    AndroidManifest.xml
    java/com/hidden/launcher/
      MainActivity.kt          # grid + empty state + group drag/drop & dialogs
      AppPickerActivity.kt     # search + checkboxes + save
      MainAdapter.kt / PickerAdapter.kt
      AppRepository.kt         # PackageManager queries + cache
      PrefsHelper.kt           # SharedPreferences + groups JSON
      AppInfo.kt / AppGroup.kt # data models + MainItem sealed
    res/
      layout/ (activity_main, activity_picker, item_app, item_group, item_picker)
      values/ (strings, themes, colors)
      menu/ (menu_main, menu_picker)
      drawable/ (ic_launcher, ic_folder)
  src/test/  # Unit tests
  release.keystore # demo signed keystore
.github/workflows/build.yml
gradle/wrapper/
```

## Testing

- `PrefsHelperTest`: filtering to selected only, empty state, no fallback.
- `AppRepositoryTest`: uninstalled/disabled handling, preserves selections.
- `SelectionPersistenceTest`: round-trip, duplicates, survive reboot.
- `GroupTest`: buildDisplayItems grouping, sorting, JSON round-trip, pruning, groupingAction (create/add/merge), identifier format, drag-to-group creation.

Run: `./gradlew testDebugUnitTest`

Lint: `./gradlew lintDebug` (non-blocking)

Manual on-device checks recommended (no emulator in CI):
- Picker lists all launchable apps with icons.
- Select/deselect persists after force-stop and reboot.
- Main shows only selected, empty state otherwise.
- Drag app onto app → prompt → folder appears; tap → members launch; add/merge/rename/ungroup work.
- Uninstall grouped app → group updates, eventually dissolves.
- Launch via Keymapper trigger.
- Signed APK installs over debug after uninstall.

## APK size

- Debug: ~4–5 MB; Release (signed, unshrunk): ~3–4 MB. Check Actions artifact for exact du -h.

## Limitations

- Only apps with launcher activity appear in picker. System apps/services without launchers excluded (by design).
- Groups are tap-to-open list, not home-screen folders with inline previews — narrow scope.
- Icons cached in memory only; disk cache not needed.
- Refresh on-resume, not via package-change receiver (lightweight, permission-free).
- Demo keystore is public — for testing only; do not publish apps to Play Store with it.

## License

MIT - See repository.
