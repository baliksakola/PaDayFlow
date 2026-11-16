# PaDayFlow Android Architecture

## Architecture goals
- Offline-first, single-user application.
- Clear separation between UI, domain rules, and persistence.
- Testable scheduling and prioritization logic.
- No mandatory cloud or generative AI dependency.

## Proposed stack
- Kotlin
- Jetpack Compose and Material 3
- AndroidX ViewModel and Lifecycle
- Room for local persistence
- Kotlin Coroutines and Flow
- WorkManager for deferrable background work
- AlarmManager only for user-visible time-critical reminders where permitted
- Jetpack Glance for home-screen widgets
- Android BiometricPrompt for biometric unlock
- Optional Google Drive integration for backup

Dependency versions should be centralized and verified against the Android Gradle Plugin compatibility matrix before implementation.

## Layering

### Presentation
Compose screens, reusable components, navigation, and ViewModels. UI observes immutable state and sends user actions to ViewModels.

### Domain
Use cases and pure Kotlin rules:
- Create/update/complete task
- Build My Day
- Calculate priority score and explain it
- Detect schedule conflicts
- Check daily capacity and protected time
- Suggest rescheduling
- Calculate habit streaks and analytics

Domain logic should be unit-testable and independent of Android UI.

### Data
Room entities, DAOs, database migrations, repository implementations, backup serialization, and optional remote integrations.

## Data ownership
Room is the source of truth. UI reads local flows. Optional backup exports a versioned snapshot. Restore must validate the snapshot and require user confirmation.

## Suggested package structure
```
com.padayflow.app
├── core
│   ├── database
│   ├── datastore
│   ├── design
│   ├── notifications
│   └── common
├── data
│   ├── repository
│   └── backup
├── domain
│   ├── model
│   ├── usecase
│   └── planner
├── feature
│   ├── dashboard
│   ├── myday
│   ├── tasks
│   ├── calendar
│   ├── habits
│   ├── notes
│   ├── assistant
│   ├── analytics
│   └── settings
└── navigation
```

## Scheduling rules
- Store instants/timestamps consistently and retain the user's local time-zone context for recurring local schedules.
- A task deadline is not the same as a scheduled work block.
- Never schedule into protected time.
- Never exceed the user's configured daily capacity without an explicit override.
- Conflicts are surfaced, not silently resolved.
- Rescheduling is a proposal until accepted.

## Notifications
Android notification delivery depends on runtime notification permission, exact-alarm access where applicable, OS background limits, and device vendor behavior. The app must explain and gracefully handle denied permissions.

## Security and privacy
- No secrets or private API keys in the repository.
- Do not log task titles, descriptions, or notes in production.
- Use Android Keystore-backed mechanisms for sensitive local secrets where appropriate.
- Backup must be opt-in and clearly explain what data leaves the device.

## Testing
- Unit tests for domain rules and recurrence.
- DAO and migration tests for Room.
- Compose UI tests for critical flows.
- Instrumentation tests for notification scheduling and restore.
- Test offline operation and permission-denied states.
