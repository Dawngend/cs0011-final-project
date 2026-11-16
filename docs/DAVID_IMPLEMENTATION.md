# David's implementation and integration guide

Branch: `feat/david-core`. This branch is based on the team standard in PR #1. Merge the standards PR before merging this implementation into main. The original repository README is unchanged.

## What is implemented

- All agreed model classes and enums, Extras, strict DateUtils, resource-based Validator.
- SQLiteOpenHelper DatabaseHelper with the public methods in TEAM_STANDARD.md: CRUD, search/filters, preserved schedule versions, shared dose generation, history, missed lists, 7-/30-day reports, and version 1/2-to-3 migrations.
- Transactional inventory updates/refills, idempotent Taken/Skip, actual-deduction refunds, observed-balance resets, overflow checks, and persisted low-stock alert state.
- TodayActivity, DoseAdapter, layouts, resources, notification permission/settings handling, minute/midnight refresh, and notification row highlighting.
- One-shot exact/inexact dose alarms, low-stock notifications, unique Long-ID PendingIntents, and restart/time/timezone/update recovery.
- A minimal Android build scaffold so this contribution can compile independently. Dawn should review and adopt it as the shared setup; no separate starter project needs to overwrite it.

Clarence's Medications, Form, Detail, and Report Activities are intentionally not implemented here. No placeholder Activities or fake database are included. Today uses explicit component Intents for those agreed class names; the menu destinations and stock-notification Detail destination become usable when Clarence's screens are merged. Until then, an unavailable menu destination shows the generic open-screen error. This is a working contribution, not a complete final application.

## Start Clarence's work from this branch

```powershell
git fetch origin
git switch -c feat/clarence-medication-screens origin/feat/david-core
```

If the branch has already merged, branch from updated main instead. Do not recreate DatabaseHelper, the models, or resources independently.

Add the four Activities under `com.example.medimind.ui`, their layouts, and manifest declarations with `android:exported="false"`:

```xml
<activity android:name=".ui.MedicationsActivity" android:exported="false" />
<activity android:name=".ui.MedicationFormActivity" android:exported="false" />
<activity android:name=".ui.MedicationDetailActivity" android:exported="false" />
<activity android:name=".ui.ReportActivity" android:exported="false" />
```

## Database calls from screens

All database and scheduler calls are synchronous; use lifecycleScope plus Dispatchers.IO. Never close the shared singleton from an Activity.

```kotlin
val db = DatabaseHelper.getInstance(this)
lifecycleScope.launch {
    val medicines = withContext(Dispatchers.IO) {
        db.getAllMedications(searchText.trim(), MedFilter.ACTIVE)
    }
    // Submit medicines to the adapter on the main thread.
}
```

Add-mode drafts use named arguments. Model classes are in `com.example.medimind.model`:

```kotlin
val draft = Medication(
    name = nameText.trim(), dose = doseText.trim(),
    instructions = instructionsText.trim().ifEmpty { null },
    startDate = startDate, endDate = endDate,
    stockQuantity = remainingUnits, // null disables tracking
    unitsPerDose = unitsPerDose,
    lowStockThreshold = threshold
)
val times = selectedTimes.map {
    Schedule(timeOfDay = it, daysOfWeek = selectedWeekdays)
}
```

Before save, call medicationExists and Validator.validateMedication, show errors by ValidationField, then call insertMedication/updateMedication. The database repeats validation transactionally. Display the passed-time confirmation on add, and the next-minute schedule note on edit. For edit, start with the loaded Medication.copy(...) and retain each unchanged schedule's ID. Keep IDs even when editing a time; DatabaseHelper retires/replaces its version internally. Use -1L only for genuinely new times. Do not supply archived rows.

For updateMedication, pass stockQuantityChanged=true **only** when the user explicitly changes Remaining units or switches tracking off/on. Otherwise keep the default false so a loaded form cannot overwrite a newer Taken deduction. Never set lowStockNotified from UI state.

After successful medicine save/delete, run ReminderScheduler.rescheduleAll and checkLowStock on IO. A reminder error does not undo the saved medicine. Reload in onResume; after saving the Form, finish() back to the caller.

```kotlin
val id = withContext(Dispatchers.IO) { db.insertMedication(draft, times) }
if (id > 0L) {
    val states = withContext(Dispatchers.IO) {
        ReminderScheduler.rescheduleAll(this@MedicationFormActivity) to
            ReminderScheduler.checkLowStock(this@MedicationFormActivity, id)
    }
    // Show the save result and any separate permission/scheduling warning, then finish().
}
```

After delete, pass the deleted medicine ID to checkLowStock so its visible stock notification is canceled. The example references MedicationFormActivity as the integration destination; it is not provided by David's branch.

## Detail, report, and stock

- Detail history uses List<DoseItem>, including calculated MISSED values, not List<IntakeLog>. Use current schedules only for the editable plan, and history results for prior occurrences.
- Removing/editing a time preserves historical doses; deleting the entire medicine intentionally cascades history after confirmation.
- Report takes a single DateUtils.now() snapshot, then passes the same today/time to getAdherenceSummary and getMissedDoses on IO. Show all three summary counts. No UI-side dose math.
- Detail's Add refill calls restockMedication(id, positiveQuantity), then checkLowStock(id), then reloads. It adds units; it does not replace stock.
- Blank stock is untracked, zero is tracked/empty. Use toIntOrNull and show an input error for invalid/overflowing text before constructing a model; do not treat invalid text as blank.
- Stock warnings remain visible in Detail when notifications are blocked. A stock notification carries Extras.MEDICATION_ID; a dose notification carries SCHEDULE_ID and DOSE_DATE to Today.

## Build and test

Use JDK 17, SDK platform 36, build tools 36.0.0, and the committed Gradle wrapper. Set sdk.dir in your untracked local.properties via Android Studio. Do not commit absolute SDK paths, signing keys, APKs, Gradle caches, or test device data.

```powershell
.\gradlew.bat assembleDebug lintDebug testDebugUnitTest assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

Instrumented tests use temporary database names except ReminderIntegrationTest, which clears **this app's** default database/notification state. Run them only on a dedicated emulator/test device with fictional data. They never modify other apps. DateUtils.clockOverride is internal and reset after tests.

Utility tests cover strict dates/times, weekday/report boundaries, interval limits, and validation. Instrumented tests exercise real Android SQLite transactions, history preservation, schema upgrades, stock corrections/refills, permission-aware notification posting, and stale reminder rejection. Observed run results and remaining manual checks are recorded below.

## Validation results

Verified on November 16, 2026:

- assembleDebug and assembleDebugAndroidTest: passed; debug app and test APKs built.
- lintDebug: passed with zero errors. Remaining warnings are unused shared resources reserved for Clarence, pinned-version update notices, and optional style/KTX suggestions; no lint baseline or error suppression was introduced.
- testDebugUnitTest: 5 tests passed, zero failures.
- connectedDebugAndroidTest: 19 tests passed, zero failures, on the existing Pixel 8a emulator running Android 17 / API 37. This includes native SQLite, both migration paths, injected mid-transaction failures, actual notification posting/cancellation, stock alert deduplication, and Long-ID alarm registry checks.
- Installed and cold-launched Today; visually checked the empty screen, denied-notification warning, and system-bar insets.
- Original README.md remains unchanged. local.properties, APKs, caches, toolchains, and emulator data are excluded from the commit.

The local Windows host required `-Dorg.gradle.native=false` to bypass an optional Gradle native-library loading problem. JDK 17 and SDK 36 were provisioned in workspace scratch; this workaround is not forced on teammates' machines.

Still required for the final group submission: run on API 26 and API 36 (not available as local test images in this session), test actual elapsed-time alarm delivery while idle and an actual reboot, and verify the complete navigation after Clarence's Activities are integrated. The existing API 37 emulator checks do not replace those target/minimum-device checks. No completed end-to-end medicine-entry UI or physical-device testing is claimed.

## AI-use record

Codex assisted David's implementation of the models, SQLite helper/migrations, utilities, Today screen, and reminder/stock logic, and generated automated tests and integration guidance. David must review the code, verify the observed checks, and be able to explain the transactions, interval rules, permissions, and stock accounting during the demo. This does not assert that David personally ran a check he has not run.
