# Defects and Improvements Log (Template)

**Status:** draft for the group to review. This file is an empty template. No defects or improvements are recorded because nothing has been built or tested yet. Fill it in only with what really happens during testing.

The activity needs at least 3 defects with corrections and at least 2 improvements based on test evidence.

## How to Record Honestly

- Write a defect only when a test really fails or you really see something wrong. Do not make one up to reach the count.
- Record it right away, while you remember it. Write what you saw, not what you think it should have been.
- Link each defect to the test case that found it. Keep the screenshot or a short note of the steps.
- Write the real cause after you find it in the code. If you do not know the cause yet, leave it blank and say "under investigation".
- Run the same test case again after the fix. Record the retest result, even if it still fails. A failed retest stays open.
- If you find fewer than 3 defects, keep testing: odd inputs, rotation, rapid taps, long text, other Android versions. If there still are fewer, report the true number and tell the instructor. Do not invent the rest.
- An improvement needs evidence: a test result, a slow step, a confusing screen or tester feedback. Write the evidence first, then the change, then what happened after.
- Note the build or commit and the device for each retest, so the result can be checked later.

## Defects

| ID | Found in test case | What happened | Cause | Fix | Retest result |
| --- | --- | --- | --- | --- | --- |
| D-01 | | | | | |
| D-02 | | | | | |
| D-03 | | | | | |
| D-04 | | | | | |

Add rows as needed. Optional extra columns: date found, device and Android version, commit of the fix, screenshot file name.

## Improvements

| ID | Evidence (test case, defect or feedback) | Change made | Result after the change |
| --- | --- | --- | --- |
| I-01 | | | |
| I-02 | | | |
| I-03 | | | |

Add rows as needed. Optional extra columns: date, commit, before and after screenshot file names.

## Summary (fill in at the end)

| Item | Count |
| --- | --- |
| Test cases run | |
| Passed on first run | |
| Defects found | |
| Defects fixed and retested | |
| Improvements made | |
