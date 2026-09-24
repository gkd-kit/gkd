# gkd-app Module Architecture

`gkd-app` adopts a "functional vertical slice + clear data and platform boundaries" approach. The dependency direction is as follows:

```text
App / MainActivity
        │
        ▼
feature ─────► domain
   │             │
   ├──────────► data
   │
   └──────────► platform

service / receiver ─► data、domain、platform
```

## Directory Responsibilities

- `app/`: Application process and Activity lifecycle entry point. Process-level unique components are directly declared as `object`, production instances are handled by their respective business packages, no unified container is set.
- `core/`: Basic state and value types shared across layers and not dependent on Android UI.
- `feature/`: Organizes Page, Route, ViewModel, and in-function components by user feature. Currently includes `log`, `snapshot`, `subscription`, `settings`.
- `domain/`: Business rules and value objects that can be independently verified, such as rule group enablement strategies. Must not depend on Compose, Activity, Service, or DAO.
- `data/`: Persistence, networking, files, and cross-data-source consistency boundaries. Single-table access directly depends on Room DAO; only introduce Repository, Manager, or Service when coordinating state, concurrency, files, or multiple data sources is needed.
- `platform/`: Unified entry point for Android platform capabilities, such as Service start/stop control; must not depend on page ViewModel.
- `ui/component`, `ui/share`, `ui/style`: UI infrastructure reused across features with no business writes.
- `service/`, `a11y/`, `notif/`, `priv/`: Android registered components and their runtime implementations. Package names are temporarily unchanged to maintain system component class name compatibility, but decoupled from UI through `platform/`.

## State and Write Boundaries

| Scenario | Read | Write |
| --- | --- | --- |
| Settings | Read-only `StateFlow` exposed by `AppStore` | `AppStore.update/replace`, and sync automation switches to the privileged process lifecycle configuration |
| Subscription | `SubscriptionRepository.snapshotFlow`, derived state of `SubscriptionState`, cold `Flow` of `Db.subsItemDao` | `SubscriptionRepository` orchestrates subscription use cases, `SubscriptionPersistence` ensures file and database compensation consistency, `SubscriptionFileStore` handles atomic file writes, single-table field updates directly use DAO |
| Rule configuration | Cold `Flow` of Room DAO | Single-table writes use DAO; cross-rule business operations use `RuleGroupConfigService` |
| Logs | Corresponding Room DAO Flow/PagingSource | Corresponding Room DAO insert, delete, and trim methods |
| Snapshot | `SnapshotRepository.snapshots()` | Atomic file and database operations of `SnapshotRepository` |
| Backup | `BackupManager` reads each data source | `BackupManager` orchestrates import, verification, and recovery |
| Service | Service itself only maintains running state | After the page requests permission, call `ServiceController`; foreground keep-alive overlay is uniformly coordinated by `KeepAliveOverlayCoordinator` |

Composables must not directly access `Db`, files, or Service lifecycle. ViewModels can directly use the single-responsibility DAO provided by `Db` and are responsible for aggregating the state needed for page consistency; do not stuff the globally unique DAO into the ViewModel constructor just for the sake of dependency injection. Cross-data-source writes and operations with business rules are handed to Repository, Manager, or Service. Service, Receiver, and accessibility runtime must not reference page ViewModels.

## Coroutines, Threads, and Concurrency

- Page operations are managed by the ViewModel's scope, defaulting to Main. Functions that perform blocking file operations switch to IO internally; heavy parsing and computation switch to Default internally; callers do not re-specify dispatchers for functions that already handle thread switching. Room suspend DAOs use the database-configured coroutine context.
- Thread scheduling does not substitute for business consistency. Simple field updates directly call DAO; rule configuration modifications based on old values are received by `RuleGroupConfigService` with target and changes, reading the latest value and updating it in the same write transaction through `SubscriptionConfigStore`. Text editing detects conflicts with the exclusion configuration at the start of editing, while preserving the new values of other fields.
- `A11yState` updates foreground information and rule selection within a private lock, and publishes through an `ActivityRule` snapshot. When involving blocking queries with privileged processes or PackageManager, `A11yState.withTopActivityLock` places the query, judgment, and update within the same critical section; do not only lock the final assignment or switch to the caller's own lock. `topActivityFlow` is just the display projection of that snapshot; `currentTopActivity` reads the published snapshot without a lock; use `A11yState.currentRule` when you need to wait for the update critical section to complete. Runtime states such as counting and delayed tasks during rule execution are still managed by the accessibility engine.
- `update/replace` of settings indicates that the in-memory update and persistence request have been accepted; `awaitPersistence` indicates that the accepted request has been persisted. Transform functions must be side-effect free; during backup recovery, the recovered state and rollback state may each be computed once.
- Backup reading and parsing phases can be cancelled; after obtaining the subscription write lock, the lock is released only after the recovery commit and necessary compensation are complete. Database import uses a single write transaction rollback; historical full-database snapshots do not overwrite other writes; subscription file saving, compensation, and normal subscription modifications are mutually exclusive. Settings rollback preserves new commands received during recovery.
- Normal operations follow the caller's lifecycle. Application-level scopes only undertake work that explicitly needs to survive across pages and provide completion or failure semantics; `NonCancellable` only covers the accepted commit and compensation ranges.
- `A11yRuntime` uniformly selects automation or accessibility service and provides root node, window, screenshot, and action entry points. Each service maintains an independent `A11yRuleEngine`, events, caches, and delayed tasks follow the service lifecycle; `A11yContext` only receives root node read callbacks and does not depend on the engine. A single query uses the selected service, and root node reads still update the corresponding engine's cache.

## New Code Placement

1. New pages are placed in the corresponding `feature/<name>` first; only pure UI used by two or more features enters `ui/component` or `ui/share`.
2. Cross-page business judgment enters `domain`, and pure function behavior tests are prioritized.
3. Single-table queries or writes directly use the corresponding DAO without adding one-to-one forwarding layers; networking, files, caching, concurrency control, and cross-table consistency operations enter `data/<name>` under clearly responsible Repository, Manager, or Service.
4. Android permissions, Service, notifications, and system API adaptations enter `platform` or remain in registered component packages, providing capabilities upward through narrow interfaces.
5. Process-level unique components with fixed dependencies are directly declared as `object`; testable implementations that require independent construction are retained in their own business packages. DAOs are directly provided by `Db` and are not re-exposed through a container.

## Compatibility Boundaries During Migration

- The `service` package name carries Manifest, accessibility service, and quick settings component identities; renaming would invalidate system authorization or tiles, so only the call boundaries are migrated, not the component class names.
- `RawSubscription` and database configuration entities remain historical shared models; rule summarization has been moved down to `domain/rule/RuleSummaryBuilder`; new logic should not write back into the application-level state container.
