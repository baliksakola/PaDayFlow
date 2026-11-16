# PaDayFlow — Software Requirements Specification

**Version:** 1.0 draft  
**Platform:** Android phone  
**Primary language:** Indonesian  
**User model:** Single user  
**Product approach:** Offline-first; optional online services

## 1. Product goal

PaDayFlow helps the user capture, organize, prioritize, schedule, and review work and personal activities. Its primary outcome is clarity: the user should know what needs attention, when it should happen, and what to do when plans change.

## 2. Product principles

- Core workflows must work without internet access.
- Local data is the source of truth.
- The app must remain useful without generative AI or paid API access.
- Smart recommendations are advisory. Mutations require user confirmation.
- Avoid over-scheduling; respect capacity and protected personal time.
- Keep daily capture and review flows short.

## 3. User and categories

Single user. Default editable categories:
- School — Vice Principal for Facilities (Wakasek Sarpras)
- School — Administration (includes Mathematics teaching)
- Side Work
- Personal
- Family
- User-created categories

Each category supports a name, color, icon, and active/archive state.

## 4. Functional requirements

### 4.1 Onboarding and settings
- Provide a short first-run setup that can be skipped and completed later.
- Configure profile, categories, productive-time capacity, protected time, routines, reminder preferences, and theme.
- Follow Android system light/dark theme by default, with optional manual override.
- Support PIN and device biometrics where available.

### 4.2 Dashboard
- Modular dashboard with show/hide and reorder capabilities.
- Suggested widgets: today's agenda, My Day progress, top priorities, upcoming deadlines, overdue tasks, Inbox count, habit summary, and productivity snapshot.
- Provide quick actions for task, voice capture, note, and Brain Dump.
- Provide a home-screen widget in a later integration milestone.

### 4.3 Tasks
A task may contain:
- Title (required), description, category, start date, scheduled start/end, deadline, priority, importance, urgency, estimated duration, status, subtasks, notes, reminder settings, recurrence rule, created/updated timestamps, and completion metadata.
- Status values: Inbox/Not started, In progress, Deferred, Waiting, Completed, Cancelled.
- Support subtasks and task history.
- Support quick capture into Inbox before full classification.
- Support recurring tasks.
- Support archive and recycle bin with restore and permanent-delete actions.
- No task assignment or multi-user collaboration in the initial product.

### 4.4 Prioritization
- Priority levels: Low, Normal, High, Urgent.
- Provide an Eisenhower classification: important/urgent, important/not urgent, not important/urgent, neither.
- Explain recommendations using transparent factors such as deadline proximity, importance, urgency, estimated duration, and schedule availability.

### 4.5 My Day and planning
- My Day automatically includes tasks scheduled for the current day.
- Local rules may recommend additional tasks; user approves additions or changes.
- Daily planning runs at a user-configurable time and presents a reviewable plan.
- Weekly planning helps select and distribute tasks across the next seven days.
- Daily capacity is configurable per day.
- Protect user-defined sleep, meals, worship, rest, and family blocks from automatic scheduling.
- If a task is not completed, move it to Deferred/Overdue review and suggest a new time; do not silently reschedule it.

### 4.6 Calendar and scheduling
- Day, week, month, and agenda views.
- Time blocking and recurring schedules.
- Detect overlapping scheduled blocks and explain the conflict.
- Distinguish task deadline from planned work session.
- Scheduling suggestions must respect protected time and daily capacity.

### 4.7 Reminders and notifications
- Reminders can fire when the app is not open, subject to Android notification and exact-alarm permissions and OS restrictions.
- Support pre-event reminders, due reminders, overdue follow-up, and configurable snooze durations.
- Notification actions: Complete, Snooze, and Open.
- Use WorkManager for deferrable background work and exact alarms only where justified and permitted.
- Explain notification permission and battery-optimization limitations to the user.

### 4.8 Habits and routines
- Habit checklist, target frequency, streak, history, and progress statistics.
- Morning and evening routines.
- Avoid punitive streak handling; retain historical completion data when a streak resets.
- Achievement badges are optional motivational feedback, not a measure of personal worth.

### 4.9 Notes
- Quick Notes.
- Brain Dump for uncategorized thoughts.
- Convert a Brain Dump item into a task with user confirmation.
- Meeting Notes with decisions and follow-up actions.
- Link notes to tasks where useful.
- Dedicated document/photo attachment storage is out of scope for the first release.

### 4.10 Smart Assistant (no paid AI in initial release)
- Use deterministic local rules for prioritization, capacity checks, conflict detection, deadline risk, and rescheduling suggestions.
- Summarize tasks and productivity from local data.
- Never claim to understand arbitrary natural language beyond implemented parsing capabilities.
- Any create/update/delete operation initiated by an assistant requires explicit user approval.
- Optional generative AI may be considered later, but must be opt-in and disclose provider, data sent, and cost.

### 4.11 Analytics
- Completed, open, deferred, and overdue task counts.
- Completion rate with clearly defined denominator and date range.
- Time estimates versus actual time when tracked.
- Category time distribution when sufficient data exists.
- Habit consistency.
- Work/personal time comparison, based only on user-entered schedule data.
- Weekly and monthly reports.

### 4.12 Backup and export
- Manual and scheduled backup.
- Google Drive backup is optional and requires explicit Google account authorization.
- Restore flow must preview backup date and warn before overwriting local data.
- Export PDF, XLSX, and CSV where supported.
- Backup files must have a versioned schema and integrity checks.
- Do not store credentials or API secrets in source code.

### 4.13 Search
- Global search is not required in the initial scope.

## 5. Non-functional requirements

- Offline-first behavior for core task, calendar, notes, habits, planning, and analytics workflows.
- Responsive layouts for common Android phone sizes.
- Accessibility: content descriptions, adequate contrast, scalable text, and touch targets.
- Database migrations must preserve user data.
- Avoid unnecessary network permissions.
- Handle notification permission denial gracefully.
- Test date/time behavior, daylight-saving/time-zone changes, recurring schedules, and app restarts.
- Do not log task descriptions or private notes in production logs.

## 6. Initial release boundaries

Not in the initial release:
- Multi-user collaboration or assigning tasks to others.
- Complex project portfolio management.
- Dedicated attachment/document management.
- Cross-device real-time synchronization.
- Mandatory paid AI services.

## 7. Acceptance criteria

The first usable release must allow the user to:
1. Create, edit, complete, defer, archive, restore, and permanently delete a task.
2. Organize tasks by editable categories and priorities.
3. Plan a day and see scheduled tasks and deadlines separately.
4. Detect calendar conflicts.
5. Receive configured reminders when Android permits them.
6. Record habits and view streak/history.
7. Create notes and Brain Dump entries.
8. Use local smart recommendations without an AI subscription.
9. Use core features in airplane mode.
10. Export or back up data and restore it without silent data loss.
