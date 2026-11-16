# MediMind - Proposal Implementation Guide

**Repository:** https://github.com/Dawngend/cs0011-final-project

**Primary specification:** [docs/PROPOSAL.md](docs/PROPOSAL.md)

**Supplementary implementation agreement:** [docs/TEAM_STANDARD.md](docs/TEAM_STANDARD.md), version 1.2, November 16, 2026 (Asia/Manila).

Implementation status and remaining screen work are described in [the integration guide](docs/IMPLEMENTATION_GUIDE.md).

This guide supplements the original README.md, which is preserved unchanged. The project proposal governs the app's scope, five screens, original tables, navigation, and report display. Our agreement supplies shared Kotlin interfaces and resolves implementation details; it does not override the proposal.

## What we are building

MediMind is a local Android medication reminder and intake tracker using Kotlin, XML Views, explicit Intents, RecyclerView, and SQLiteOpenHelper. It saves medicines/schedules, reminds at dose times, records Taken or Skip, and reports Taken and Missed over 7 or 30 days. It gives no medical advice and has no accounts or online storage.

The only separately requested feature addition is optional stock tracking and low-stock notifications. It is clearly identified below and in the agreement. It is not represented as an existing proposal requirement or instructor approval.

## Alignment with the proposal

- Keep the original medications, schedules, and intake_logs relationships.
- Store current schedules and edit retained schedules by ID in place. Do not add schedule versions, effective-date fields, or a next-minute cutover rule.
- A removed time deletes its linked intake logs after confirmation; deleting a medicine cascades its schedules/logs. Editing retained times preserves their IDs and logs.
- Detail history shows stored Taken/Skipped logs. Report's missed list is calculated from current schedules and dates.
- Report displays two totals: Taken and Missed. Skipped remains a recorded status and is excluded from Missed; no extra Skipped tile or percentage is required.
- Use Today's menu to open Medications or Report. Keep the existing five-screen navigation and explicit ID extras.
- Current-schedule edits can recalculate past inferred missed counts. This is an accepted limit of the proposal schema, not a promise of immutable history.

## Low-stock addition requested by the user

Tracking is optional. Enter a starting whole-unit quantity, units per dose (default 1), and a low-stock threshold (default 5). Units such as tablets are separate from dose text such as 500 mg.

Taken deducts stock once; Skip and calculated Missed do not. Correcting a recorded action adjusts its actual prior deduction. Stock does not become negative, and zero stock does not block recording Taken or stop dose reminders. Detail offers Add refill, which adds supply.

Notify when an Active tracked medicine reaches or falls below its threshold, including zero. Alert once per low-stock episode; refilling above the threshold resets the alert. Blocked notifications leave an inline warning and a pending alert for the next permitted check. A stock alert opens the existing Detail screen; no new Activity is added.

The addition needs four medication fields and one intake-log field, documented in the agreement. It does not add schedule-version columns. The revised database version is 4 to distinguish it from earlier unreleased implementations; the number does not add product features.

## Ownership

| Member | Work |
| --- | --- |
| David Decano | Models, DatabaseHelper, DateUtils, Validator, Extras; Today/DoseAdapter; dose reminders, restart recovery, stock transactions and alerts |
| Clarence Canaria | Form, Medications list/search/filter, Detail/history/delete, Report; stock inputs and Detail refill controls |
| Joboy Porras Jr. | At least 12 documented tests, genuine defects/fixes/retests, improvements, screenshots |
| Erold Jan P. Leocario | ERD, UML/use cases, navigation diagram, user guide, tools/architecture documentation |
| Dawn Andrei Pamesa | Shared setup review, integration, merges, fixes, APK, final documentation and submission |

The group reference's working deadlines remain November 17 for David/Clarence and November 19 for Joboy/Erold. Dawn confirms the course deadline and approval status; no new deadline or instructor approval is asserted here.

## Build baseline

Use applicationId/namespace com.example.medimind, module app, minSdk 26, compileSdk/targetSdk 36, JDK/JVM target 17, Gradle wrapper 8.13, AGP 8.11.1, and Kotlin 2.2.21. Use the shared starter project rather than independently generating conflicting projects.

Pinned libraries: AppCompat 1.7.1, RecyclerView 1.4.0, Material Views 1.13.0, Lifecycle runtime KTX 2.9.4, Coroutines Android 1.10.2. These are implementation pins, not proposal feature requirements. See the [AGP compatibility](https://developer.android.com/build/releases/agp-8-11-0-release-notes) and [Kotlin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html) references.

Once the implementation branch is merged:

```powershell
.\gradlew.bat assembleDebug lintDebug testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

Use a dedicated emulator for instrumented tests. Report actual checks; do not claim unrun device or reboot tests.

## Shared Git workflow

Work in the shared repository, with feature branches and PRs reviewed/merged by Dawn. The original PDF's group-chat file exchange is superseded by the user's repository instruction. Merge the database/model API before dependent screen work.

```powershell
git clone https://github.com/Dawngend/cs0011-final-project.git
cd cs0011-final-project
git switch main
git pull --ff-only origin main
git switch -c feat/your-feature
```

Do not overwrite another member's work or force-push main. Commit the wrapper, including its JAR; exclude local.properties, caches, all module build output, APKs, keystores, credentials, and real health data. Update the agreement when a shared interface changes. An author must review and be able to explain AI-assisted code and accurately declare AI use.

## Document precedence

1. Verified instructor requirements and the repository proposal govern scope.
2. The user's explicit low-stock request is a separate addition, not silently part of the original proposal.
3. Existing architecture/design notes and the group PDF provide details where consistent with the proposal.
4. TEAM_STANDARD.md fills only unresolved interface/behavior details and describes the stock addition. Version 1.2 replaces our earlier versioning/report/cutover choices.

The original proposal is preserved as-is. Its draft/approval label is not independently verified by this guide.
