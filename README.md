# PaDayFlow

**PaDayFlow** is an offline-first personal productivity and life-management Android app.

It is designed to help one person capture tasks quickly, decide what matters, plan realistic days, maintain habits, and review personal productivity without requiring a paid AI subscription.

## Product principles

- **Offline first:** tasks, calendar, notes, habits, planning, and local analytics work without an internet connection.
- **User controlled:** recommendations never change or delete user data without explicit confirmation.
- **Low friction:** Inbox, Quick Task, My Day, and reusable routines reduce capture and planning effort.
- **Realistic planning:** scheduling respects available time, protected personal time, task duration, and conflicts.
- **Privacy conscious:** local data is the source of truth. Cloud backup is optional and user-controlled.
- **No mandatory paid AI:** the first release uses deterministic local rules for smart recommendations.

## Planned technology

- Kotlin
- Jetpack Compose + Material 3
- Room database
- WorkManager and Android notifications
- Jetpack Glance for home-screen widgets
- Android BiometricPrompt
- Optional Google Drive backup integration
- Local rule-based Smart Assistant

## Core modules

1. Dashboard — customizable widgets and daily overview.
2. My Day — focused list of today's commitments.
3. Tasks — Inbox, categories, subtasks, priorities, deadlines, recurrence, archive, and recycle bin.
4. Calendar — day, week, month, time blocking, and recurring schedules.
5. Habits — goals, streaks, routines, and progress.
6. Notes — quick notes, Brain Dump, and meeting notes.
7. Smart Assistant — local prioritization, conflict detection, and rescheduling suggestions.
8. Analytics — task completion, time use, habits, and work-life balance.
9. Settings — reminders, security, backup, export, and customization.

## Repository structure

The Android application and product documentation will be developed incrementally. See [docs/SRS.md](docs/SRS.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), and [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md).

## Development status

**Status: foundation and specification.** Features are not considered implemented until their code, tests, and acceptance criteria are present.

## Build

The Android Gradle project is being bootstrapped. Build instructions will be finalized with the Gradle wrapper and CI workflow in the initial application scaffold.

## License

All rights reserved unless a license is added explicitly.
