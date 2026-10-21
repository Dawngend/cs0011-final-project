# Test Cases (Draft)

**Status:** draft for the group to review. These are plans only. Nothing has been run, so every status is "not run". The app is not built yet.

The activity needs at least 12 documented test cases. This draft has 20, so the group can drop or merge some. Message ids (M-S1 and so on) refer to `DESIGN_DETAIL.md`, section 6.

## How to Use This File

First, run each case on the emulator or a real phone and fill in the actual result. Then set the status to Pass or Fail. If a case fails, copy it into `DEFECTS_AND_IMPROVEMENTS_LOG.md` as a real defect. Finally, after the fix, run the case again and note the retest result.

Add these columns when you run the cases: actual result, status date, device and Android version, and the screenshot file name.

## Test Data

Prepare these before running. Use made-up medicines only. Do not use real personal or health data.

- Medicine A: "Losartan", dose "50 mg", instructions "after meals", times 08:00 and 20:00, all days, start today.
- Medicine B: "Metformin", dose "500 mg", time 13:00, Monday to Friday, start today.
- A clean install with no data, for the empty state cases.

## Test Cases

| ID | Feature | Steps | Expected result | Status |
| --- | --- | --- | --- | --- |
| TC-01 | Create (CRUD) | 1. Open Medications and tap +. 2. Fill in Medicine A. 3. Tap Save. | Message M-S1 shows. The Medications list shows Losartan 50 mg with times 08:00 and 20:00. | not run |
| TC-02 | Read, data persists | 1. Add Medicine A. 2. Close the app fully. 3. Open it again. 4. Open Medications. | Medicine A is still in the list with the same details. | not run |
| TC-03 | Update (CRUD) | 1. Open Medicine A in Detail. 2. Tap Edit. 3. Change the dose to "100 mg". 4. Save. | Message M-S2 shows. Detail shows "100 mg". Earlier intake history is still there. | not run |
| TC-04 | Delete with confirmation | 1. Open Medicine A in Detail. 2. Tap Delete. 3. Tap Cancel. 4. Tap Delete again and confirm. | The dialog M-C1 shows both times. After Cancel the medicine stays. After confirm, message M-S3 shows and the medicine is gone from the list. | not run |
| TC-05 | Validation, required fields | 1. Open the Form. 2. Leave name and dose empty. 3. Tap Save. | The form does not save. The name field shows "Enter the medicine name." The dose field shows "Enter the dose, for example 500 mg." Nothing is added to the database. | not run |
| TC-06 | Validation, dates, times, days | 1. Open the Form and fill name and dose. 2. Set the end date before the start date. 3. Remove all times and all days. 4. Tap Save. | Each rule shows its own message from the validation table. The form does not save. | not run |
| TC-07 | Validation, duplicates | 1. Add Medicine A. 2. Add another medicine with the same name and dose. 3. In another test, add the same time twice to one medicine. | The first shows "This medicine is already in your list." The second shows "That time is already added." Neither saves. | not run |
| TC-08 | Empty states | 1. On a clean install open Today. 2. Open Medications. 3. Open Report. | Today shows M-N1. Medications shows M-N2. Report shows M-N6. No blank white screen and no crash. | not run |
| TC-09 | Intent to Detail | 1. Add Medicine A and Medicine B. 2. Tap Metformin in the list. 3. Go back and tap Losartan. | Each Detail screen shows the data of the row that was tapped, not of another row. | not run |
| TC-10 | Intent to Form (edit and add) | 1. Open Detail, tap Edit. 2. Check the fields. 3. Go back to Medications and tap + . | Edit shows the fields filled with the saved values, including times and days. Add shows an empty form. | not run |
| TC-11 | Notification | 1. Allow notifications. 2. Save a medicine with a time two minutes from now. 3. Leave the app. 4. Wait for the time. 5. Tap the notification. | A notification "Time for ..." appears at the set time. Tapping it opens Today with that dose shown. | not run |
| TC-12 | Notification permission denied | 1. On Android 13 or newer, deny the notification permission when asked. 2. Add a medicine. 3. Open Today. | The app still saves and works. Today shows the banner M-E4 with a button to open settings. | not run |
| TC-13 | Taken and Skip | 1. Open Today with a dose whose time has passed and one that is upcoming. 2. Tap Taken on the first. 3. Tap Skip on it. | Upcoming dose has no buttons. First tap shows M-S4 and status Taken. The second tap changes it to Skipped with M-S5. There is still only one log for that dose. | not run |
| TC-14 | Missed dose | 1. Save a medicine with a time that passed earlier today. 2. Do not tap anything on that dose. 3. Open Report for 7 days. | The dose shows as missed in Today and in the Report missed list. After tapping Taken it leaves the missed list. | not run |
| TC-15 | Adherence report | 1. Prepare known history: for example 3 Taken, 1 Skipped, 1 missed. 2. Open Report. 3. Switch between 7 and 30 days. | The counts match the known history. Changing the period changes the counts. The percent equals taken divided by all doses. | not run |
| TC-16 | Search | 1. Add Medicine A and B. 2. Type "met" in the search box. 3. Type "zzz". 4. Clear the box. | Step 2 shows only Metformin. Step 3 shows M-N3. Step 4 shows both again. | not run |
| TC-17 | Filter active and done | 1. Add a medicine with an end date in the past or set one to inactive. 2. Tap Active, then Done, then All. | Active shows only current medicines. Done shows only finished ones. All shows both. An empty result shows M-N4. | not run |
| TC-18 | Reminders after restart | 1. Save a medicine with a time ten minutes away. 2. Restart the phone or emulator. 3. Do not open the app. 4. Wait for the time. | Matches the group's choice: either the reminder still fires (boot receiver kept) or the user guide says to open the app once. Record which one was tested. | not run |
| TC-19 | Delete cascade | 1. Add a medicine and log some doses. 2. Delete the medicine. 3. Check the database (App Inspection or a query). | No rows remain for that medicine in schedules or intake_logs. | not run |
| TC-20 | Screen rotation in the Form | 1. Open the Form. 2. Type a name and add a time. 3. Rotate the screen. | The typed values are still there. If they are lost, record it as a real defect. | not run |

## Coverage Check

| Area | Cases |
| --- | --- |
| Create, read, update, delete | TC-01, TC-02, TC-03, TC-04, TC-19 |
| Validation | TC-05, TC-06, TC-07 |
| Empty states and messages | TC-08, TC-04, TC-13 |
| Intents and data transfer | TC-09, TC-10, TC-11 |
| Notification and permissions | TC-11, TC-12, TC-18 |
| Report and missed doses | TC-14, TC-15 |
| Search and filter | TC-16, TC-17 |
| Stability | TC-20 |
