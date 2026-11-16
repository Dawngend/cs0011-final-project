# Implementation and Integration Guide

Branch: feat/david-core. Scope comes from [PROPOSAL.md](PROPOSAL.md), with the user's explicitly requested low-stock addition. Shared interfaces are in [agreement 1.2](TEAM_STANDARD.md). The original README.md and proposal are preserved unchanged.

## What is implemented

- Models/enums, Extras, strict DateUtils, resource-based Validator.
- SQLiteOpenHelper CRUD, search/filters, current schedules, stored-only intake history, calculated missed doses, and 7-/30-day summary methods.
- Atomic Taken/Skip and stock accounting, corrections/refills, thresholds, and duplicate-alert prevention.
- TodayActivity/DoseAdapter with the proposal's menu navigation and Taken/Skip actions.
- Exact/inexact dose alarms, notification permission/settings handling, stock notifications, and restart/timezone/update recovery.
- Minimal pinned Android build setup, meaningful JVM/Android tests, and integration resources. Dawn should review/adopt the setup rather than replace it with an unrelated generated project.

Clarence's Medications, Form, Detail, and Report Activities remain his assignment. They are not replaced by fake screens. Today's explicit menu destinations and the stock notification's Detail destination work once those Activities and their manifest entries are integrated.

## Important changes from the expanded implementation

- Schedule has only id, medicationId, timeOfDay, daysOfWeek. No version/effective-date/current flags.
- Retained schedule IDs update in place, immediately. Removed schedules and their logs are deleted after confirmation. Do not delete/reinsert all times.
- getHistoryForMedication now returns List<IntakeLog>, not List<DoseItem>. It includes recorded Taken/Skipped actions, not calculated missed rows.
- Report displays Taken and Missed only. Summary.skipped remains internal accounting; skip is not missed.
- There is no next-minute rule, schedule_change_note, or separate Change action. Today's original Taken/Skip actions can correct recorded actions today.
- Current-plan edits can change inferred past missed counts. Earlier immutable-history promises are withdrawn because the proposal schema cannot provide them.
- Database version is 4. Its fresh schedule table has exactly the proposal's four columns; added medication/log columns support only the requested stock feature.

## Continue from this branch

Merge PR #1 first, then retarget implementation PR #2 to main for review/merge. Clarence can begin from the shared branch now:

```powershell
git fetch origin
git switch -c feat/clarence-medication-screens origin/feat/david-core
```

After this work merges, start from updated main instead. Keep one shared DatabaseHelper and model set. Add Clarence's four Activities under com.example.medimind.ui and declare them nonexported:

```xml
<activity android:name=".ui.MedicationsActivity" android:exported="false" />
<activity android:name=".ui.MedicationFormActivity" android:exported="false" />
<activity android:name=".ui.MedicationDetailActivity" android:exported="false" />
<activity android:name=".ui.ReportActivity" android:exported="false" />
```

## Shared calls

Database and scheduling calls are synchronous: call on Dispatchers.IO from lifecycleScope, then update views on the main thread. Do not close the singleton from an Activity.

```kotlin
val db = DatabaseHelper.getInstance(this)
val medicines = withContext(Dispatchers.IO) {
    db.getAllMedications(searchText.trim(), MedFilter.ACTIVE)
}
```

Form drafts use named arguments and -1L for new schedule IDs. On edit, begin with the loaded Medication.copy(...) and retain schedule IDs, including when changing their time/weekday. Submit the full desired schedule list; omitted rows are deleted with logs, so confirm removal first. Use the original destructive-removal message, not the previous history-preserved message.

Before writing, get duplicateExists and call Validator.validateMedication; show resource errors by field. DatabaseHelper repeats checks inside the transaction. Date/time pickers produce normalized values. Warn before adding a medicine whose selected time has already passed today.

For stock, distinguish blank (tracking off), zero (empty), and invalid numeric text (error). Only pass stockQuantityChanged=true when Remaining units was explicitly edited/enabled/disabled; otherwise the helper preserves any newer Taken deduction. Never set lowStockNotified from a form.

After successful save/delete, call rescheduleAll(context) and checkLowStock(context,id) on IO; a notification failure does not undo saved data. After delete, pass its deleted ID so the stock notification is canceled. Show data-save success separately from reminder errors, then finish the Form; previous screens reload in onResume.

Detail maps recorded IntakeLog.scheduleId to current schedules for displayed schedule times. It does not synthesize Missed history rows. Removing logs does not refund stock that was already consumed. Add refill calls restockMedication(id,positiveQuantity), then checkLowStock and reloads.

Report captures one DateUtils.now(), passes the same today/nowTime to the summary and missed-list calls, and shows the two proposal totals. Taken/Skipped counts use stored logs; Missed is inferred from current eligible schedules. Changing current dates/active state can change missed counts but does not delete recorded logs. No UI-side dose math or additional percentage is required.

Dose notifications route to Today using SCHEDULE_ID and DOSE_DATE. Stock notifications route to Detail using MEDICATION_ID. Do not interchange those routes. The internal alarm_time payload rejects an old queued alarm after a retained schedule's time is edited.

## Build, tests, and migration

Use JDK/JVM target 17, SDK 36, build tools 36.0.0, and the committed Gradle 8.13 wrapper. Android Studio writes the untracked local.properties SDK path. Do not commit machine paths, caches, APKs, signing keys, or real health data.

```powershell
.\gradlew.bat assembleDebug lintDebug testDebugUnitTest connectedDebugAndroidTest
```

Instrumented tests use a dedicated emulator. Reminder tests clear this app's default database/notification registry; other tests use temporary database names. Never run them against personal records. Test clock overrides are reset afterward.

Original proposal v1 data receives stock fields with tracking off and preserved IDs/logs. Earlier unreleased versioned v2/v3 data converts current schedules and logs only when retired versions have no recorded log; an archived recorded log causes a safe transactional upgrade failure, preserving the old database for explicit export/conversion. Do not clear needed records to bypass that protection.

Verified November 16, 2026: assembleDebug and lintDebug passed (zero lint errors); 5 JVM utility tests and 24 Android integration tests passed on the API 37 Pixel 8a emulator. Coverage includes exact proposal schedule columns, immediate retained-ID edits, stored-only history, deletion cascades without stock refunds, current-plan reports, original-v1 upgrades, safe v2/v3 conversion/protection, stock accounting, and stale queued alarm rejection. Notification assertions wait for Android's asynchronous posting. Installed/cold-launched the app and visually verified Today's overflow menu contains Medications and Report.

Remaining final-group checks: API 26/36 device runs, actual reboot/idle-time alarm delivery, physical-device testing, and full navigation once Clarence's screens are integrated. The local host required -Dorg.gradle.native=false for Gradle's optional Windows native integration; that workaround is not forced on teammates. No final-group UI or instructor approval is claimed.

## AI-use record

Codex assisted the implementation, proposal-alignment revision, tests, and integration guidance. The contributor must review it and be able to explain current-schedule CRUD, inferred missed counts, transaction rollback, stock accounting, and Android permissions. Record only checks actually performed; no instructor approval, physical-device test, or completed group UI is implied.
