# Design Detail (Draft)

**Status:** draft for the group to review. The instructor has not approved the proposal, so nothing here is built. This file adds detail to `PROPOSAL.md` and `ARCHITECTURE.md`. If this file and the proposal disagree, the proposal wins and this file gets fixed.

The design keeps the same three tables (`medications`, `schedules`, `intake_logs`), the same five screens (Today, Medications, Form, Detail, Report) and the same three extras (notification reminders, adherence report, search and filter).

## 1. Package and Class Layout

The package name is a placeholder. The group sets the real one when the Android project is created.

```
com.example.medimind
  model/
    Medication.kt        data class, one per row of medications
    Schedule.kt          data class, one per row of schedules
    IntakeLog.kt         data class, one per row of intake_logs
    DoseItem.kt          one dose on one date, used by Today and Report (not a table)
    AdherenceSummary.kt  taken, skipped, missed counts for a period (not a table)
  db/
    DatabaseHelper.kt    SQLiteOpenHelper plus all CRUD and report queries
  ui/
    TodayActivity.kt
    MedicationsActivity.kt
    MedicationFormActivity.kt
    MedicationDetailActivity.kt
    ReportActivity.kt
    DoseAdapter.kt          RecyclerView adapter for Today and the missed list
    MedicationAdapter.kt    RecyclerView adapter for the medicine list
    HistoryAdapter.kt       RecyclerView adapter for the Detail history
  reminder/
    ReminderScheduler.kt    sets and cancels alarms
    ReminderReceiver.kt     BroadcastReceiver that shows the notification
    BootReceiver.kt         BroadcastReceiver that sets alarms again after a restart
  util/
    Extras.kt               constants for Intent extra names
    DateUtils.kt            date and time text helpers
    Validator.kt            validation rules from section 5
```

Notes on the layout:

- `ARCHITECTURE.md` says there is no extra layer between screens and the database. So the CRUD methods live in `DatabaseHelper`. If the class gets too long, the group may split it into small files by table. That is a group choice and not needed at the start.
- `DoseItem` and `AdherenceSummary` are plain data classes for query results. They hold no logic.
- `Validator` has one small function per rule so each can be tested alone.

## 2. DatabaseHelper

`DatabaseHelper(context)` extends `SQLiteOpenHelper` with `DATABASE_NAME = "medimind.db"` and `DATABASE_VERSION = 1`.

First, `onConfigure` turns foreign keys on with `db.setForeignKeyConstraintsEnabled(true)`. SQLite ignores foreign keys unless this is on, and the delete cascade depends on it. Then `onCreate` runs the three statements below. Finally, `onUpgrade` is empty at version 1. If the schema changes during development, the group raises the version, drops the tables and calls `onCreate`. Test data is lost on that change, which is fine before release.

### CREATE TABLE statements

```sql
CREATE TABLE medications (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL,
    dose          TEXT    NOT NULL,
    instructions  TEXT,
    start_date    TEXT    NOT NULL,
    end_date      TEXT,
    is_active     INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1))
);

CREATE TABLE schedules (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    medication_id INTEGER NOT NULL,
    time_of_day   TEXT    NOT NULL,
    days_of_week  TEXT    NOT NULL,
    FOREIGN KEY (medication_id) REFERENCES medications(id) ON DELETE CASCADE
);

CREATE TABLE intake_logs (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id   INTEGER NOT NULL,
    dose_date     TEXT    NOT NULL,
    status        TEXT    NOT NULL CHECK (status IN ('TAKEN', 'SKIPPED')),
    logged_at     TEXT    NOT NULL,
    FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE CASCADE,
    UNIQUE (schedule_id, dose_date)
);

CREATE INDEX idx_schedules_medication ON schedules(medication_id);
CREATE INDEX idx_logs_date ON intake_logs(dose_date);
```

### Data format rules

| Field | Format | Example |
| --- | --- | --- |
| `start_date`, `end_date`, `dose_date` | `yyyy-MM-dd` (sorts correctly as text) | `2026-11-04` |
| `time_of_day` | `HH:mm`, 24 hour | `08:00` |
| `logged_at` | `yyyy-MM-dd HH:mm` | `2026-11-04 08:12` |
| `days_of_week` | numbers joined by commas, 1 is Monday and 7 is Sunday (same as `java.time.DayOfWeek.getValue()`) | `1,2,3,4,5` |

Do not use `Calendar.DAY_OF_WEEK` for this. It counts Sunday as 1 and would shift every day by one.

## 3. Database Methods (DAO List)

All methods are in `DatabaseHelper`. Insert, update and delete of a medicine run in one transaction together with its schedules, so the data cannot end up half saved.

### Medications and schedules

| Method | Does |
| --- | --- |
| `fun insertMedication(m: Medication, schedules: List<Schedule>): Long` | Inserts the medicine and its schedules in one transaction. Returns the new medicine id, or -1 on failure. |
| `fun getMedication(id: Long): Medication?` | One medicine by id, or null. |
| `fun getAllMedications(query: String?, filter: MedFilter): List<Medication>` | List for the Medications screen. `query` matches `name` with `LIKE`. `MedFilter` is `ALL`, `ACTIVE` or `DONE` (see section 3.1). |
| `fun updateMedication(m: Medication, schedules: List<Schedule>): Boolean` | Updates the medicine, then compares the schedule list by id: updates the ones that exist, inserts the ones with no id, deletes the ones that were removed. |
| `fun deleteMedication(id: Long): Boolean` | Deletes the medicine. Schedules and logs go with it through the cascade. |
| `fun getSchedulesForMedication(medicationId: Long): List<Schedule>` | Schedules of one medicine, ordered by time. |
| `fun getSchedule(id: Long): Schedule?` | One schedule by id, used by the receiver. |
| `fun getAllSchedulesForAlarms(): List<Schedule>` | Schedules of active medicines that have not ended. Used to set all alarms again. |

`updateMedication` must not delete all schedules and insert them again. That would erase the intake history through the cascade. It only deletes a schedule the user removed. The Form warns the user before that (message M-C3).

### Intake logs and screens

| Method | Does |
| --- | --- |
| `fun saveIntake(scheduleId: Long, doseDate: String, status: String): Boolean` | Inserts a log, or replaces the log for the same schedule and date (`insertWithOnConflict` with `CONFLICT_REPLACE`). This lets the user change Skip to Taken. |
| `fun getHistoryForMedication(medicationId: Long): List<IntakeLog>` | Logs of one medicine, newest first, for the Detail screen. |
| `fun getTodayDoses(today: String, dayOfWeek: Int): List<DoseItem>` | Today's doses with the log status (section 3.2). |
| `fun getMissedDoses(fromDate: String, today: String, nowTime: String): List<DoseItem>` | Past doses with no log (section 3.3). |
| `fun getAdherenceSummary(days: Int, today: String, nowTime: String): AdherenceSummary` | Counts of taken, skipped and missed for the last 7 or 30 days. |

`DoseItem` fields: `scheduleId`, `medicationId`, `name`, `dose`, `instructions`, `timeOfDay`, `doseDate`, `status`. The `status` text is `TAKEN`, `SKIPPED`, `MISSED` or `UPCOMING`. The first two come from the log. The other two are worked out in Kotlin by comparing the dose time with the current time.

### 3.1 Search and filter

Search is `name LIKE '%' || ? || '%'` with the trimmed text. The filter is added to the same query:

```sql
-- ACTIVE
WHERE is_active = 1 AND (end_date IS NULL OR end_date >= ?)   -- ? is today
-- DONE
WHERE is_active = 0 OR (end_date IS NOT NULL AND end_date < ?)
```

### 3.2 Today's doses

```sql
SELECT s.id AS schedule_id, m.id AS medication_id, m.name, m.dose,
       m.instructions, s.time_of_day, l.status
FROM schedules s
JOIN medications m ON m.id = s.medication_id
LEFT JOIN intake_logs l ON l.schedule_id = s.id AND l.dose_date = ?
WHERE m.is_active = 1
  AND ? >= m.start_date
  AND (m.end_date IS NULL OR ? <= m.end_date)
  AND (',' || s.days_of_week || ',') LIKE '%,' || ? || ',%'
ORDER BY s.time_of_day;
-- arguments in order: today, today, today, dayOfWeek
```

The comma padding on `days_of_week` makes the weekday match exact.

### 3.3 Missed dose logic

The rule from the proposal: a dose is not stored until the user acts on it. A dose whose time has passed and has no log is missed.

In plain words, the query does four things. First, it builds a list of every date from the start of the period up to today. Then, for each date it keeps the schedules that run on that weekday and fall between the medicine's start and end dates. After that, it throws away every dose that has a log (taken or skipped). Finally, it keeps only doses in the past: an earlier date, or today with a time earlier than now.

```sql
WITH RECURSIVE days(d) AS (
    SELECT ?                                   -- fromDate
    UNION ALL
    SELECT date(d, '+1 day') FROM days WHERE d < ?   -- today
)
SELECT m.id AS medication_id, m.name, m.dose,
       s.id AS schedule_id, s.time_of_day, days.d AS dose_date
FROM days
JOIN schedules s
  ON (',' || s.days_of_week || ',') LIKE
     '%,' || CASE strftime('%w', days.d) WHEN '0' THEN '7'
                                         ELSE strftime('%w', days.d) END || ',%'
JOIN medications m ON m.id = s.medication_id
LEFT JOIN intake_logs l ON l.schedule_id = s.id AND l.dose_date = days.d
WHERE m.is_active = 1
  AND days.d >= m.start_date
  AND (m.end_date IS NULL OR days.d <= m.end_date)
  AND l.id IS NULL
  AND (days.d < ? OR (days.d = ? AND s.time_of_day < ?))   -- today, today, nowTime
ORDER BY days.d DESC, s.time_of_day;
-- arguments in order: fromDate, today, today, today, nowTime
```

`strftime('%w')` gives 0 for Sunday. The `CASE` turns that into 7 so it matches our Monday-is-1 format.

The adherence counts use the same pieces:

- **Taken:** `SELECT COUNT(*) FROM intake_logs WHERE status = 'TAKEN' AND dose_date BETWEEN ? AND ?`
- **Skipped:** the same with `'SKIPPED'`.
- **Missed:** the number of rows from the missed query above.
- **Adherence percent:** taken divided by (taken + skipped + missed), shown as a whole number. When the total is 0, the Report shows the empty state and no percent.

Known limits of this logic (group to decide):

1. A medicine saved today with a time that already passed today shows that dose as missed. The Form warns about this (message M-C4) so the user is not surprised. A cleaner fix needs a `created_at` field, which is not in the proposal tables. See section 9.
2. A medicine set to inactive stops counting from the moment it is set inactive, and its earlier missed doses leave the report too. This keeps the query simple.
3. The report only counts logs for schedules that still exist. Deleting a medicine deletes its history.

## 4. Intent Extras per Screen

All screens use explicit Intents (`Intent(this, TargetActivity::class.java)`). The extra names are constants in `Extras.kt` so a typo cannot silently break a screen.

| From | To | Extras | Notes |
| --- | --- | --- | --- |
| Today (menu) | Medications | none | |
| Today (menu) | Report | none | The 7 or 30 day choice is on the Report screen. |
| Medications (tap row) | Detail | `medication_id` (Long) | |
| Medications (+ button) | Form | none | No `medication_id` means add mode. |
| Detail (Edit) | Form | `medication_id` (Long) | Form loads the record and fills the fields. |
| Notification (tap) | Today | `schedule_id` (Long), `dose_date` (String, `yyyy-MM-dd`) | Today scrolls to and highlights that dose. |
| Form (Save) | back to Medications or Detail | none | The previous screen reloads in `onResume`. |
| Detail (Delete) | back to Medications | none | Detail calls `finish()` after the delete. Medications reloads in `onResume`. |

Rules for reading extras:

- Read with `getLongExtra("medication_id", -1L)`. A value of -1 means add mode in the Form.
- If Detail gets an id that is not in the database, it shows message M-E3 and closes.
- If Today gets `schedule_id` but no matching dose, it just opens normally.

## 5. Validation Rules

Validation runs in the Form when the user taps Save, before any database call. Errors show on the field with `setError`. Text is trimmed first.

| Field | Rule | Message text |
| --- | --- | --- |
| Name | Required | Enter the medicine name. |
| Name | 2 to 50 characters | Name must be 2 to 50 characters. |
| Dose | Required | Enter the dose, for example 500 mg. |
| Dose | At most 30 characters | Dose must be 30 characters or fewer. |
| Instructions | Optional, at most 100 characters | Instructions must be 100 characters or fewer. |
| Start date | Required | Choose a start date. |
| Start date | On add, not earlier than today | Start date cannot be in the past. |
| End date | Optional. If set, not before the start date | End date cannot be before the start date. |
| Times | At least one | Add at least one time. |
| Times | No two the same | That time is already added. |
| Days of week | At least one chosen | Choose at least one day. |
| Medicine | Same name and dose as another medicine (ignore upper and lower case; on edit, ignore itself) | This medicine is already in your list. |
| Search box | Trimmed. Empty means no search | none |

Notes:

- Dates and times come from a `DatePickerDialog` and a `TimePickerDialog`, so a badly typed format cannot happen. The checks still run in case of an empty value.
- The Taken and Skip buttons are hidden for doses that are still upcoming, so the user cannot log a dose before its time.
- A late Taken or Skip is allowed for a missed dose. It replaces the missed state.

## 6. Message Catalog

All text goes in `strings.xml`. The ids in the first column are for this document only. Messages with `%s` get the medicine name. Success and error messages use a `Snackbar` or `Toast`. Confirmations use an `AlertDialog`.

### Success

| Id | Text |
| --- | --- |
| M-S1 | Medicine saved. |
| M-S2 | Medicine updated. |
| M-S3 | %s deleted. |
| M-S4 | Marked as taken. |
| M-S5 | Marked as skipped. |

### Error and warning

| Id | Text |
| --- | --- |
| M-E1 | Could not save. Please try again. |
| M-E2 | Could not load your medicines. |
| M-E3 | This medicine could not be found. |
| M-E4 | Reminders are off. Turn on notifications to get reminders. |
| M-E5 | Exact reminder times are off. Reminders may come a few minutes late. |
| M-E6 | Could not delete. Please try again. |

### Empty state

| Id | Screen | Text |
| --- | --- | --- |
| M-N1 | Today | No doses today. Add a medicine to get started. |
| M-N2 | Medications | No medicines yet. Tap + to add one. |
| M-N3 | Medications, search | No medicines match "%s". |
| M-N4 | Medications, filter | No medicines in this list. |
| M-N5 | Detail history | No doses recorded yet. |
| M-N6 | Report | No doses in this period yet. |
| M-N7 | Report, missed list | No missed doses in this period. |

### Confirmation

| Id | Title | Text | Buttons |
| --- | --- | --- | --- |
| M-C1 | Delete medicine? | This also deletes its times and intake history. This cannot be undone. | Delete, Cancel |
| M-C2 | Discard changes? | Your changes will not be saved. | Discard, Keep editing |
| M-C3 | Remove this time? | The history for this time will be deleted. | Remove, Cancel |
| M-C4 | Time already passed | %s has already passed today. It will show as missed unless you mark it. | Save anyway, Go back |

### Fixed text

A line on the Today and Report screens: "MediMind is a reminder tool. It does not give medical advice."

## 7. Notification and Alarm Design

### 7.1 Overview

Doses are not stored in advance. Each schedule has one alarm for its next dose time. First, `ReminderScheduler` works out the next time for a schedule and sets an alarm. Then, when the alarm fires, `ReminderReceiver` shows the notification. Finally, the receiver sets the alarm for the next occurrence of that same schedule.

Doing it this way (one alarm at a time per schedule) avoids repeating alarms, which Android does not allow to be exact.

### 7.2 Alarm details

- Use `AlarmManager`. One alarm per schedule.
- The alarm `PendingIntent` is an explicit Intent to `ReminderReceiver` with the extra `schedule_id`. The request code is the schedule id, so each schedule has its own alarm and cancel finds the right one.
- Flags are `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`.
- Next time: starting from now, look at today and the next 7 days. Take the first date whose weekday is in `days_of_week`, that is between the start and end dates, and whose time is after now. If none is found (the medicine has ended), no alarm is set.
- When a medicine is saved, updated or deleted, the scheduler cancels the old alarms of its schedules and sets the new ones.

### 7.3 Notification channel

- One channel, created before the first notification (it is safe to create it again).
- Id `dose_reminders`. Name and description come from `strings.xml`. Importance is high so it makes a sound.
- Notification content: title "Time for %s", text "%s, %s" (dose and instructions). Category is reminder. It cancels when tapped.
- The tap `PendingIntent` is `getActivity` with an explicit Intent to `TodayActivity` and the extras `schedule_id` and `dose_date`. The request code is the schedule id and the flags are `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`. Starting the Activity straight from the notification is required on recent Android versions.
- Taken and Skip happen on the Today screen. Action buttons inside the notification are not in scope.

### 7.4 Permissions and Android versions

| Item | Android version | What the app does |
| --- | --- | --- |
| Notification channel | 8.0 and above | Create the channel. |
| `POST_NOTIFICATIONS` runtime permission | 13 and above | Ask when the user saves the first medicine, after a short explanation. If denied, everything still works except the reminders. |
| `SCHEDULE_EXACT_ALARM` | 12 and above | Needed for exact times. It may be off by default on newer versions, so the app checks `canScheduleExactAlarms()`. |

Notification permission handling:

1. First, before asking, a dialog says "MediMind needs permission to show reminders at your dose times."
2. Then the app asks for `POST_NOTIFICATIONS`.
3. If the user denies, a banner on Today shows message M-E4 with a button that opens the app's notification settings.
4. The banner is checked in `onResume` with `NotificationManagerCompat.areNotificationsEnabled()`, so it disappears once the user turns notifications on.

Exact alarm handling:

1. If `canScheduleExactAlarms()` is true (or the phone is older than Android 12), use `setExactAndAllowWhileIdle`.
2. If it is false, use `setAndAllowWhileIdle` instead. The reminder can be a few minutes late.
3. Then show message M-E5 once, with a button that opens `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
4. When the user changes this permission, the app sets all alarms again (see 7.5).

The manifest declares `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED`. The group should check on a real phone or emulator which of these behave as described, because the rules change by Android version.

### 7.5 After a restart

Alarms are cleared when the phone restarts. Our choice is to handle this, because a reminder app that goes silent after a restart fails its main job.

- `BootReceiver` listens for `BOOT_COMPLETED`. It reads `getAllSchedulesForAlarms()` and sets an alarm for each one.
- As a backup, the app also sets all alarms again every time `TodayActivity` opens.
- If time runs short, the fallback is a documented limit: "After a restart, open MediMind once to restore reminders." Test case TC-18 checks whichever one the group keeps.

Other limits to write in the user guide: reminders may be late if the phone's battery saver is strict, and a change of the phone clock or time zone is only picked up the next time the app opens.

## 8. Screen Behavior Summary

| Screen | Loads | Main actions |
| --- | --- | --- |
| Today | `getTodayDoses` in `onResume` | Taken, Skip, menu to Medications and Report |
| Medications | `getAllMedications` in `onResume` | Search, filter, tap row, add |
| Form | `getMedication` and `getSchedulesForMedication` in edit mode | Save, Cancel |
| Detail | `getMedication`, `getSchedulesForMedication`, `getHistoryForMedication` | Edit, Delete (with confirmation) |
| Report | `getAdherenceSummary` and `getMissedDoses` | Switch between 7 and 30 days |

## 9. Open Questions for the Group

1. Package name, `minSdk` and `targetSdk` are set when the project is created. This design assumes Android 8.0 (API 26) or higher for notification channels and `java.time`.
2. Should the Report show a Skipped count? The proposal says two numbers (taken and missed). This draft adds a skipped count so the percent adds up. The group can drop it.
3. Do we add a `created_at` field to `medications` to avoid the "already passed today" case? It would change the proposal table, so the instructor should see it.
4. Reboot handling: keep `BootReceiver` or document the limit?
5. Does the course want a separate DAO or repository class? `ARCHITECTURE.md` says no, so this draft keeps methods in `DatabaseHelper`. Confirm against the lab style.
