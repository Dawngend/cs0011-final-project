# CS0011 Final Project

Database-driven Android application for CS0011, built in Kotlin with Android Studio and SQLite. Group of 5.

**Status: implemented and tested.** MediMind builds and runs. TC-01 to TC-29 all pass (5 defects fixed, 2 improvements), and the documentation draft is in `docs/DOCUMENTATION.md`. Remaining: presentation and submission.

**Build note:** run `gradlew.bat assembleDebug testDebugUnitTest` with JDK 21 as `JAVA_HOME` (the JDK 25 bundled with newer Android Studio versions fails the Gradle build). The debug APK is written to `app/build/outputs/apk/debug/`.

## What Is Here

- `docs/PROPOSAL.md` has the Part 1 proposal for the medication reminder app.
- `docs/PLAN.md` has the activity requirements as a checklist, the proposal outline, the schedule and the open questions.
- `docs/ARCHITECTURE.md` has the app architecture we follow.
- `docs/TEAM_STANDARD.md` and `docs/IMPLEMENTATION_GUIDE.md` have the shared interfaces and how the parts fit together.
- `docs/TEST_EXECUTION_LOG.md`, `docs/DEFECTS_AND_IMPROVEMENTS_LOG.md` and `docs/DOCUMENTATION.md` have the test results, the defect and improvement log, and the project documentation.

## Rules for the Group

- The proposal is approved, so coding is open.
- Work on a branch per feature and merge into `main` through a pull request.
- Keep the code simple and readable. Match what the Mobile Programming labs used.

## Team

To be filled in.
