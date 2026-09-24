# Repository Instructions

- Except for scenarios where Android platform components such as `app_icon` and `service` must use XML, adding new XML files is prohibited.
- UI, icons, and other implementations that can be expressed in Kotlin must use `.kt` files; no drawable, layout, or other XML resources should be added for them.
- When adding new icon resources, icons used by pages and components within pages must be defined as Kotlin `ImageVector`; drawable XML must not be used.
- Drawable XML is only allowed for icons and their dependent resources that are referenced by Android platform XML configurations such as `AndroidManifest.xml`; when the same icon is used for both platform configuration and a page, the page must still use Kotlin `ImageVector`.
- When it is unclear whether a scenario falls under an XML exception, user confirmation must be obtained first.

## Git Commits and Pushes

- When the user explicitly requests a commit or push, only perform necessary lightweight checks and the corresponding Git operations; do not automatically expand into deep code review, temporary worktree isolation verification, full build or test, Release Gate, release checks, or other heavyweight processes. Corresponding checks are only allowed when the user explicitly requests them.

## Kotlin Visibility

- The `internal` keyword is prohibited within the `gkd-app` module; since no other module will reference `gkd-app`, visibility modifiers should be omitted from publicly visible declarations (using Kotlin's default `public`), and `private` should only be used when narrowing the scope.
- `_xxx` backing properties that directly correspond one-to-one with public properties and are only used to narrow visibility or mutability must be changed to use Explicit Backing Fields; this does not prohibit regular private fields, caches, or code-style naming that do not have such a direct correspondence. Unused Lambda parameter placeholders `_` are not subject to this restriction.

## Kotlin Static Initialization

- Lists, sets, maps, and their sorted results composed of `object`/`data object` singletons within `companion object` or `object` must be initialized using `by lazy`; directly constructing such collections during static initialization is prohibited to avoid circular initialization on JVM, JS, and Wasm.

## Kotlin Utility Declarations

- In the `util` package of `gkd-app`, new or modified cross-file utility functions and shared utility properties must be declared as members of an `object` with the same name as the file; extension functions, type declarations, and `private` implementations intended only for internal file use may remain as top-level declarations.
- `XxxExt.kt` files are only allowed to contain extension declarations; regular utility functions and shared utility properties must be moved to the corresponding `XxxUtils.kt` or a same-named `object` with a clear responsibility.
- Compose pages, components, and their private Composables are not subject to the above utility declaration rules.

## General UI Component Naming

- Custom UI components intended for reuse across pages or files are uniformly prefixed with `Gk` and use PascalCase, named `GkAbc`, including basic controls and shared business components; this rule is not restricted by the package in which the component resides.
- Component names must describe their purpose; generic prefixes like `Perf`, `Custom` are no longer used; semantically meaningful words such as `App`, `AppBar`, `Rule`, `Subs` are retained, e.g., `GkAppIcon`, `GkAppBarTextField`, `GkRuleGroupCard`.
- Single-component files share the name of the component; overloads, private implementations, and supporting declarations of the same component family may be placed in the same file, with the file name starting with `Gk` and describing the component family.
- Component-specific configuration types use names like `GkAbcDefaults`, `GkAbcColors`; icon components use `GkIcon`, shared icon sets use `GkIcons`.
- Pages, page-private Composables, Previews, regular utility functions, Modifier extensions, and independent state management types are not required to add `Gk`; the `Render()` member of a state object is not an independent component entry.

## Compose and State Boundaries

- Main interface Composables and page ViewModels uniformly obtain the current `mainVm` through `MainViewModel.requireCurrent()`, without needing to forward navigation, global popups, URL opening, and other application-level operations layer by layer; `LocalMainViewModel` is no longer used. Instances are registered by `MainActivity` after permission and Activity Result host binding, before creating the Compose interface; when the ViewModel is cleaned up, references are cleared by instance identity.
- `MainViewModel.requireCurrent()` is only for initialized main interface call chains and must not be used for Services, background tasks, or floating windows. One instance is obtained once and maintained throughout the operation; it must not be re-obtained before and after permission waiting, nor cached in static fields; this method must not create alternative instances itself.
- Route pages and their private Composables can directly obtain the page ViewModel and handle platform UI behaviors such as permissions and Activity Results. Reusable components must not obtain page ViewModels; they only receive the required state and event callbacks.
- Application-level read-only Flows are directly collected by the Composables that actually consume them; do not copy them into page `UiState` or ViewModel. Regular Flows use `collectAsStateWithLifecycle`, Paging uses dedicated APIs, and high-frequency state is placed in the smallest consuming subtree.
- Service start/stop, persistence, and other business side effects must be triggered by clear events and handed to ViewModels, Repositories, or Stores; Composables must not perform writes through state listening.
- `XxxUiState` and `XxxUiActions` are only used when truly needed for reuse, independent preview, or complex page contracts. `UiState` can only represent immutable page snapshots and must not contain Flow, Paging, or high-frequency state; when the same mapping has multiple construction paths, a private builder function is then extracted.
- Mutable state in ViewModels must be `private`, exposing only immutable state and clear business methods. Read-only `StateFlow` uses Explicit Backing Fields; `_xxxFlow`/`xxxFlow` dual properties and `.asStateFlow()` are prohibited.
- Pure UI states such as scrolling, focus, menus, animations, drag-and-drop, and multi-selection remain in Compose; reusable interaction logic can be encapsulated as `rememberXxxState`, but must not access ViewModels, databases, Stores, Services, or navigation. Multiple fields requiring atomic consistency must be provided by the same source of truth as one immutable snapshot; business state must not be passed through `CompositionLocal`.
- When a Composable needs to decide conditionally whether to output subsequent UI, early `return` is prohibited; the UI must be wrapped in the corresponding conditional block; labeled returns for events or coroutine Lambdas are not subject to this restriction.

## State and Side Effects

- Room observable queries should remain as cold `Flow`s, first aggregated within the ViewModel according to page consistency boundaries, then the final page snapshot is converted to `StateFlow<Loadable<XxxUiState>>`; `Loading` indicates that the complete initial payload has not yet been received, `Ready(emptyList())` indicates that loading is complete but the result is empty. Using empty collections to fake initial values is prohibited, as is using counters, `attachLoad`, or other bypass states to infer whether multiple queries have finished loading.
- Derived display states produced by `combine`, `map`, `stateIn`, etc., can only be used for rendering and temporary UI synchronization; driving database, file, network writes, and Service start/stop through `collect`, `onEach`, or state watch is prohibited.
- Persistence and business side effects must be triggered by clear user events, system events, or domain methods, and completed within Repository/Store according to business consistency boundaries. Allowing a single authoritative state to be synchronized to an idempotent external projection is permitted, but synchronization callbacks must not read other states to assemble writes.
- `debounce`, `conflate`, `collectLatest`, and mutexes can only control scheduling or concurrency; they cannot substitute for atomic updates across multiple state sources; states requiring consistent reads should be aggregated into a single immutable state object.

## UI Interaction and Transition Animations

- Scrollable pages must provide unified additional bottom padding at the end of the content: regular `Column` uses `GkPageBottomSpace()`, `LazyColumn` uses `gkPageBottomSpace()` to add a trailing item; when the trailing item already contains content such as an empty state, `GkPageBottomSpace()` can be used within that item without adding it again. Height is uniformly managed by `GkPageBottomSpaceDefaults`; hand-written page bottom Spacer heights are no longer used.
- Bottom padding must be located inside the scrollable content so that the last item can continue scrolling upward; it does not replace Scaffold, system navigation bar, or IME inset handling, and must not be repeatedly stacked in unified components with insets already handled by the host.

- Animations are only responsible for visual transitions; interactions respond immediately based on the current business or UI state. Temporary disabled states, waiting for animations to complete, delayed unlocks, or extra click throttling must not be added to buttons, icons, switches, or titles due to unfinished animations, icon deformation, or old content exiting; operations must also not be swallowed in click callbacks.
- Disabled interactions must correspond to clear business premises, such as no operable data, invalid input, or insufficient permissions. Brief saves, mode switches, or assumptions about rapid clicks of normal switches must not become reasons for temporarily locking controls or expanding the disabled scope; write consistency is handled in ViewModels, Repositories, or Stores.
- Repeated exiting content can be hidden from accessibility navigation, but this must not change control colors or add click waiting. Related tests should verify normal clicks and state transitions during the transition; such temporary disabling must not be solidified as correct behavior.

## Build and Test

- Regular tests only compile the `gkd` flavor; if the user has not explicitly instructed otherwise, running any `play` flavor compilation tasks is prohibited.
- Before running UI tests (including Compose UI / Instrumentation tests on real devices or emulators), the app's original automation switches and running modes must be recorded first, automation must be temporarily disabled (turn off `enableAutomator` and exit automation mode), and the settings must be confirmed effective before initializing the test to prevent app automation from interfering with test initialization.
- After tests, the original automation settings must be restored and verified; recovery must also be performed if tests fail or interrupt; scripts should use `finally` and other cleanup mechanisms to guarantee this. Only the fields temporarily modified in this session should be restored; do not overwrite other settings with the entire old configuration.

## Test Strategy

- New tests must verify observable behavior, clearly specifying the tested input, expected output, and specific regressions to prevent. Priority coverage includes pure functions, boundary conditions, exception paths, platform or version compatibility differences, and regression scenarios for fixed defects.
- Splitting production logic that should be aggregated just to add tests, expanding declaration visibility, or exposing test-specific APIs is prohibited; tests must fit reasonable production design rather than shaping production code in reverse.
- Adding tests that merely restate static declarations of production code is prohibited, including enum members, constant values, collections, sequential numbering, member relationships derived from the same registry, and constraints already guaranteed by the Kotlin type system.
- Stability tests are only allowed when a constant or identifier belongs to an external protocol, persistence format, or cross-version compatibility contract, and the compatible behavior to be protected must be explained in the test name or comment.

## Android API Investigation

- When performing source code localization, cross-version signature or availability comparisons, API missing reason analysis, or Java hidden-API access code generation involving Android framework Java/AIDL APIs, the project's `android-api-diff` skill must be used: `.agents/skills/android-api-diff/SKILL.md`.
- The `android-api-diff` CLI must be used according to that skill's routing, with default JSON output retained; implementing or simulating Android API version checks independently is prohibited.
- When installing or updating project-level skills, run `android-api-diff skill install` in the project root directory.

## Embedded UserService Exception Boundaries

- Embedded `UserService` runs in a privileged process. Binder methods that may fail, such as calling hidden APIs, must be caught with a terminal `catch (e: Throwable)` at the outermost layer of the method to handle recoverable errors, and the original type, message, and stack trace must be passed to the main process through Binder-transmittable exceptions or failure results; catching only in the main process must not be done, and `LinkageError` such as `NoSuchMethodError` must not escape and cause the privileged process to crash.
- Terminating errors that cannot be reliably recovered, such as `VirtualMachineError` and `ThreadDeath`, can be thrown as-is; do not disguise them as ordinary business failures.

## Internationalization (i18n)

- `values/strings.xml` contains the **original Chinese** strings (default language)
- `values-en/strings.xml` contains **English** translations
- Android automatically selects the correct strings based on device language
- When contributing to the original repo, only submit `values-en/strings.xml` as a PR — do NOT overwrite `values/strings.xml`

## Git Remote Configuration

- `origin` points to the main repository (push requires PAT token authentication)
- `upstream` points to the original repo (`https://github.com/theclumsypirate/gkd-english`)
- `gkd-kit` points to the main kit repo (`https://github.com/gkd-kit/gkd`)
- To fork: set `origin` to your fork URL, keep `upstream` pointing to the original repo
- To sync from the original repo without losing translations: `git fetch upstream && git merge upstream/main`
- To contribute to gkd-kit: `git push gkd-kit main` after making changes, then create a PR on `https://github.com/gkd-kit/gkd`
- To contribute translations: submit a PR with only `values-en/strings.xml` — never modify `values/strings.xml`

## GitHub Actions Secrets

Required secrets for the `Build-Release.yml` workflow (create them at Settings → Secrets and variables → Actions):

| Secret | Description |
|--------|-------------|
| `GRADLE_CACHE_ENCRYPTION_KEY` | Random key for Gradle cache encryption |
| `GKD_STORE_FILE_BASE64` | Base64-encoded gkd.jks keystore file |
| `PLAY_STORE_FILE_BASE64` | Base64-encoded play.jks keystore file |
| `GKD_STORE_PASSWORD` | Keystore password for gkd.jks |
| `GKD_KEY_ALIAS` | Key alias for gkd.jks |
| `GKD_KEY_PASSWORD` | Key password for gkd.jks |
| `PLAY_STORE_PASSWORD` | Keystore password for play.jks |
| `PLAY_KEY_ALIAS` | Key alias for play.jks |
| `PLAY_KEY_PASSWORD` | Key password for play.jks |
| `GKD_GITHUB_COOKIE` | GitHub cookie for API access |
| `GKD_API_AUTH_TOKEN` | API auth token |

To create a keystore base64 secret: `base64 -w 0 gkd.jks | pbpaste` or `base64 -w 0 gkd.jks`

## Workflow

The `Build-Release.yml` workflow:
- Triggers on `v*` tag pushes
- Builds gkd release APK and Play Store bundle
- Uploads artifacts and creates GitHub Release
- Uses `softprops/action-gh-release@v3` for release creation

The `Publish-Selector.yml` workflow:
- Triggers only on `workflow_dispatch` (manual trigger)
- Publishes the gkd-kit/selector package
