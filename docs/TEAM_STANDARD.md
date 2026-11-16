# MediMind Team Standard

**Version:** 1.1  
**Updated:** November 16, 2026 (Asia/Manila)  
**Repository:** https://github.com/Dawngend/cs0011-final-project  
**Purpose:** the shared implementation contract for all five members.

## 1. Authority and changes from the reference

Use this standard for implementation. Read [the implementation guide](../README_TEAM_STANDARD.md) for ownership, pinned build versions, setup, Git workflow, and repository status. The supplied `MediMind-Group-Reference.pdf` is the project brief. The existing README.md is preserved. Earlier planning documents, including docs/DESIGN_DETAIL.md, remain historical context; verified instructor requirements override implementation decisions here and must be recorded in a standards PR.

This standard deliberately changes or completes the reference as follows:

| Topic | Final decision |
| --- | --- |
| Shared work | Use GitHub, feature branches, and reviewed PRs. Group-chat file merging is retired. |
| Proposal wording | Treat the supplied PDF as the user's project brief. The repository also has an earlier proposal draft; instructor approval is not asserted here. |
| Model/API gaps | Use the exact types and signatures below. David owns these files; callers do not invent alternate models or queries. |
| History | Detail includes calculated missed occurrences and recorded Taken/Skipped actions. |
| Report | Show Taken, Skipped, and Missed separately. A percentage is not required. |
| Schedule edits | Preserve versions and logs. Add `effective_from`, `effective_until`, and `is_current` to schedules. |
| Schedule removal | Retire the time for future occurrences; preserve its history. Replace the PDF's destructive removal message. |
| Database version | Use schema version **3**: original v1, schedule-history v2, and stock tracking/alerts v3. |
| Added feature | Optional stock tracking and low-stock notifications; see section 11A. This feature was requested after standard 1.0. |
| Time boundary | A dose at or before the captured current minute is due. Without a log, its calculated status is MISSED; it can still be marked today. |
| UI ownership | Screens never calculate missed doses or report totals independently. |

Keep the app small: Activities call DatabaseHelper directly. Do not introduce Room, Compose, a repository layer, dependency injection frameworks, accounts, a backend, or cloud storage for this project.

## 2. Files and ownership

Source root: `app/src/main/java/com/example/medimind/`.

```text
model/
  Medication.kt               David
  Schedule.kt                 David
  IntakeLog.kt                David
  DoseItem.kt                 David
  AdherenceSummary.kt         David
  MedFilter.kt                David
  IntakeStatus.kt             David
  DoseStatus.kt               David
db/
  DatabaseHelper.kt           David
ui/
  TodayActivity.kt            David
  DoseAdapter.kt              David
  MedicationsActivity.kt      Clarence
  MedicationFormActivity.kt   Clarence
  MedicationDetailActivity.kt Clarence
  ReportActivity.kt           Clarence
  MedicationAdapter.kt        Clarence
  HistoryAdapter.kt           Clarence
reminder/
  ReminderScheduler.kt        David
  ReminderReceiver.kt         David
  BootReceiver.kt             David
util/
  Extras.kt                   David
  DateUtils.kt                David
  Validator.kt                David
```

Layouts: `activity_today.xml`, `activity_medications.xml`, `activity_medication_form.xml`, `activity_medication_detail.xml`, `activity_report.xml`, `item_dose.xml`, `item_medication.xml`, `item_history.xml`. The Activity/adapter owner owns its layout. Dawn integrates build scripts, manifest declarations, themes, and shared resources; members add resources with their feature PR.

## 3. Data representation

- Kotlin properties use camelCase; SQLite columns use snake_case.
- All IDs are Long. Unsaved models use -1L; persisted IDs are positive. Do not narrow IDs to Int.
- Dates: zero-padded `yyyy-MM-dd`. Times: `HH:mm`, 24-hour.
- Local date-times, including schedule bounds and logged_at: `yyyy-MM-dd HH:mm`.
- Optional blank instructions/end dates become SQL NULL. Trim name, dose, and instructions before validation/storage; retain internal spaces.
- Weekdays: nonempty Set<Int>, Monday=1 through Sunday=7. Store sorted values joined by commas without spaces. Use java.time.DayOfWeek.value, not Calendar.DAY_OF_WEEK.
- Use strict java.time parsing. For custom strict formatters use uuuu internally (for example uuuu-MM-dd HH:mm), avoiding year-of-era parsing problems; the stored four-digit-year values retain the reference format. Display formatting may be friendlier; storage does not change with device language.
- Follow the device's local wall clock. Capture one LocalDateTime per load/write/report and derive all cutoffs from it, rather than reading now for each row.
- MISSED means due without a recorded action, not a medical judgment. Today can label it "Not recorded" while the enum remains MISSED.

## 4. Exact model contract

All definitions below are separate files in `com.example.medimind.model`. Properties are immutable val. These are contracts to implement, not a claim that model files already exist.

```kotlin
enum class MedFilter { ALL, ACTIVE, DONE }
enum class IntakeStatus { TAKEN, SKIPPED }
enum class DoseStatus { TAKEN, SKIPPED, MISSED, UPCOMING }

data class Medication(
    val id: Long = -1L,
    val name: String,
    val dose: String,
    val instructions: String? = null,
    val startDate: String,
    val endDate: String? = null,
    val isActive: Boolean = true,
    val stockQuantity: Int? = null,
    val unitsPerDose: Int = 1,
    val lowStockThreshold: Int = 5,
    val lowStockNotified: Boolean = false
)

data class Schedule(
    val id: Long = -1L,
    val medicationId: Long = -1L,
    val timeOfDay: String,
    val daysOfWeek: Set<Int>,
    val effectiveFrom: String = "",
    val effectiveUntil: String? = null,
    val isCurrent: Boolean = true
)

data class IntakeLog(
    val id: Long = -1L,
    val scheduleId: Long,
    val doseDate: String,
    val status: IntakeStatus,
    val loggedAt: String,
    val stockDeducted: Int = 0
)

data class DoseItem(
    val scheduleId: Long,
    val medicationId: Long,
    val name: String,
    val dose: String,
    val instructions: String?,
    val timeOfDay: String,
    val doseDate: String,
    val status: DoseStatus
)

data class AdherenceSummary(
    val taken: Int,
    val skipped: Int,
    val missed: Int
)
```

An empty effectiveFrom is permitted only in an unsaved form draft. DatabaseHelper calculates bounds and isCurrent when writing; the Form cannot edit historical bounds. The Form submits current definitions, keeping IDs for existing rows and using -1L for new times.

The initial interface has no separate active/inactive switch. A new medicine is active. The Form preserves a loaded isActive; a medicine normally becomes Done through its end date. If activation is later exposed, its database behavior is already defined in section 6; add the control through a reviewed PR.

## 5. SQLite schema

Database: `medimind.db`. `DATABASE_VERSION = 3`. Enable foreign keys in onConfigure using `db.setForeignKeyConstraintsEnabled(true)`.

```sql
CREATE TABLE medications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    dose TEXT NOT NULL,
    instructions TEXT,
    start_date TEXT NOT NULL,
    end_date TEXT,
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    stock_quantity INTEGER CHECK (stock_quantity IS NULL OR stock_quantity >= 0),
    units_per_dose INTEGER NOT NULL DEFAULT 1 CHECK (units_per_dose >= 1),
    low_stock_threshold INTEGER NOT NULL DEFAULT 5 CHECK (low_stock_threshold >= 0),
    low_stock_notified INTEGER NOT NULL DEFAULT 0 CHECK (low_stock_notified IN (0, 1))
);

CREATE TABLE schedules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    medication_id INTEGER NOT NULL,
    time_of_day TEXT NOT NULL,
    days_of_week TEXT NOT NULL,
    effective_from TEXT NOT NULL,
    effective_until TEXT,
    is_current INTEGER NOT NULL DEFAULT 1 CHECK (is_current IN (0, 1)),
    FOREIGN KEY (medication_id) REFERENCES medications(id) ON DELETE CASCADE,
    CHECK (effective_until IS NULL OR effective_until >= effective_from)
);

CREATE TABLE intake_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id INTEGER NOT NULL,
    dose_date TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('TAKEN', 'SKIPPED')),
    logged_at TEXT NOT NULL,
    stock_deducted INTEGER NOT NULL DEFAULT 0 CHECK (stock_deducted >= 0),
    FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE CASCADE,
    UNIQUE (schedule_id, dose_date)
);

CREATE UNIQUE INDEX idx_schedules_current_time
    ON schedules(medication_id, time_of_day) WHERE is_current = 1;
CREATE INDEX idx_schedules_medication ON schedules(medication_id);
CREATE INDEX idx_logs_date ON intake_logs(dose_date);
```

The lower bound is inclusive and the upper bound exclusive. Equal bounds represent an empty interval. is_current means "part of the latest editable plan," not "eligible for every historical date." Retired rows remain with is_current=0.

Use parameterized SQL and close cursors with use. Keep writes transactional, including duplicate validation and schedule changes. Validate writes even if the Form already validated. Do not overwrite a row using delete-and-reinsert. For saveIntake, use a transactional update-then-insert compatible with API 26; do not rely on newer SQLite ON CONFLICT DO UPDATE syntax, and do not use INSERT OR REPLACE.

### Upgrade to version 3

Fresh installs create the complete version 3 schema. Upgrade an original version 1 database using steps 1-5 below, then add the stock columns as described afterward. An existing version 2 database skips steps 1-5 and adds only the stock columns. Never recreate a database simply to upgrade it:

1. Add the three schedule columns, with temporary/default values as needed by ALTER TABLE.
2. Set is_current=1 for original schedules. Backfill effective_from from the medicine start date at 00:00; effective_until is the day after its end date at 00:00, or NULL.
3. For an inactive medicine, cap the inferred interval at the captured upgrade minute, clamped to at least effective_from. Preserve existing logs.
4. Add indexes after checking for conflicting current times. If legacy data prevents a safe migration, fail clearly and preserve the original data; do not silently delete conflicts.
5. Verify original IDs, statuses, and log counts are unchanged. Application validation also enforces interval checks on upgraded databases where ALTER TABLE cannot add the fresh-schema CHECK.

Earlier schedule changes cannot be reconstructed. Migration preserves saved logs but cannot promise accurate inferred missed counts before upgrade. This does not affect a fresh version 3 database. No destructive fallback migration.

For both v1 and v2 upgrades, add the four medication stock columns and intake_logs.stock_deducted using the types, defaults, and CHECK rules in the schema. Existing medicines receive stock_quantity=NULL (tracking off), units_per_dose=1, low_stock_threshold=5, and low_stock_notified=0. Existing intake logs receive stock_deducted=0. Never infer stock from old logs or apply deductions retrospectively. A direct v1-to-v3 upgrade performs both changes in the same upgrade transaction.

## 6. Schedule changes and historical correctness

### Initial add

For each time, create one current version from the medicine's start date at 00:00 until the day after its end date at 00:00, or without an upper bound. An inactive medicine gets an empty interval.

Adding a medicine that starts today may create an already-due occurrence. Before saving, show the passed-time warning if a selected weekday/time applies today and is already due. There is no automatic catch-up notification; Today allows the user to mark it.

### Edit or remove

Capture one save time. Cutover is the **start of the next minute**: saving at 10:15:42 uses 10:16. Current-minute doses/actions belong to the old version.

- Metadata-only edits to name, dose text, or instructions do not replace schedules.
- Retain an unchanged definition's ID and bounds.
- For a changed time/weekday definition, retire the old version and insert a replacement with a new ID. Removing a time retires it without replacement.
- A newly added time during edit starts at/after cutover; never create earlier missed occurrences.
- Changing start date, end date, or isActive versions all current schedules. Historical eligibility uses saved schedule bounds, not the medicine's newly edited dates/active flag.
- Retirement sets is_current=0 and caps effective_until at cutover, without extending an earlier end. If effective_from is after cutover, set effective_until=effective_from, an empty interval.
- A replacement starts at max(cutover, new start date at 00:00). Its upper bound is the new end date plus one day at 00:00, or NULL. Clamp an earlier upper bound to the lower bound. An inactive replacement has equal bounds.
- Mark old rows noncurrent before inserting replacements to avoid violating the current-time index.
- Commit all changes together; every log keeps its original schedule ID. Reload schedules after saving because changed versions receive new IDs.

The Form uses getSchedulesForMedication(), returning latest current definitions. History/report generation examines every version. **Never filter historical generation by is_current, the medicine's present isActive, or its present start/end dates.** Version bounds record those decisions.

History displays the medicine's current name, dose text, and instructions. This preserves occurrence history/statuses; it is not a medical audit trail of old label text.

Example: change 08:00 daily to 09:00 at 10:15 on November 17. The old November 17 08:00 occurrence/status remains. The replacement begins at 10:16, so its first 09:00 occurrence is November 18. Do not create an extra missed November 17 09:00 dose.

Deleting the whole medicine is different: after confirmation, hard-delete it and cascade all current/retired schedules and logs. Reports then exclude it. The confirmation discloses this intentional loss.

## 7. DatabaseHelper public API

Package: `com.example.medimind.db`. Provide `DatabaseHelper.getInstance(context: Context): DatabaseHelper`, backed by applicationContext. Share the helper; Activities must not close it in onDestroy.

Instance methods use the exact model types from section 4. Return typed models, not Cursor, Map, Any, or an independently defined screen model.

```kotlin
fun insertMedication(m: Medication, schedules: List<Schedule>): Long
fun getMedication(id: Long): Medication?
fun getAllMedications(query: String?, filter: MedFilter): List<Medication>
fun medicationExists(name: String, dose: String, excludeId: Long = -1L): Boolean
fun restockMedication(id: Long, quantity: Int): Boolean
fun getLowStockMedications(): List<Medication>
fun setLowStockNotified(id: Long, notified: Boolean): Boolean
fun updateMedication(m: Medication, schedules: List<Schedule>, stockQuantityChanged: Boolean = false): Boolean
fun deleteMedication(id: Long): Boolean
fun getSchedulesForMedication(medicationId: Long): List<Schedule>
fun getSchedule(id: Long): Schedule?
fun getAllSchedulesForAlarms(): List<Schedule>
fun saveIntake(scheduleId: Long, doseDate: String, status: String): Boolean
fun getHistoryForMedication(medicationId: Long): List<DoseItem>
fun getTodayDoses(today: String, dayOfWeek: Int): List<DoseItem>
fun getMissedDoses(fromDate: String, today: String, nowTime: String): List<DoseItem>
fun getAdherenceSummary(days: Int, today: String, nowTime: String): AdherenceSummary
```

| Method | Required behavior |
| --- | --- |
| insertMedication | Normalize/validate, insert medicine and schedules together; return positive new ID, or -1L on expected validation/SQLite failure. Assign medication_id internally. Require at least one time. |
| getMedication | One medicine, or null when absent. |
| getAllMedications | Trim query; null/blank means no search. Parameterized name LIKE '%' + query + '%'; SQLite LIKE wildcard behavior applies to entered % and _. ACTIVE means is_active=1 and end_date is NULL or >=today; DONE is the exact complement. Future-starting active medicines remain ACTIVE. Sort name COLLATE NOCASE, then id. |
| medicationExists | Compare trimmed name AND dose using Kotlin equals(ignoreCase=true); exclude excludeId. Check all medicines including Done. Repeat inside the write transaction to prevent concurrent duplicate saves. |
| restockMedication | Add a positive whole quantity to tracked stock atomically; reject missing medicine, disabled tracking, nonpositive quantity, or Int overflow. Reevaluate the alert latch; see section 11A. |
| getLowStockMedications | Active, tracked medicines with stock_quantity <= low_stock_threshold, sorted by name then ID. Includes already-alerted rows so the notifier can reconcile state. |
| setLowStockNotified | notified=true is allowed only while the medicine remains Active/tracked/low, after an allowed post succeeds. notified=false clears the latch only when the episode has ended (above threshold, tracking off, or Done). Missing rows return false. |
| updateMedication | Require an existing positive medicine ID. Input is the complete desired current schedule plan. Preserve the latest database stock quantity unless stockQuantityChanged=true for an explicit user quantity correction. Persist validated units/threshold settings, and never accept a caller-supplied alert latch. Existing schedule IDs must belong to this medicine and be current. Apply section 6 atomically; false on invalid input, missing row, stale/foreign ID, or SQLite failure. Never delete/reinsert all times. |
| deleteMedication | One transaction; true only if one medicine was removed. Missing ID returns false. |
| getSchedulesForMedication | Current definitions, including ended/empty intervals; sort timeOfDay, then id. For Form/list/current schedule display. |
| getSchedule | Any version by ID, including retired; null if missing. Receiver rechecks eligibility. |
| getAllSchedulesForAlarms | Versions with at least one eligible occurrence after now. A just-retired version can remain eligible until cutover; do not reject it solely for is_current=0. |
| saveIntake | Only exact uppercase TAKEN/SKIPPED. Only an eligible due occurrence for today's local date, including an eligible retired version from earlier today. Reject future/nonexistent/ineligible doses. Upsert by (schedule_id,dose_date), preserving log ID. Repeating the same status returns true without changing logged_at; changing status updates both. Apply the stock delta and stock_deducted in the same transaction per section 11A. |
| getHistoryForMedication | All due occurrences through the captured current minute, with recorded or calculated missed status; no upcoming. Newest date/time first, then scheduleId. |
| getTodayDoses | For the current local date, eligible occurrences with final statuses. Validate dayOfWeek against today. Sort time ascending, name COLLATE NOCASE, scheduleId. |
| getMissedDoses | Inclusive fromDate..today through nowTime on today; eligible unlogged doses only. Newest date/time first, then scheduleId. |
| getAdherenceSummary | Only days=7 or 30. Counts over section 8's window must agree with the same shared occurrence generator. |

Read methods return empty lists only for successful queries with no data. Propagate SQLiteException for read failures so screens show errors instead of false empty states. Invalid date formats, impossible weekdays, and unsupported report lengths are programming errors and may throw IllegalArgumentException. Writes return documented failure values for user-data/SQLite failures. Do not catch every Throwable or swallow coroutine cancellation.

Database methods are synchronous. Activities call them on Dispatchers.IO from lifecycleScope and update views on the main thread. Receivers use bounded background work with goAsync() and always finish their PendingResult. Do not run long history/report queries on the UI thread.

## 8. One occurrence and status rule

Implement one shared internal occurrence generator in DatabaseHelper for Today/history/missed/reports. A dose is identified by **(scheduleId, doseDate)**.

For each calendar date in the range and each version:

1. Its weekday must be selected in daysOfWeek.
2. Combine doseDate and timeOfDay into a local scheduledAt.
3. Require scheduledAt >= effective_from and (effective_until IS NULL or scheduledAt < effective_until).
4. Match the unique log for that schedule/date; map TAKEN/SKIPPED to DoseStatus.
5. Without a log, scheduledAt > capturedNow means UPCOMING; scheduledAt <= capturedNow means MISSED.

Never store UPCOMING or MISSED. At the scheduled minute, Taken and Skip become available on Today. For a recorded dose, show its status and a small Change action offering Taken/Skip for today only. This corrects a tap without adding logs. Old dates are read-only.

A 7-day report covers today.minusDays(6)..today; 30 days covers today.minusDays(29)..today. Medicine end dates are inclusive; stored schedule upper bounds are exclusive. Exclude upcoming doses. Skipped is separate from missed. An empty window returns AdherenceSummary(0,0,0).

Example: November 17, 2026 at 10:00 has a 7-day window of November 11-17. Today's 09:00 dose is counted; today's 20:00 dose is excluded. One due, unlogged dose contributes taken=0, skipped=0, missed=1.

Do not calculate expected doses as "current schedules x days." Consider weekdays and effective bounds for every version. A readable Kotlin date loop with batched log lookups is appropriate for this small database; avoid one database query per generated dose.

## 9. DateUtils and Validator

Package: `com.example.medimind.util`. Object DateUtils exposes:

```kotlin
fun now(): LocalDateTime
fun formatDate(value: LocalDate): String
fun formatTime(value: LocalTime): String
fun formatTimestamp(value: LocalDateTime): String
fun parseDate(value: String): LocalDate?
fun parseTime(value: String): LocalTime?
fun parseTimestamp(value: String): LocalDateTime?
fun dayOfWeek(date: String): Int
fun periodStart(today: String, days: Int): String
```

now() returns local time truncated to minutes. Parsing returns null for invalid inputs. dayOfWeek/periodStart require valid ISO dates and reject unsupported inputs. For deterministic tests, DateUtils has internal var clockOverride: Clock? = null. now() uses that override when set, otherwise a freshly obtained Clock.systemDefaultZone(), so timezone changes are respected. Tests reset the override to null afterward. All database current-time reads go through DateUtils.now().

Object Validator exposes validateMedication. Define ValidationField and ValidationIssue in Validator.kt:

```kotlin
enum class ValidationField { NAME, DOSE, INSTRUCTIONS, START_DATE, END_DATE, TIMES, DAYS, MEDICINE, STOCK, UNITS_PER_DOSE, LOW_STOCK_THRESHOLD }
data class ValidationIssue(val field: ValidationField, val messageResId: Int)

// Member function of object Validator:
fun validateMedication(
    m: Medication,
    schedules: List<Schedule>,
    isEdit: Boolean,
    today: String,
    duplicateExists: Boolean
): List<ValidationIssue>
```

Return an empty list for valid input. Validator performs no database calls. The caller obtains duplicateExists from DatabaseHelper; the database repeats validation/duplicate checking in the transaction. Return resource IDs rather than hardcoded text. Form shows errors and focuses the first invalid field; times/days may use visible error labels.

| Field | Final rule |
| --- | --- |
| Name | Required after trim; 2-50 characters. |
| Dose | Required after trim; at most 30 characters. |
| Instructions | Optional; at most 100 characters after trim. |
| Start | Required valid date; on add, today or later. On edit, a past date is permitted because versioning prevents retroactive new occurrences. |
| End | Optional valid date, not before start. An ended medicine can be extended; new occurrences start after cutover. |
| Times | At least one valid HH:mm time; no duplicates. |
| Weekdays | At least one value; all 1..7. The Form has one common weekday selection applied to all submitted times; reject inconsistent weekday sets in a submitted plan. |
| Stock | Optional nonnegative whole quantity; blank disables tracking. unitsPerDose must be at least 1 and lowStockThreshold at least 0, both whole Int values. No quantity or refill may exceed Int.MAX_VALUE. |
| Medicine | Reject another medicine with the same trimmed name AND dose ignoring case; exclude self on edit. |

Use DatePickerDialog/TimePickerDialog and normalized strings, not free-form date/time entry. Preserve unsaved input across rotation; confirm discard when leaving with changes.

## 10. Screens, Intents, and refresh

Extras.kt contains:

```kotlin
object Extras {
    const val MEDICATION_ID = "medication_id"
    const val SCHEDULE_ID = "schedule_id"
    const val DOSE_DATE = "dose_date"
}
```

Use explicit Intents. Read medication IDs with getLongExtra(Extras.MEDICATION_ID, -1L). Missing ID means add mode only in Form. Missing/invalid Detail IDs show not-found and finish safely.

| From | To | Data / completion |
| --- | --- | --- |
| Launcher | TodayActivity | None |
| Today menu | MedicationsActivity / ReportActivity | None |
| Medication row | MedicationDetailActivity | MEDICATION_ID Long |
| Add | MedicationFormActivity | No medication ID |
| Detail Edit | MedicationFormActivity | MEDICATION_ID Long |
| Dose notification | TodayActivity | SCHEDULE_ID Long, DOSE_DATE ISO String |
| Stock notification | MedicationDetailActivity | MEDICATION_ID Long |
| Form Save | Previous screen | finish(); caller reloads in onResume |
| Detail Delete | Medications | Delete, then finish(); list reloads in onResume |

Today handles notification extras in onCreate and onNewIntent. They identify a highlight, never authorize a write. A stale notification opens current Today safely without recreating data or logging an old dose.

Today reloads in onResume, after intake writes, and when a visible clock reaches a new minute/midnight. Detail/Report reload in onResume. Report totals and missed list share one today/nowTime. Do not let an old async search/filter/report result overwrite a newer selection.

After successful insert/update/delete, the screen owner calls ReminderScheduler.rescheduleAll(). After intake writes, cancel the matching dose notification, refresh Today, and call ReminderScheduler.checkLowStock(context). Call that stock check after stock/medicine saves and refills too. Reminder failure is separate from successful data storage; do not roll back the medicine. The shared scheduler interface is defined below.

## 11. Reminders

Use AlarmManager, ReminderReceiver, BootReceiver, and one notification channel: `medication_reminders`, shared by dose and stock notifications. Schedule one-shot alarms for the next eligible occurrence of each version, not a repeating 24-hour alarm. After firing, schedule the next eligible occurrence.

Expose this object in `com.example.medimind.reminder`; put the enum in ReminderScheduler.kt:

```kotlin
enum class ReminderState { READY, NOTIFICATIONS_OFF, INEXACT, ERROR }

object ReminderScheduler {
    fun rescheduleAll(context: Context): ReminderState
    fun cancelDoseNotification(context: Context, scheduleId: Long, doseDate: String)
    fun checkLowStock(context: Context, medicationId: Long? = null): ReminderState
}
```

rescheduleAll is synchronous and runs off the UI thread. Cancel registered alarms and rebuild from DatabaseHelper. Serialize rebuilds with one process-wide lock; overlapping saves/receivers must not restore obsolete plans. Track registered alarm identities in private SharedPreferences so deleted schedules' alarms can be canceled. PendingIntent extras alone do not establish identity.

Use an explicit broadcast Intent with data URI `medimind://reminder/{scheduleId}` per schedule version. A fixed request code works with distinct data URIs. Use FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE and include SCHEDULE_ID/DOSE_DATE extras. Notification tags are `dose:{scheduleId}:{doseDate}` with a fixed numeric ID; content Intents have distinct data including both values. Do not narrow Long IDs for uniqueness.

Receiver behavior: reload schedule/medicine, verify intended date/time eligibility and that no log exists, then notify. Never notify early or for an earlier local date; a delayed alarm can notify later on its intended date. Always arrange the next occurrence even when suppressing this notification. Recheck bounds so stale/retired versions cannot generate phantom reminders. Serialize receiver-side alarm-plan mutations with full rebuilds using the same lock.

Permissions and failures:

- Android 13+: request POST_NOTIFICATIONS in an Activity. Check app/channel settings too; permission alone does not mean the channel is enabled.
- Android 12+: call canScheduleExactAlarms() before setExactAndAllowWhileIdle(). Declare SCHEDULE_EXACT_ALARM; do not assume install grants it. Offer a user-initiated link to special-access settings.
- With exact access use setExactAndAllowWhileIdle(). Otherwise use setAndAllowWhileIdle() and return INEXACT. Do not promise a maximum delay.
- With blocked notifications return NOTIFICATIONS_OFF. Keep the future alarm plan; suppress blocked notifications. Recheck permissions/settings on return to Today.
- An actual scheduling failure returns ERROR. Display priority: ERROR, NOTIFICATIONS_OFF, INEXACT, READY. Log technical diagnostics without medicine details.

BootReceiver restores alarms after BOOT_COMPLETED. Also handle TIME_SET, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED, and exact-alarm access granted. Declare RECEIVE_BOOT_COMPLETED. Keep receivers nonexported where possible; Today is the exported launcher. Never launch permission dialogs in a receiver. Force-stop requires reopening the app before normal alarm behavior resumes; explain this in the user guide.

The baseline stores local wall-clock records, not timezone history. Recalculate future alarms on time/zone changes. For DST gaps, java.time zone conversion shifts to a valid instant; for overlaps, select the earlier offset and notify at most once per schedule/date. If using a DST zone, include that case in testing. Device power restrictions can delay delivery; this is a reminder aid, not a guaranteed clinical alarm.

Official guidance: [alarms](https://developer.android.com/develop/background-work/services/alarms), [exact access defaults](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms), [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission).

## 11A. Low-stock notifications

This user-requested feature extends the original scope. Keep the existing five Activities and navigation. Add stock controls to Form/Detail, a refill dialog to Detail, and a stock-notification route to Detail.

### Inventory fields and interface

Medication.stockQuantity is NULL when tracking is off. Otherwise it is a nonnegative whole-unit estimate. unitsPerDose defaults to 1 and must be >=1; lowStockThreshold defaults to 5 and must be >=0. All quantities fit in Kotlin Int; calculate additions in Long before checking overflow. A unit is a user-understood count such as one tablet/capsule, separate from dose text such as 500 mg. Fractional units, liquid-volume conversion, stock expiry, and days-until-empty forecasting are outside this addition.

Form adds optional Remaining units, Units per dose, and Low-stock threshold inputs. Blank remaining quantity disables tracking. Detail displays remaining quantity, units per dose, threshold, the estimate note, an inline low/empty warning, and Add refill when tracking is enabled. Add refill accepts a positive quantity and calls restockMedication; it adds to supply rather than replacing the balance. Use Edit to enable tracking or correct an observed balance.

On insert, use the entered starting quantity. On update, preserve the latest database quantity unless the user explicitly edited the Remaining units field: pass stockQuantityChanged=true only for that change. A schedule/name edit must not overwrite stock from a stale loaded model after a Taken action. lowStockNotified is database-owned; ignore its submitted value.

### Intake transactions and corrections

saveIntake updates the log and stock together in one transaction:

1. A repeated identical status does not change quantity, logged_at, or stock_deducted.
2. For a new TAKEN action with tracking enabled, subtract min(current quantity, unitsPerDose). Store the actual subtraction in that log's stock_deducted. Quantity never goes below zero; insufficient/zero stock does not block recording Taken or stop dose reminders.
3. For TAKEN -> SKIPPED today, restore only that log's stock_deducted to tracked quantity, then set stock_deducted=0. SKIPPED -> TAKEN deducts once using the current unitsPerDose. Reject an overflowing write atomically instead of partially updating log/stock.
4. SKIPPED, calculated MISSED, and UPCOMING do not consume stock. An action made while tracking is off has stock_deducted=0.
5. Editing unitsPerDose alone does not change earlier deductions. A later correction uses the stored stock_deducted, not the new dose setting.
6. Explicitly replacing the quantity, enabling tracking with an observed quantity, or disabling tracking establishes a new inventory baseline. In the same transaction, set stock_deducted=0 on all existing logs for that medicine. This prevents later corrections from refunding units already accounted for in the new observed balance. Preserve log IDs, statuses, and timestamps.
7. An additive refill does not reset prior stock_deducted values. All writes reevaluate the alert latch as described below.

Do not infer historical deductions when enabling tracking or upgrading a database. Stock is an estimate based on entries/actions, not a measurement of the physical supply. Schedule versioning is independent: stock/threshold-only changes do not replace schedule versions or change adherence counts.

### Alert eligibility and duplicate prevention

A medicine qualifies when it is Active under the existing filter, tracking is enabled, and stockQuantity <= lowStockThreshold, including zero. Use lowStockNotified as a persisted latch for the current low-stock episode:

- Entering the low range leaves the latch false until an allowed notification is posted successfully, then mark it true. Marking is conditional on the medicine still being eligible.
- While the medicine remains low, repeated actions, refills that stay low, reloads, or restarts do not post again. Zero reached during the same already-alerted episode does not trigger a second notification; Detail always shows the latest balance.
- Quantity above threshold, tracking disabled, or becoming Done ends the episode: reset the latch and cancel its visible stock notification. A later new low episode can alert once.
- A new medicine or edited threshold that first qualifies is checked after save. A correction that stays in the same low episode does not reset the latch.
- Blocked notifications leave an unposted alert pending (latch false) and retain the inline warning. Retry after notification access is restored and the app next checks stock. Permission failure never rolls back a stock/intake save.

Use ReminderScheduler.checkLowStock(context, medicationId) after medicine/settings saves, intake writes, and refills; after delete, cancel that ID's stock notification through the same check. Call with null on app resume, restart/update recovery, and return from notification settings to reconcile all medicines. Run checks off the main thread and serialize them using the scheduler's lock. Re-read eligibility just before posting. Track posted stock IDs in the private notification registry so a full check can remove notifications for deleted medicines. Do not poll stock with an exact alarm: supply changes only when the user records intake or changes the balance.

Stock checks return READY, NOTIFICATIONS_OFF, or ERROR; INEXACT applies only to scheduled dose alarms. They share the medication_reminders notification channel and normal notification permission; exact-alarm access is not needed for an immediate stock notification.

Use notification tag `stock:{medicationId}` with fixed numeric ID 0. Content Intent: explicit MedicationDetailActivity, MEDICATION_ID Long, unique data URI `medimind://stock/{medicationId}`, immutable/update-current PendingIntent. Do not pass a stock alert to Today's dose-highlighting route. Show stock_low_title/body, or stock_empty_title/body when the first pending alert is at zero. Notification delivery and the SQLite latch cannot form one transaction; after an abrupt crash in that gap, reconcile with the same notification tag and use only-alert-once behavior to limit duplicate sound. No absolute exactly-once delivery guarantee is claimed.

### Ownership and acceptance

David owns schema v3, model/API changes, stock transactions/refunds, latch persistence, checks, notification routing, and recovery integration. Clarence owns the existing Form/Detail additions and calls the shared methods. Joboy adds T27-T34; Erold updates the ERD and guide; Dawn integrates and coordinates scope against the existing working deadlines. No new deadline is invented here.

## 12. Resources and messages

All text goes in strings.xml, colors in colors.xml, dimensions in dimens.xml. Text uses sp, dimensions dp. Describe actionable icons; use at least 48dp touch targets. Apply system-bar/IME insets so target SDK 36 does not obscure controls. Do not rely on color alone for status. Use fictional test data; do not put real health details in committed logs/screenshots.

Keep these shared string names and values. Additional labels can be added without renaming them.

| Resource name | English value |
| --- | --- |
| app_name | MediMind |
| medical_disclaimer | MediMind is a reminder tool. It does not give medical advice. |
| error_name_required | Enter the medicine name. |
| error_name_length | Name must be 2 to 50 characters. |
| error_dose_required | Enter the dose, for example 500 mg. |
| error_dose_length | Dose must be 30 characters or fewer. |
| error_instructions_length | Instructions must be 100 characters or fewer. |
| error_start_required | Choose a start date. |
| error_start_past | Start date cannot be in the past. |
| error_date_invalid | Choose a valid date. |
| error_end_before_start | End date cannot be before the start date. |
| error_time_required | Add at least one time. |
| error_time_duplicate | That time is already added. |
| error_time_invalid | Choose a valid time. |
| error_days_required | Choose at least one day. |
| error_days_invalid | Choose valid days of the week. |
| error_medicine_duplicate | This medicine is already in your list. |
| error_stock | Enter a remaining quantity of zero or more whole units. |
| error_units_per_dose | Enter at least one whole unit per dose. |
| error_low_stock_threshold | Enter a threshold of zero or more whole units. |
| error_refill | Enter a positive whole refill quantity within the supported limit. |
| error_refill_save | Could not update stock. Please try again. |
| stock_refilled | Stock updated. |
| stock_low_title | Medicine stock is low |
| stock_low_body | %1$s has %2$d units remaining. |
| stock_empty_title | Medicine stock is empty |
| stock_empty_body | %1$s has no recorded units remaining. |
| stock_tracking_off | Stock tracking is off. |
| stock_estimate_note | Stock is estimated from your starting quantity, refills, and recorded Taken doses. |
| medicine_saved | Medicine saved. |
| medicine_updated | Medicine updated. |
| medicine_deleted | %1$s deleted. |
| marked_taken | Marked as taken. |
| marked_skipped | Marked as skipped. |
| error_save | Could not save. Please try again. |
| error_load_medicines | Could not load your medicines. |
| error_load_doses | Could not load your doses. Please try again. |
| error_load_history | Could not load intake history. Please try again. |
| error_load_report | Could not load your report. Please try again. |
| error_save_intake | Could not update this dose. Please try again. |
| error_not_found | This medicine could not be found. |
| error_delete | Could not delete. Please try again. |
| reminders_off | Reminders are off. Turn on notifications to get reminders. |
| reminders_inexact | Exact reminder times are off. Reminders may be delayed. |
| reminders_error | Your changes were saved, but reminders could not be updated. Open Today to try again. |
| reminders_retry_error | Could not update reminders. Please try again. |
| empty_today | No doses today. Add a medicine to get started. |
| empty_medications | No medicines yet. Tap + to add one. |
| empty_search | No medicines match "%1$s". |
| empty_filter | No medicines in this list. |
| empty_history | No doses recorded yet. |
| empty_report | No doses in this period yet. |
| empty_missed | No missed doses in this period. |
| confirm_delete_title | Delete medicine? |
| confirm_delete_body | This also deletes its times and intake history. This cannot be undone. |
| confirm_discard_title | Discard changes? |
| confirm_discard_body | Your changes will not be saved. |
| confirm_remove_time_title | Remove this time? |
| confirm_remove_time_body | Future reminders for this time will stop from the next minute. Past intake history will be kept. |
| confirm_passed_title | Time already passed |
| confirm_passed_body | %1$s has already passed today. It will show as not recorded unless you mark it. |
| schedule_change_note | Schedule changes apply from the next minute. Earlier doses and history stay unchanged. |

Required action labels: Delete, Cancel, Discard, Keep editing, Remove, Save anyway, Go back, Taken, Skip, Change, Save, Edit, Add, Retry, All, Active, Done, 7 days, 30 days, Medications, Report, Add refill, Remaining units, Units per dose, Low-stock threshold. Use action_* or label_* keys; no literal UI text in Kotlin/layouts.

Display the disclaimer on Today/Report and schedule_change_note by edit controls. Use the revised removal text. The inexact warning avoids promising reminders will be only a few minutes late. Escape quotes/apostrophes appropriately when converting this table into Android XML; formatted strings use positional placeholders.

## 13. Integration sequence and checks

1. **Dawn: setup PR.** Building launcher, pinned toolchain, View Binding, shared theme, wrapper, ignores, and manifest skeleton. Record instructor approval separately; this standard does not grant it.
2. **David: contract/database PR.** Models, schema/migration, typed API, DateUtils, Validator, Extras, and meaningful database checks. Merge this before dependent screens.
3. **Feature PRs.** David/Clarence implement owned screens, layouts, adapters, resources, scheduler integration. Temporary local fixtures are fine; do not merge a second DatabaseHelper or fake implementation into main.
4. **Joboy: integrated testing.** Record actual outcomes, genuine defects, retests, improvements, screenshots. Do not invent defects to meet a required count.
5. **Erold/Dawn: documentation/release.** Diagrams show versioned schedule fields and agreed navigation. Explain permissions, edit cutover, history preservation, stock estimates/refills/threshold alerts, hard deletion, and reminder limits in the guide. Verify clean build/install/restart/persistence before submission.

Update this document in the same PR as a shared contract change and identify affected owners. Dawn resolves integration conflicts; David owns database correctness; Clarence owns dependent screen behavior. Do not silently change shared names, enums, extras, columns, return types, or resource keys.

## 14. Acceptance checklist

These are expected outcomes, not passed tests. Joboy adds steps, actual results, evidence, and pass/fail columns. Automate meaningful database transaction/interval/report checks; use UI/manual checks for dialogs and permissions.

| ID | Scenario | Expected result |
| --- | --- | --- |
| T01 | Add valid medicine with two times | One medicine, two current schedules; correct IDs and success. |
| T02 | Close/reopen | Data and recorded actions persist. |
| T03 | Blank/short/overlong inputs | Correct errors; no partial writes. |
| T04 | Duplicate name+dose with case/outer spaces; self-edit | Duplicate rejected; unchanged self-edit succeeds; same name/different dose allowed. |
| T05 | Invalid dates, duplicate/no times, no weekdays | Matching error; save blocked. |
| T06 | Monday/Sunday boundary | Monday=1, Sunday=7; only selected weekdays generate doses. |
| T07 | Before/at/after due minute | Upcoming before; due at equality; future writes rejected. |
| T08 | Taken twice, then Skipped today | One log, same ID, final Skipped; no double counts; old dates read-only. |
| T09 | Due and future unlogged doses | Due is MISSED/not recorded; future UPCOMING excluded from reports. |
| T10 | 7/30-day month/year rollover | Correct inclusive first date and cutoff; counts match generator. |
| T11 | Change 08:00 to 09:00 at 10:15 | Old 08:00 history stays; new starts 10:16; no retroactive 09:00. |
| T12 | Remove a time with history | Past recorded/missed history stays; no occurrence at/after cutover; alarm removed. |
| T13 | Edit dates; extend ended medicine | Earlier occurrences unchanged; new ones respect effective interval. |
| T14 | Metadata-only edit | Schedule IDs/bounds/logs/counts unchanged; current labels update. |
| T15 | Cancel then confirm whole-medicine deletion | Cancel changes nothing; confirm cascades every version/log and cancels alarms. |
| T16 | Search and All/Active/Done | Trimmed search, complementary filters, stable sort, correct empty state. |
| T17 | Detail and Report | Same statuses for same dates; no upcoming history; empty period handled. |
| T18 | Valid/stale notification extras | Safe Today open/highlight; no recreated data or intake write. |
| T19 | Permission denied/channel blocked | Save/load still works; warning; no crash. |
| T20 | Exact access denied then granted | Inexact fallback, warning, safe alarm rebuild on return. |
| T21 | Restart/timezone/clock/app update | Future alarms rebuilt without duplicate alarms or logs. |
| T22 | Failure during transaction | Full rollback; prior data/logs unchanged; error shown. |
| T23 | Original v1 or schedule-v2 upgrade to v3 | IDs/log counts/statuses preserved; versioned schedules and stock fields present; tracking initially off; old stock_deducted=0; no destructive fallback. |
| T24 | Rotation/back/midnight | Input retained; discard confirmation; Today date/eligibility refresh. |
| T25 | Inactive/future-starting database inputs | Inactive versions create no new doses; future active medicine has no early doses. |
| T26 | Rebuilds overlap with schedule edit | Serialized final alarm plan; receiver rejects stale/ineligible deliveries. |
| T27 | Enable tracking with quantity above, at, and below threshold | Above: no alert. At/below, including zero: one alert; tracking off: no alert. |
| T28 | Taken twice, then Skipped, then Taken with unitsPerDose=2 | First Taken deducts once; duplicate tap unchanged; Skip restores actual prior deduction; second Taken deducts once. Log count remains one. |
| T29 | Remaining stock=1 with unitsPerDose=2 | Taken succeeds; stock becomes 0; stock_deducted=1; no negative stock; correction restores 1. |
| T30 | Refill within and above low range | Additive update; still-low refill does not repeat the alert; above-threshold refill resets it; next low episode alerts once. |
| T31 | Blocked permission/channel, repeated resume, restart | Pending alert retained when blocked; one permitted post later; latch persists across restarts; no repeated alerts. |
| T32 | Change unitsPerDose or correct stock baseline | Existing logs use their saved stock_deducted; absolute baseline reset clears prior deltas; no fabricated refund; changes are atomic. |
| T33 | Stock notification tap and medicine deletion | Opens existing Detail by MEDICATION_ID; deleted medicine handled safely; deletion cancels stock notification. |
| T34 | Skip/Missed, zero stock, invalid stock/refill inputs, failed transaction | Skip/Missed do not consume; zero never blocks Taken/dose reminders; invalid/overflow rejected; log/stock roll back together. |

## 15. References and decision record

Prepared from the supplied five-page `MediMind-Group-Reference.pdf` and these repository files, reviewed November 16, 2026:

- [Original README](https://github.com/Dawngend/cs0011-final-project/blob/main/README.md): planning status and branch/PR workflow.
- [Earlier proposal](https://github.com/Dawngend/cs0011-final-project/blob/main/docs/PROPOSAL.md): original feature/table/navigation scope.
- [Project plan](https://github.com/Dawngend/cs0011-final-project/blob/main/docs/PLAN.md): submission and evaluation checklist; earlier dates/unconfirmed details are historical.
- [Architecture](https://github.com/Dawngend/cs0011-final-project/blob/main/docs/ARCHITECTURE.md): simple Activity/model/SQLiteOpenHelper structure.

Versioning, complete models/APIs, report completion, build pins, message revisions, and Git integration here are implementation decisions finalized for this project. They are not represented as text already in the PDF, existing application code, or instructor approval. This package does not implement the app or claim acceptance tests have passed.

| Version | Date | Change |
| --- | --- | --- |
| 1.0 | 2026-11-16 | Shared models/APIs, schema-v2 history, dose/report rules, build baseline, ownership, resources, reminders, Git workflow. |
| 1.1 | 2026-11-16 | Added user-requested stock tracking/low-stock alerts, stock-safe intake corrections/refills, schema v3, UI ownership, messages, and tests T27-T34. |
