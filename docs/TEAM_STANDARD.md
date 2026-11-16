# MediMind Proposal Implementation Agreement

Version 1.2 - November 16, 2026 (Asia/Manila).

The [repository proposal](PROPOSAL.md) is the primary specification. This document agrees on shared Kotlin interfaces and unresolved details; it does not supersede the proposal. The [supplementary guide](../README_TEAM_STANDARD.md) lists owners, build pins, and Git workflow. The original README.md and PROPOSAL.md remain unchanged.

## 1. What changed from our earlier standard

| Earlier choice | Proposal-aligned decision |
| --- | --- |
| Versioned schedules and effective_from/effective_until/is_current | Remove them. Use the proposal's current-schedule table. |
| Next-minute edit cutover | Remove it. Edits apply immediately to the current plan. |
| Preserved history after removing a time | Restore the reference/design confirmation: removal deletes that time and its logs. |
| Calculated missed rows in Detail history | Detail shows recorded Taken/Skipped logs; Report shows calculated missed doses. |
| Three report totals | Display Taken and Missed only, as the proposal specifies. |
| Extra Change action | Use the existing Taken/Skip actions on due rows; no separate Change action. |
| Permanent Medications/Report buttons on Today | Use the Today menu, matching the proposal navigation. |
| Team standard overrides the proposal | Proposal governs scope; this agreement supplies implementation details. |

Low-stock notifications remain because the user explicitly requested them. They are the sole feature addition described here. BootReceiver is a selected implementation of the proposal's reminder/restart behavior, not a separate screen or feature.

## 2. Shared data conventions

Package/namespace: com.example.medimind. IDs are Long; persisted IDs are positive, unsaved models use -1L. SQL names use snake_case; Kotlin properties camelCase.

Dates are yyyy-MM-dd, times HH:mm, timestamps yyyy-MM-dd HH:mm. Use strict java.time parsing with uuuu in custom formatters. Blank optional instructions/end dates become SQL NULL. Trim text before validation. Weekdays are Set<Int>, Monday=1 through Sunday=7, stored sorted as comma-separated values; do not use Calendar.DAY_OF_WEEK.

Capture one local current-minute timestamp per operation. Due means scheduled time <= that minute. An unlogged due row has internal status MISSED, displayed as Not recorded on Today; future rows are UPCOMING. This boundary is an implementation clarification, not a medical grace period.

## 3. Models

Keep one file per type under model/. These definitions are the public contract.

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
    val daysOfWeek: Set<Int>
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

data class AdherenceSummary(val taken: Int, val skipped: Int, val missed: Int)
```

AdherenceSummary.skipped is internal accounting for the stored status, as anticipated by the existing design notes; Report renders only taken/missed. Detail history returns List<IntakeLog>. For scheduled times in history, map scheduleId to the medicine's current schedules.

## 4. Schema and migrations

Database: medimind.db, version 4. Three tables match the proposal, with stock-only additions explicitly marked below. Enable foreign keys in onConfigure. SQL constraints supplement Kotlin validation.

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
    FOREIGN KEY (medication_id) REFERENCES medications(id) ON DELETE CASCADE
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
CREATE UNIQUE INDEX idx_schedules_medication_time ON schedules(medication_id, time_of_day);
CREATE INDEX idx_schedules_medication ON schedules(medication_id);
CREATE INDEX idx_logs_date ON intake_logs(dose_date);
```

Only stock_quantity, units_per_dose, low_stock_threshold, low_stock_notified, and stock_deducted are additions to the proposal's fields. The unique schedule-time index enforces the reference's no-duplicate-time validation.

Original v1 databases upgrade by adding stock fields with tracking off and stock_deducted=0. Preserve medicine/schedule/log IDs and statuses; never infer past stock consumption. Normalize legacy empty optional text to NULL.

Unreleased expanded v2/v3 databases contain schedule versions. They may convert by retaining current schedules and all logs only when no retired schedule has a recorded log. Retired unlogged rows are discarded because missed history is now inferred from the current plan. If retired logs exist, fail the upgrade transaction and preserve the old database for export rather than silently erase recorded history. This is a development-branch compatibility limit, not a destructive fallback. Do not downgrade a v4 database back to older code.

## 5. DatabaseHelper API

Object factory: DatabaseHelper.getInstance(context), using applicationContext. Activities do not close the shared helper. Methods are synchronous and run on Dispatchers.IO. Read failures propagate as SQLiteException; a successful empty list is distinct from a query failure. Writes return -1L/false on invalid input or SQLite failure. Do not swallow coroutine cancellation.

```kotlin
fun insertMedication(m: Medication, schedules: List<Schedule>): Long
fun getMedication(id: Long): Medication?
fun getAllMedications(query: String?, filter: MedFilter): List<Medication>
fun medicationExists(name: String, dose: String, excludeId: Long = -1L): Boolean
fun updateMedication(m: Medication, schedules: List<Schedule>, stockQuantityChanged: Boolean = false): Boolean
fun deleteMedication(id: Long): Boolean
fun getSchedulesForMedication(medicationId: Long): List<Schedule>
fun getSchedule(id: Long): Schedule?
fun getAllSchedulesForAlarms(): List<Schedule>
fun saveIntake(scheduleId: Long, doseDate: String, status: String): Boolean
fun getHistoryForMedication(medicationId: Long): List<IntakeLog>
fun getTodayDoses(today: String, dayOfWeek: Int): List<DoseItem>
fun getMissedDoses(fromDate: String, today: String, nowTime: String): List<DoseItem>
fun getAdherenceSummary(days: Int, today: String, nowTime: String): AdherenceSummary
fun restockMedication(id: Long, quantity: Int): Boolean
fun getLowStockMedications(): List<Medication>
fun setLowStockNotified(id: Long, notified: Boolean): Boolean
```

Insert/update/delete medicine and schedules in one transaction. Update existing schedule IDs in place, insert -1L draft times, and delete omitted schedules. Reject stale/foreign/duplicate IDs. Never delete/reinsert every time. Parameterize SQL, close cursors, and repeat duplicate validation within the write transaction.

Search trims text and uses parameterized name LIKE '%' + query + '%'; entered %/_ retain SQLite wildcard behavior. ACTIVE means is_active=1 and no end date or end_date>=today. DONE is its complement; future-starting active medicines remain Active. Sort names case-insensitively, then ID. Duplicate medicines match trimmed name AND dose ignoring case, excluding self on edit.

Schedule eligibility uses the medicine's current active flag, inclusive start/end dates, selected weekday, and time. All expected-dose/report calculations share one implementation. A 7-day window includes today and the previous six dates; 30 days includes the previous 29. Exclude future unrecorded doses. Taken/Skipped counts come from recorded logs in the date window through the cutoff, so editing dates/active state does not erase recorded actions. Missed uses the current eligible plan. Skipped never counts as Missed.

saveIntake accepts only TAKEN/SKIPPED for today. A new record requires an eligible due dose; an already-recorded dose today can be corrected even if its retained schedule was subsequently edited. Upsert by schedule/date using transactional update-then-insert compatible with API 26; preserve log ID. Repeating a status does not change time or stock. Taken/Skip can correct today's recorded action through the same two buttons. Older dates are read-only in this implementation.

Detail history returns recorded logs newest dose date/time first. It does not synthesize MISSED rows. Reports synthesize missed occurrences from current schedules with no log.

### Accepted limits of the proposal schema

Editing a retained time keeps its ID/logs but changes the time shown against those logs. Changes to weekdays/dates/active state can recalculate inferred past missed doses. Removing a time deletes its logs through the cascade; deleting the medicine deletes all its history. Explain the destructive removals in confirmations. No immutable schedule or label audit trail is claimed.

## 6. Validation, messages, and utility contract

Keep DateUtils functions now, formatDate/Time/Timestamp, parseDate/Time/Timestamp, dayOfWeek, and periodStart. Invalid parsing returns null; invalid programming inputs to dayOfWeek/periodStart may throw IllegalArgumentException. An internal test clock override is reset after tests.

Validator.validateMedication(m, schedules, isEdit, today, duplicateExists) returns List<ValidationIssue(field, messageResId)> without database access. ValidationField contains NAME, DOSE, INSTRUCTIONS, START_DATE, END_DATE, TIMES, DAYS, MEDICINE, STOCK, UNITS_PER_DOSE, LOW_STOCK_THRESHOLD.

Rules follow the existing reference/design: trimmed name 2-50 characters; required dose <=30; optional instructions <=100; valid required start, not before today on add; optional end >=start; at least one valid nonduplicate time; nonempty weekdays 1..7 shared by all submitted times; no duplicate name+dose. Use date/time pickers. Stock inputs are optional nonnegative whole Int quantities, unitsPerDose>=1 and threshold>=0. Invalid numeric text is an error, not tracking-off.

All UI text goes in strings.xml, colors in colors.xml, dimensions in dimens.xml. Retain shared resource keys. In particular:

- confirm_remove_time_body: The history for this time will be deleted.
- confirm_delete_body: This also deletes its times and intake history. This cannot be undone.
- confirm_passed_body warns that a selected time already passed today and will be missed/unrecorded unless marked.
- Remove schedule_change_note because there is no next-minute policy.
- Keep success, error, empty, discard, permission, and medical-disclaimer messages from the reference. Today and Report display the disclaimer.

Use accessible labels, 48dp touch targets, readable contrast, and system-bar/keyboard insets. Save input through rotation and confirm discard where relevant.

## 7. Screens and reminders

Keep the proposal's five Activities and navigation. Today uses a menu to Medications/Report. Form saves then returns to its previous screen, which reloads in onResume. IDs use Extras.MEDICATION_ID="medication_id", SCHEDULE_ID="schedule_id", DOSE_DATE="dose_date"; use Long IDs and explicit Intents.

Dose notifications open Today with schedule/date extras; stock notifications open Detail with medication ID. Stale notifications must not recreate data or write intake. Today handles onCreate/onNewIntent and refreshes each visible minute/midnight. Upcoming rows hide Taken/Skip; due rows show those actions and the current status. No separate Change action is needed.

ReminderScheduler exposes rescheduleAll(context): ReminderState, cancelDoseNotification(context,scheduleId,doseDate), and checkLowStock(context,medicationId=null): ReminderState. States are READY, NOTIFICATIONS_OFF, INEXACT, ERROR. Run scheduling on IO. Rebuild after medicine save/delete, recheck stock after stock/intake/refill changes, and handle error/warnings separately from successful data storage.

Use AlarmManager one-shot alarms for the next eligible current-schedule occurrence, a BroadcastReceiver, one medication_reminders channel, POST_NOTIFICATIONS on Android 13+, exact-alarm access checks on Android 12+, and inexact fallback. Do not promise an upper bound on delay. BootReceiver restores alarms after restart; also rebuild on time/timezone changes and app replacement. Receivers use bounded goAsync work, not UI permission prompts.

Use explicit immutable/update-current PendingIntents with unique data URIs for Long schedule IDs; extras alone do not define identity. Track registrations to cancel removed schedules. Recheck current eligibility/log status before notifying, suppress old-date or already-recorded doses, and arrange the next occurrence. Repeated rebuilds are serialized. Device force-stop requires reopening the app; actual delivery can be delayed by Android/device restrictions.

## 8. Explicit low-stock addition

Stock is a user-entered whole-unit estimate, separate from dose text. Defaults: tracking off (NULL), unitsPerDose=1, lowStockThreshold=5. No fractional/liquid conversions or days-remaining prediction.

- A new Taken subtracts min(quantity, unitsPerDose), records the actual deduction on the log, and commits both together. Skip/Missed do not consume stock. Corrections restore only the saved deduction. Quantity never goes negative; zero does not block Taken or dose reminders.
- Changing unitsPerDose does not rewrite earlier deductions. Explicit stock replacement/enabling/disabling establishes a new baseline and clears previous log deduction deltas without changing recorded statuses/IDs/timestamps. Additive refills retain those deltas. Reject Int overflow atomically.
- updateMedication preserves the latest database quantity unless stockQuantityChanged=true for an explicit user balance correction. Never accept a caller-supplied lowStockNotified latch.
- Deleting omitted schedule logs does not refund stock: deleting history does not mean medicine was physically returned. The whole update remains atomic.
- Notify an Active tracked medicine at/below threshold, including zero, once per low-stock episode. Quantity above threshold, tracking off, or becoming Done clears the latch and visible notification. A refill still below threshold does not repeat the alert.
- Blocked notifications retain the unposted alert and an inline Detail warning. Retry on the next permitted stock check; immediate stock alerts need no exact-alarm access. A stock alert opens existing Detail; Add refill is a Detail dialog, not a new Activity.
- Check after saves/intake/refills, on resume, and restart/settings recovery. Use stock:{medicationId} notification tags and a persisted latch/registry. Re-read before posting; notification delivery and SQLite cannot provide an absolute exactly-once transaction across a crash.

David owns stock persistence/alerts. Clarence owns Form fields and Detail display/refill controls. Joboy tests deductions/corrections, disabled permissions, threshold boundaries, refills, overflow, and duplicate suppression. Erold updates diagrams/guide. This is the user's additional feature, not text from PROPOSAL.md.

## 9. Verification and integration

Test CRUD/rollback/persistence, duplicate validation, Monday/Sunday, due/future boundaries, 7-/30-day windows, immediate edits with retained IDs, deletion cascades, stored-only Detail history, proposal-schema upgrades, current-plan missed-count recalculation, stock transactions, notifications and stale IDs. At least 12 documented test cases and genuine defect/improvement evidence remain the course requirements; do not invent results.

Dawn reviews shared setup and merges PRs. David's database/models must be available before Clarence's screens are integrated. Add no placeholder Activities or second database helper. Preserve the original README/proposal; update this supplementary agreement when shared APIs change.

Version history: 1.0 introduced an expanded design; 1.1 added low-stock notifications; **1.2 withdraws the expanded schedule/history/report choices and follows PROPOSAL.md, retaining only the user's stock addition and necessary implementation clarifications.**
