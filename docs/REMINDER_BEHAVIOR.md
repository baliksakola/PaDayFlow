# Reminder & Recurring Task Behavior

## Scheduling
- Task reminders are scheduled as unique WorkManager jobs keyed by task ID.
- Each job stores the expected reminder timestamp. Before displaying a notification, the worker reloads the task from Room and verifies that the task is still active and the reminder timestamp has not changed.
- A stale job is discarded. If a job runs slightly before its expected time, WorkManager retries it.
- When the app starts, future reminders for active tasks are reconciled from Room and scheduled again.
- Editing, completing, or deleting a task cancels its previous unique reminder job.

## Notification actions
- **Lihat Tugas** opens the selected task editor using its database ID.
- **Selesai** changes the task status only if it is still active. For recurring tasks, creation of the next occurrence happens in the same Room transaction as completion, preventing duplicate next occurrences from repeated taps.
- **Tunda** updates the reminder timestamp in Room only while the task remains active, then replaces the scheduled WorkManager job. The configured snooze duration is 15, 30, 60, or 120 minutes.

## Recurring tasks
- Supported rules: DAILY, WEEKLY, and MONTHLY.
- Interval is clamped to 1–365.
- Monthly recurrence preserves end-of-month intent (for example, January 31 → February 28 → March 31).
- Deadline offset, scheduled start/end times, and reminder time are carried to the next occurrence.
- A next occurrence is not created when the next start date is beyond the configured recurrence end date.
- Unsupported rules and NONE do not create another occurrence.

## Verification
Unit tests cover daily and weekly intervals, monthly end-of-month behavior, recurrence end date, unsupported rules, and non-recurring tasks. CI runs :app:testDebugUnitTest before building the debug APK.

## Platform limitations
- Android notification permission must be granted for notifications to appear on Android 13 and newer.
- Exact-to-the-second delivery is not guaranteed; WorkManager is designed for reliable deferrable work and may run later under battery or background restrictions.
- Device-level restart and permission behavior still requires testing on physical Android hardware.
