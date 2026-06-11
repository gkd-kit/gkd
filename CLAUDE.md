# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

- **Build release APK (GKD channel):** `./gradlew app:assembleGkdRelease`
- **Build debug APK:** `./gradlew app:assembleGkdDebug`
- **Build Play bundle:** `./gradlew app:bundlePlayRelease`
- **Run unit tests:** `./gradlew app:testGkdDebugUnitTest`
- **Run selector module tests (JVM):** `./gradlew selector:jvmTest`
- **Lint:** `./gradlew app:lintGkdDebug`
- **Update version catalog interactively:** `./gradlew versionCatalogUpdate --interactive`
- **CI build** (GitHub Actions): pushes to any branch trigger `Build-Apk.yml`; tags trigger `Build-Release.yml`

Requires **Java 21** (Zulu distribution in CI). Set `GKD_STORE_FILE` / `GKD_STORE_PASSWORD` / `GKD_KEY_ALIAS` / `GKD_KEY_PASSWORD` in `gradle.properties` for signing.

## Project Structure

```
:app          — Main Android app (li.songe.gkd)
:selector     — KMP module (JVM + JS): CSS-like node selector engine (li.songe.selector)
:hidden_api   — Android hidden API stubs for HiddenApiBypass
```

Key versions: Kotlin 2.3.21, AGP 9.2.1, Compose 1.11.2, Room 2.8.4, Ktor 3.5.0, targetSdk 37.
Uses **version catalog** at `gradle/libs.versions.toml`.

## Architecture

### Automation Modes

The app clicks/taps UI elements through two backends:

| Mode | Implementation | Key Classes |
|------|---------------|-------------|
| **A11yMode** | Android `AccessibilityService` | `service/A11yService.kt`, `a11y/A11yRuleEngine.kt`, `a11y/A11yContext.kt` |
| **AutomationMode** | Shizuku (privileged shell) | `shizuku/AutomationService.kt`, `shizuku/ShizukuApi.kt` |

Both modes implement `A11yCommonImpl` (screenshot, query windows/nodes). Mode selection is stored in `store/SettingsStore.kt` (`automatorMode` field).

### Rule Engine (`a11y/`)

- **`A11yRuleEngine.kt`** — Core rule matching & action dispatch. Processes accessibility events, matches rules against current UI tree, throttles via per-event/per-query dispatchers.
- **`A11yContext.kt`** — Wraps `AccessibilityNodeInfo` for selector query context.
- **`A11yState.kt`** — Tracks top-activity, app-info map, and per-app enabled rules.
- **`A11yFeat.kt`** — Additional accessibility features (volume-key capture, status-bar notification, etc.).

### Subscription & Rule Data Model (`data/`)

The central data flow: **RawSubscription** (parsed JSON) → **ResolvedGroup/ResolvedRule** → **AppRule/GlobalRule** → matching & action.

- **`RawSubscription.kt`** — JSON schema for subscription files: `globalGroups[]`, `apps[].groups[].rules[]`, `categories[]`. Each rule has selectors (`matches`, `excludeMatches`, etc.), actions (`clickNode`, `longClick`, `swipe`, `back`, `none`), and position expressions.
- **`AppRule.kt` / `GlobalRule.kt`** — Resolved rules with activity/version filtering.
- **`GkdAction.kt`** — Action performers: `Click`, `LongClick`, `Back`, `Swipe`, etc. Each defines how to execute on an `AccessibilityNodeInfo` — preferred via Shizuku, fallback via `AccessibilityService.dispatchGesture()`.
- **`SubsConfig.kt` / `CategoryConfig.kt` / `AppConfig.kt`** — Per-subs/per-category/per-app user overrides stored in Room.

### Selector Engine (`selector/`)

KMP module providing a **CSS-like selector** for Android UI nodes. Used by the rule engine to find target nodes.

- Entry point: `Selector.parse(selectorString)` → `Selector`
- Query: `selector.match(node, context)` or `selector.findAll(nodes, context)`
- Supports property selectors (`[vid="menu"]`), combinators (`<`, `>`, `-`), and boolean expressions.
- Also compiles to JS for the companion web tool (https://gkd.li).

### Database (`db/`)

Room database (`AppDb.kt`) with tables: `SubsItem`, `Snapshot`, `SubsConfig`, `CategoryConfig`, `AppConfig`, `ActionLog`, `ActivityLog`, `AppVisitLog`, `A11yEventLog`. Single DAO instance via `DbSet` object.

### Settings (`store/`)

`SettingsStore` is a `@Serializable` data class persisted via custom `StorageExt.kt`. All settings are reactive via `storeFlow: MutableStateFlow<SettingsStore>`.

### Services (`service/`)

- **`A11yService.kt`** — Main accessibility service (abstract, extended by `GkdTileService`).
- **`HttpService.kt`** — Embedded Ktor HTTP server for local API access (port configurable).
- **`ScreenshotService.kt`** — Screenshot capture via MediaProjection.
- **`StatusService.kt`** — Persistent notification with runtime stats.
- **`TrackService.kt`** — Visual touch indicator overlay (floating dots for taps/swipes).

### UI (`ui/`)

Single-Activity Comppose app (`MainActivity.kt`). Navigation uses `NavDisplay` from `androidx.navigation3` with `MainViewModel` managing the back stack and page routing. Key pages: `HomePage`, `SubsAppListPage`, `AppConfigPage`, `SnapshotPage`, `AdvancedPage`, `AboutPage`.

### Shizuku Integration (`shizuku/`)

Privileged system interaction via Shizuku: `tap(x, y, duration)`, `swipe()`, `keyEvent()`, `grantSelf()`, `isAutomationRegistered()`. Also includes `AccessibilityManager.kt` for enabling/disabling accessibility services programmatically, and `WindowManager.kt` for overlay windows.

## Two Build Flavors

- **`gkd`** — Full release with `is_accessibility_tool=true` (ships via GitHub Releases / gkd.li).
- **`play`** — Play Store compliance with `is_accessibility_tool=false` (removes certain A11y features required by Google Play policy).

## Key Constants

- `LOCAL_SUBS_ID = -2L` / `LOCAL_HTTP_SUBS_ID = -1L` — IDs for local/HTTP subscriptions (not remote).
- `shizukuAppId = "moe.shizuku.privileged.api"` — Required companion app.
- `systemUiAppId = "com.android.systemui"` — Excluded from some rule matching.
