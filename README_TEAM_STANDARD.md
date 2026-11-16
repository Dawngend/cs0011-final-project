# MediMind - Team Implementation Guide

A medication reminder and intake tracker for the CS0011 Mobile Programming final project.

This supplementary guide is added as README_TEAM_STANDARD.md. The existing README.md is retained unchanged. Read that repository overview alongside this implementation guide and the linked team standard.

**Repository:** https://github.com/Dawngend/cs0011-final-project  
**Team standard:** 1.1, updated November 16, 2026 (Asia/Manila)  
**David implementation:** [available files and integration guide](docs/DAVID_IMPLEMENTATION.md)
**Implementation contract:** [docs/TEAM_STANDARD.md](docs/TEAM_STANDARD.md)

MediMind saves medicines and schedules on the phone, shows dose and low-stock reminders, records Taken or Skipped actions, and reports taken, skipped, and missed doses over 7 or 30 days. It is a reminder tool and does not give medical advice.

## Start here

1. Read this README and the [team standard](docs/TEAM_STANDARD.md) before coding.
2. Dawn reviews the minimal starter setup included with David's branch, using the pinned build baseline below; do not overwrite it with an unrelated generated project.
3. Review and merge David's shared models, DatabaseHelper, DateUtils, Validator, and Extras before dependent screens are merged. The implementation and tests are provided on feat/david-core.
4. Clarence builds Form, Medications, Detail, and Report against that contract and adds their manifest entries. David's Today/reminders are included; follow the integration guide for the explicit Intent destinations.
5. Joboy prepares the test table now, then runs it against the integrated app. Erold prepares the diagrams and user documentation from the same standard.

This branch includes David's models, database, utilities, Today screen, dose/stock reminders, tests, and a minimal Android starter setup. Clarence's four Activities and the final group integration remain to be completed. The repository contained planning documents without an Android project when implementation began on November 16, 2026. Instructor approval and the final course submission date are not verified by these documents; Dawn records those facts when confirmed. Preserve the repository's approval gate: Dawn confirms instructor approval before application development begins. Documentation and standardization can proceed now.

## Which document wins?

For implementation details, follow `docs/TEAM_STANDARD.md` version 1.1 and its explicitly listed changes to the PDF reference. This implementation guide is the entry point to the new standard. The supplied `MediMind-Group-Reference.pdf` remains the project brief for everything the standard does not change.

The repository's existing `docs/PROPOSAL.md`, `docs/PLAN.md`, `docs/ARCHITECTURE.md`, and `docs/DESIGN_DETAIL.md` are earlier planning documents. Preserve the proposal as a historical submission record; do not silently rewrite an approved proposal. Any verified instructor requirement takes precedence and must be recorded in a follow-up standards change. `docs/IDEAS.md` is historical brainstorming.

The old instruction to exchange finished code through group chat is superseded: **GitHub branches and pull requests are the shared workflow.** Chat can be used for discussion, but the merged repository contains the agreed code and decisions.

## Scope

- Kotlin, Android Views/XML, View Binding, RecyclerView, and explicit Intents between Activities.
- Local SQLite through one SQLiteOpenHelper-based DatabaseHelper.
- Today, Medications, Medication Form, Medication Detail, and Report screens.
- Add/edit/delete medicines, weekly schedules, search, All/Active/Done filters, validation, and confirmations.
- AlarmManager dose reminders, permission handling, and restoration after restart.
- Optional stock tracking with configurable low-stock notifications and a refill action in Medication Detail.
- History and 7-/30-day reports calculated consistently from schedules and intake logs.
- No accounts, cloud synchronization, dosage recommendations, or medical advice.

## Low-stock notifications (added in standard 1.1)

Users can optionally track a medicine's supply by entering remaining units, units used per dose, and a low-stock threshold. Units are whole numbers such as tablets or capsules; they are separate from the dose text (for example, "500 mg"). Defaults are 1 unit per dose and a threshold of 5 units. Tracking is off until a starting quantity is entered.

- A successful Taken action deducts units exactly once, in the same database transaction as the intake log. Skip and calculated Missed do not deduct stock. Correcting today's recorded action adjusts only the units previously deducted for that log.
- Notify when an Active medicine's remaining quantity is at or below its threshold, including zero. Example: "Medicine stock is low: Demo medicine has 5 units remaining."
- Send one alert per low-stock episode. Repeated checks do not repeat the alert. Refilling above the threshold resets the alert; a refill that remains low does not generate another alert.
- If notifications are blocked, keep a visible stock warning in Medication Detail and retry the pending alert when permission is restored and the app checks stock again.
- A stock notification opens the existing Medication Detail screen. The user adds supply using Add refill there; no additional Activity is introduced.
- Check after a medicine/stock save, Taken/Skip correction, or refill, and on app resume/restart. These are quantity-based alerts, not predictions of days remaining. Zero stock does not block recording Taken or suppress dose reminders.

David owns the stock database logic and notifications. Clarence owns stock inputs in the existing Form and the stock/refill controls in Detail. Joboy verifies deductions, correction/refill behavior, blocked permissions, and duplicate-alert prevention. Erold updates the ERD and user guide. Existing working deadlines remain unchanged; Dawn coordinates the added work.

The exact fields, schema-v3 migration, shared methods, alert rules, and tests are in [section 11A of the standard](docs/TEAM_STANDARD.md#11a-low-stock-notifications).

## Owners and working deadlines

| Owner | Deliverables | Working deadline |
| --- | --- | --- |
| David Decano | Shared models, DatabaseHelper, DateUtils, Validator, Extras; then TodayActivity, DoseAdapter, ReminderScheduler, ReminderReceiver, BootReceiver; stock persistence, intake deductions/corrections, low-stock alerts | Tuesday, November 17, 2026 |
| Clarence Canaria | Medication Form, Medications list/search/filter, Detail/history/delete, Report; stock inputs, stock display/refill action; MedicationAdapter and HistoryAdapter | Tuesday, November 17, 2026 |
| Joboy Porras Jr. | At least 12 documented test cases; at least 3 genuine defects with cause/fix/retest; 2 evidence-based improvements; screenshots | Thursday, November 19, 2026 |
| Erold Jan P. Leocario | ERD, UML use cases, navigation diagram, user guide, tools and architecture documentation | Thursday, November 19, 2026 |
| Dawn Andrei Pamesa | Android setup, shared build/resources/manifest integration, code review, merges, fixes, APK, final documentation assembly and submission | After integration; final submission date to be recorded by Dawn |

Everyone supplies an accurate AI-use declaration for their own work and must be able to explain it during the demo. These working deadlines come from the group reference and do not establish the instructor's submission date.

## Pinned build baseline

These are selected compatible versions, not claims about what is already installed or the newest releases.

| Setting | Standard |
| --- | --- |
| Application ID and namespace | `com.example.medimind` |
| Android module | `app` |
| Minimum SDK | 26 (Android 8.0) |
| Compile SDK / Target SDK | 36 / 36 |
| Gradle wrapper | 8.13 |
| Android Gradle Plugin | 8.11.1 |
| Kotlin Android plugin | 2.2.21 |
| Gradle JDK / Java and Kotlin bytecode target | 17 / 17 |
| AppCompat | `androidx.appcompat:appcompat:1.7.1` |
| RecyclerView | `androidx.recyclerview:recyclerview:1.4.0` |
| Material Views | `com.google.android.material:material:1.13.0` |
| Lifecycle runtime | `androidx.lifecycle:lifecycle-runtime-ktx:2.9.4` |
| Coroutines Android | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` |
| UI / storage | XML + View Binding / SQLiteOpenHelper |
| Database | `medimind.db`, schema version **3** |

Dawn commits the wrapper, version catalog, build scripts, and a building launcher Activity together. Use a stable Android Studio version that supports this AGP version. Use the committed Gradle wrapper rather than a separately installed Gradle. Configure the local Gradle JDK to 17. No per-person version upgrades or dynamic dependency versions.

Compatibility references: [AGP 8.11](https://developer.android.com/build/releases/agp-8-11-0-release-notes), [Kotlin/Gradle/AGP](https://kotlinlang.org/docs/gradle-configure-project.html), [AppCompat](https://developer.android.com/jetpack/androidx/releases/appcompat), [RecyclerView](https://developer.android.com/jetpack/androidx/releases/recyclerview), [Material 1.13](https://github.com/material-components/material-components-android/releases/tag/1.13.0), [Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle), [Coroutines 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.10.2).

## Git workflow

Clone the shared repository once:

```powershell
git clone https://github.com/Dawngend/cs0011-final-project.git
cd cs0011-final-project
```

Start a feature from an updated, clean `main`:

```powershell
git switch main
git pull --ff-only origin main
git switch -c feat/david-database
```

Use separate branches such as `feat/dawn-project-setup`, `feat/david-database`, `feat/david-today-reminders`, `feat/clarence-medication-screens`, `test/joboy-acceptance`, and `docs/erold-design-guide`. Integrate David's database PR before the dependent screen PRs.

Commit selected files, push your feature branch, and open a pull request into `main`. Dawn reviews and merges; the author resolves conflicts on the feature branch. Do not force-push `main`, overwrite another member's files, or merge failing builds. A branch name does not give exclusive ownership of shared resource files; coordinate edits to strings, build files, and the manifest in the PR.

Each PR states the behavior changed, affected files/contracts, checks actually run, and any remaining issue. Update the standard in the same PR whenever a shared contract changes. Keep commits small and descriptive. Follow [GitHub flow](https://docs.github.com/en/get-started/using-github/github-flow).

Do not commit `local.properties`, `.gradle/`, `.idea/`, `**/build/`, APK/AAB files, keystores, signing passwords, real medicine data, or device database files. Keep the Gradle wrapper files in Git, including its JAR. Dawn expands the existing root-only build ignore to `**/build/` during setup. Deliver the final APK through the agreed submission or release channel.

## Build and check

After the Android starter project has been merged, run these from the repository root:

```powershell
.\gradlew.bat assembleDebug lintDebug testDebugUnitTest
```

With an emulator or test phone connected:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

The second command is required once instrumented database tests exist. Before a merge, run the checks relevant to the change and report what actually ran; documentation-only changes do not require an Android build. Dawn verifies a clean-clone build and APK installation before submission. Use at least an API 26 emulator and an API 36 emulator/device for the final smoke test.

Joboy's functional checklist is in [the acceptance section](docs/TEAM_STANDARD.md#14-acceptance-checklist). Test records and screenshots must use fictional medicines and test data.

## Most important contract decisions

- Database code owns expected-dose and status calculations; screens display the returned values.
- Only TAKEN and SKIPPED are stored. MISSED and UPCOMING are calculated.
- A report includes today and the preceding 6 or 29 dates, excluding doses later than the current minute.
- Schedule changes preserve history by closing old schedule versions and inserting replacements. The schema adds `effective_from`, `effective_until`, and `is_current` to schedules.
- Changing or removing a time preserves history. Deleting the entire medicine permanently deletes its schedules and logs after confirmation.
- Reminder permission failures do not prevent a medicine from being saved.
- Stock tracking is optional. Taken updates stock atomically; low-stock alerts are deduplicated until supply rises above the threshold. Stock requirements are an explicit feature addition to the original PDF.
- All IDs are Long, weekday numbers are Monday=1 through Sunday=7, and optional text is SQL NULL rather than an empty string.

Read the full standard before implementing these decisions; it defines exact fields, signatures, update rules, messages, and boundary behavior.
