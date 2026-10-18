# Hidden Launcher

Minimal, lightweight custom app launcher for Android 11–14 (API 30–34), designed to be triggered by [Keymapper](https://github.com/keymapperorg/Keymapper).

## Features

- **Displays ONLY selected apps** — never shows all apps by default. Empty state if nothing selected.
- **App picker** with search, icons, package names, and checkboxes.
- **Persists selection** via `SharedPreferences` across restarts and reboots (no DB).
- **Compact responsive grid** (3–5 columns depending on screen width) — tap to launch.
- **Graceful handling** of uninstalled/disabled/non-launchable apps (greyed-out, toast, no crash).
- **Discreet settings access**: overflow menu (⋮ → Settings), button in empty state, and long-press on background/grid.
- **Keymapper-ready**: stable `MAIN/LAUNCHER` component, no root/service required.
- **Offline, no permissions**, no background services, no analytics, minimal APK.

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
```

Requires JDK 17 and Android SDK (compileSdk 34).

### GitHub Actions

Workflow `.github/workflows/build.yml` runs on push/PR/manual:

- Sets up JDK 17 + Android SDK
- Gradle cache
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug` → uploads `hidden-launcher-debug-apk`
- Attempts `assembleRelease` (unsigned) as optional artifact

Download APK from **Actions → latest run → Artifacts**.

## Implementation notes

- **Storage**: `SharedPreferences` file `hidden_launcher_prefs` with key `selected_packages` (StringSet). Lightweight, no DB.
- **Selection flow**: Picker loads launchable apps via `AppRepository`, pre-checks saved set, user toggles, Save persists and returns to main. Main refreshes in `onResume()`. Uninstalled selections are preserved (greyed) until user clears them — never auto-replaced.
- **Icon caching**: `LruCache<String, Drawable>(100)` in `AppRepository` avoids redundant `loadIcon()` calls. Queries run off UI thread (background Thread) to avoid ANR.
- **No background work**: No `Service`, `BroadcastReceiver`, `WorkManager`, or polling. Battery impact is negligible.
- **Offline**: No network permissions, no ads/analytics.

## Project structure

```
app/
  src/main/
    AndroidManifest.xml
    java/com/hidden/launcher/
      MainActivity.kt          # grid + empty state
      AppPickerActivity.kt     # search + checkboxes + save
      MainAdapter.kt / PickerAdapter.kt
      AppRepository.kt         # PackageManager queries + cache
      PrefsHelper.kt           # SharedPreferences wrapper
      AppInfo.kt
    res/
      layout/ (activity_main, activity_picker, item_app, item_picker)
      values/ (strings, themes, colors)
      menu/ (menu_main, menu_picker)
      drawable/ (ic_launcher)
  src/test/  # JUnit + Robolectric unit tests
.github/workflows/build.yml
gradle/wrapper/
```

## Testing

- `PrefsHelperTest`: filtering to selected only, empty state, no fallback to all apps.
- `AppRepositoryTest`: uninstalled/disabled handling, preserves selections, launch-intent null safety.
- `SelectionPersistenceTest`: round-trip, duplicates, survive reboot.

Run: `./gradlew testDebugUnitTest`

Lint: `./gradlew lintDebug` (non-blocking)

Manual on-device checks recommended (no emulator in CI):
- Picker lists all launchable apps with icons.
- Select/deselect persists after force-stop and reboot (needs device).
- Main shows only selected, empty state otherwise.
- Uninstall a selected app → entry greyed, toast on tap, no crash.
- Launch via Keymapper trigger.

## APK size

Typical debug APK: ~2–4 MB (optimized, no unnecessary libs, no Compose, no network libs). Check Actions artifact for exact size.

## Limitations

- Only apps with a launcher activity appear in picker. System apps/services without launchers are excluded (by design).
- No widget/home-screen replacement — intentionally narrow scope.
- Icons are cached in memory only (cleared on process death); disk cache not needed for this scale.
- Refresh is manual/on-resume, not via package-change receiver (keeps app lightweight and permission-free).

## License

MIT - See repository.
