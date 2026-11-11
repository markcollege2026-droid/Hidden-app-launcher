# Hidden Launcher

Minimal, lightweight custom app launcher for Android 11–14 (API 30–34), designed to be triggered by [Keymapper](https://github.com/keymapperorg/Keymapper).

## Features

- **Displays ONLY selected apps** — never shows all apps by default. Empty state if nothing selected.
- **Instant startup & refresh** — the first frame paints cached app names/tiles behind lightweight skeleton placeholders; metadata and icons stream in without ever flashing “No apps selected” when apps are selected.
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
- `./gradlew lintDebug` (warnings non-blocking; the `Fail on lint errors` step fails the build on lint errors)
- Unit-test & lint summary printed to the log; reports uploaded as `test-and-lint-reports`
- `./gradlew assembleDebug` → `hidden-launcher-debug-apk`
- `./gradlew assembleRelease` → `hidden-launcher-signed-release-apk` (signed)

Download APKs from **Actions → latest run → Artifacts**.

## Implementation notes

- **Storage**: `SharedPreferences` file `hidden_launcher_prefs` with keys `selected_packages` (StringSet) and `app_groups` (JSONArray JSON). Lightweight, no DB.
- **Groups model**: `AppGroup(id=UUID, name, packageNames)` + `MainItem` sealed (App vs Group). Display builder `PrefsHelper.buildDisplayItems()` keeps groups sorted alphabetically, remainder apps sorted.
- **Drag-and-drop**: Each `item_app` starts `startDragAndDrop(ClipData("app:packageName"))` on long-press; each tile registers `OnDragListener` highlighting drop target (cyan) and calling `handleDrop()` via `PrefsHelper` persistence. Group creation prompts for name; other operations toast and refresh.
- **Selection flow**: Picker loads launchable apps via `AppRepository`, pre-checks saved set, Save persists (and remembers labels for the instant first paint); Main refreshes on resume, pruning groups whose members were deselected.
- **Icon caching**: one shared `LruCache<String, Drawable>(100)` for the whole app (used by picker *and* home) so an icon is never loaded twice. Cached icons are painted immediately; missing ones are loaded one-by-one off the UI thread and pop into their tiles.
- **Selected-only loading**: the home screen never enumerates installed packages. It asks PackageManager only about the selected packages (`getPackageInfo`/`getApplicationLabel`/`getLaunchIntentForPackage`, plus one icon load each) on a background executor; the picker is the only screen that enumerates all launchable apps. Resolved launch intents are cached and invalidated with the rest of the package caches, so tapping a tile does not hit PackageManager on the UI thread.
- **Persistent metadata cache**: `AppMetadataCache` keeps a tiny JSON record (label, enabled flag, versionCode, lastUpdateTime) per selected package in `SharedPreferences`. It gives real names on the first frame and is invalidated on package add/remove/change/replace and whenever a refresh sees a new version/update stamp. It is *never* used as an availability source: availability is always re-read live, and it is pruned to the current selection (max 200 entries).
- **Refresh coordination**: `RefreshScheduler` allows one refresh at a time, coalesces bursts into a single follow-up, discards outdated results (token + selection signature) and skips refreshing when returning from a launched app if nothing changed. Returning from Settings or a package-change broadcast still refreshes.
- **Package changes**: a dynamic (in-process only) receiver for `PACKAGE_ADDED/REMOVED/CHANGED/REPLACED` registered in `onCreate`/`onDestroy`; broadcasts for packages outside the selection are ignored. No manifest receiver, no service, no polling, no permissions.
- **No background work**: no `Service`, no manifest `BroadcastReceiver`, no `WorkManager`, no polling. Battery impact negligible.
- **Offline**: No network permissions, no ads/analytics.

## Startup & refresh performance

This revision focuses on eliminating the blank/empty-screen flash and on doing the
minimum PackageManager work needed to show the home screen. No architecture or feature
changes: still "selected apps only", in-memory icon cache, `SharedPreferences` storage,
zero permissions and no background service.

### What changed

| Area | Before | After |
| --- | --- | --- |
| First frame | `emptyView` was visible until a background thread finished; users briefly saw “No apps selected” even with a non-empty selection | Empty state is decided synchronously from the saved selection; a non-empty selection instantly renders **skeleton tiles** (or cached labels) plus a thin indeterminate progress bar |
| Home-screen load | Enumerated **every** installed launcher activity (`queryIntentActivities`) and loaded **every** icon, then filtered | Queries metadata only for the **selected** packages, off the UI thread; icons are loaded only for selected apps |
| Labels | Waited for `loadLabel()` per package | Painted immediately from a small persistent metadata cache, then validated live |
| Icons | Loaded in bulk with the app list; all-or-nothing paint | Streamed in progressively after names/tiles are visible; cached icons never reloaded; placeholder icon in the meantime |
| Icon cache | One `LruCache` per `AppRepository` instance (picker and home each had their own) | One shared `LruCache(100)` across both activities |
| Metadata cache | none | `AppMetadataCache`: tiny JSON in `SharedPreferences` (label, enabled, versionCode, lastUpdateTime), pruned to the selection, max 200 entries, invalidated on package add/remove/change/replace and on version/update-stamp mismatch |
| Refreshes | Every `onResume()` started a fresh background thread; concurrent refreshes could race and paint stale data | `RefreshScheduler`: one refresh at a time, bursts coalesced into a single follow-up, outdated results discarded, no-op resumes skipped |
| Package changes | Only picked up on the next resume | Dynamic receiver invalidates the affected cache entry/icon and refreshes immediately (only for *selected* packages) |

### Behaviour guarantees kept

- Selected-app-only restriction, selection persistence and group/folder behaviour are untouched.
- Uninstalled/disabled apps stay in the grid (greyed out, “unavailable”) and never crash or disappear silently.
- Empty selections still show the “choose apps” empty state — but only when the selection truly is empty.
- Availability is always re-checked live; the persistent cache can never make a removed/disabled app look launchable.
- Android 11–14 compatibility, `<queries>` package visibility, offline operation, zero extra permissions.

### Changed files

| File | Change |
| --- | --- |
| `app/src/main/java/com/hidden/launcher/MainActivity.kt` | Instant skeleton/cached first paint, selected-only background loading, progressive icons, refresh scheduling, package-change receiver |
| `app/src/main/java/com/hidden/launcher/AppRepository.kt` | Shared icon `LruCache`, per-package availability query with `PackageInfo` metadata, persistent-cache integration, `buildSelectedDisplays()` pure resolver |
| `app/src/main/java/com/hidden/launcher/AppInfo.kt` | `SelectedAppDisplay.isPlaceholder`, `CachedAppMeta`, `AppAvailability` models |
| `app/src/main/java/com/hidden/launcher/AppMetadataCache.kt` | **new** — persistent metadata cache + `KeyValueStore` abstraction |
| `app/src/main/java/com/hidden/launcher/RefreshPolicy.kt` | **new** — `HomeState`/`HomeUiState` (loading vs. empty), `RefreshScheduler` (coalescing, stale results, no-op resumes) |
| `app/src/main/java/com/hidden/launcher/MainAdapter.kt` | Skeleton tile binding, payload-based progressive icon updates |
| `app/src/main/java/com/hidden/launcher/FolderContentAdapter.kt` | Progressive icons + placeholders inside open folders |
| `app/src/main/java/com/hidden/launcher/AppPickerActivity.kt` | Uses the shared repository; remembers labels of newly selected apps for the instant first paint |
| `app/src/main/res/layout/activity_main.xml` | Skeleton/empty/grid visibility decided at runtime, thin loading indicator |
| `app/src/main/res/drawable/ic_app_placeholder.xml` | **new** — lightweight placeholder icon |
| `app/src/main/res/values/strings.xml` | Loading-state strings |
| `app/src/test/java/com/hidden/launcher/HomeLoadingTest.kt` | **new** — loading vs. empty, cached/placeholder paint, selected-only resolution, missing/disabled packages |
| `app/src/test/java/com/hidden/launcher/RefreshSchedulerTest.kt` | **new** — rapid/overlapping refreshes, coalescing, stale results, resume skipping |
| `app/src/test/java/com/hidden/launcher/AppMetadataCacheTest.kt` | **new** — persistence, invalidation, pruning, freshness, malformed data |
| `.github/workflows/build.yml` | Prints unit-test and lint summaries and uploads test/lint reports |

### Verifying

```bash
./gradlew testDebugUnitTest   # all unit tests (existing + new)
./gradlew lintDebug           # warnings non-blocking; CI fails only on lint errors
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

CI (GitHub Actions) runs all of the above on every push; download the APKs from
**Actions → latest run → Artifacts**:

- `hidden-launcher-debug-apk` — `app/build/outputs/apk/debug/app-debug.apk`
- `hidden-launcher-signed-release-apk` — `app/build/outputs/apk/release/app-release.apk`
- `test-and-lint-reports` — `app/build/test-results/testDebugUnitTest/`, `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/lint-results-debug.*`

## Project structure

```
app/
  src/main/
    AndroidManifest.xml
    java/com/hidden/launcher/
      MainActivity.kt          # instant loading/grid, refresh scheduling, group drag/drop & dialogs
      AppPickerActivity.kt     # search + checkboxes + save
      MainAdapter.kt / PickerAdapter.kt / FolderContentAdapter.kt
      AppRepository.kt         # selected-only PackageManager queries + shared icon cache
      AppMetadataCache.kt      # tiny persistent label/metadata cache
      RefreshPolicy.kt         # loading state + refresh scheduler (coalescing)
      PrefsHelper.kt           # SharedPreferences + groups JSON
      AppInfo.kt / AppGroup.kt # data models + MainItem sealed
    res/
      layout/ (activity_main, activity_picker, item_app, item_group, item_picker)
      values/ (strings, themes, colors)
      menu/ (menu_main, menu_picker)
      drawable/ (ic_launcher, ic_folder, ic_app_placeholder)
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
- `HomeLoadingTest`: empty selection is `EMPTY` (not loading), non-empty selection is `LOADING` (never the empty state), cached metadata paints labelled tiles instantly, unknown packages become placeholders, placeholders are replaced once metadata arrives, only selected packages are resolved/looked up, uninstalled/disabled packages stay visible but unavailable, stale cache can never fake availability, selection signature changes with selection/groups.
- `RefreshSchedulerTest`: one refresh at a time, coalescing of rapid/overlapping requests, stale result/completion discarding, resume skipped when nothing changed, resume runs after selection or package changes, settings-return behaviour, coalesced package change not lost.
- `AppMetadataCacheTest`: persistence across instances, invalidation of a single package, pruning to the selection, max-entry bounding, freshness detection on version/update-stamp changes, malformed/blank JSON, lossless encode/decode, no writes when nothing changes.

Run: `./gradlew testDebugUnitTest`

Lint: `./gradlew lintDebug` (warnings are non-blocking; CI fails on lint errors)

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
- Icons cached in memory only (shared `LruCache`); no disk icon cache — the persistent metadata cache only stores labels/flags.
- Package changes for *selected* packages are picked up by a dynamic receiver; unrelated packages are deliberately ignored to avoid refresh storms.
- Demo keystore is public — for testing only; do not publish apps to Play Store with it.

## License

MIT - See repository.
