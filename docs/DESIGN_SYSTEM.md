# PaDayFlow Design System

## Direction

**Premium Hybrid:** professional minimalism with controlled, useful color.

## Visual principles
- Clear hierarchy and generous spacing.
- Color communicates meaning; it is not decoration.
- Consistent iconography and component behavior.
- Lightweight motion; respect Android reduced-motion preferences where possible.
- Light and dark themes follow the Android system by default.
- Every screen must remain usable with larger font sizes.

## Color roles

Use Material 3 color roles rather than hard-coding colors throughout screens.

Initial brand direction:
- Primary: Indigo
- Secondary: Violet
- Tertiary/accent: Cyan
- Success: Emerald
- Warning: Amber
- Error: Rose
- Light surface: warm off-white
- Dark surface: deep charcoal

Category colors are user-configurable and must maintain readable foreground contrast.

## Typography and shape
- Use Material 3 typography scale.
- Prefer readable body text over dense dashboards.
- Use medium corner radii for cards and dialogs.
- Avoid excessive elevation and nested cards.

## Navigation
Primary destinations:
1. Beranda (Dashboard)
2. My Day
3. Tugas
4. Kalender
5. Lainnya

Notes, Brain Dump, Meeting Notes, Smart Assistant, Analytics, and Settings are reachable from Lainnya or contextual actions.

## Dashboard modules
Widgets should be reorderable and individually show/hide:
- Today's agenda
- My Day progress
- Top priorities
- Upcoming deadlines
- Deferred tasks
- Inbox
- Habit summary
- Productivity snapshot

## Interaction rules
- Destructive actions require confirmation.
- Assistant suggestions show rationale and require approval before applying.
- Empty states explain the next useful action.
- Loading, error, permission-denied, and offline states must be designed.
- Do not rely on color alone to communicate task priority or status.

## Screen design sequence
1. Dashboard
2. My Day
3. Task list and task editor
4. Calendar
5. Habit tracker
6. Notes and Brain Dump
7. Smart Assistant
8. Analytics
9. Settings, backup, archive, and recycle bin
