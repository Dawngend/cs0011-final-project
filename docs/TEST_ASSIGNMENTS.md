# Test Assignments (Joboy and Erold)

Joboy and Erold own all the testing together. They decide between themselves how to split the cases.

Build to test: branch `feat/medication-form`, commit `4ca8943` (Clarence's "Refresh MediMind UI"). Use the Pixel 10 Pro emulator (API 37.1) or a real phone, and write the device in every result.

## What They Cover

- All 20 cases in `TEST_CASES_DRAFT.md` (TC-01 to TC-20, test data in that file).
- The stock tests from `TEAM_STANDARD.md`: deductions and corrections, threshold boundaries, refills, overflow, and duplicate low-stock notification suppression. Write each one as a new case, TC-21 onward, in the same table format.
- The refreshed UI in dark mode and with a large font size (Clarence added night themes).

## Rules

1. Fill in the actual result, status (Pass or Fail), date, device and screenshot file name for every case. Save screenshots in `docs/screenshots/` named `TC-01.png` and so on.
2. A failed case becomes a row in `DEFECTS_AND_IMPROVEMENTS_LOG.md` (D-01...) with the real cause once found, then a retest. Do not invent a defect to reach 3. If fewer than 3 are real, keep testing odd inputs, rapid taps and long text, then tell Dawn.
3. At least 2 improvements, each with evidence from a test result or a confusing screen. Write the evidence first.
4. Do not both edit the same case. Agree on who runs which before starting.
5. Tell Clarence or David about any defect right away so it can be fixed and retested.
6. Send results as a pull request to `main`, or message Dawn, who reviews and merges.
