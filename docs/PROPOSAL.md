# Part 1: Application Proposal (Draft)

**Status:** draft for the group to review. The instructor has not approved it yet. Do not start coding before approval.
**Group:** group of 5. Member names and roles are to be filled in by the group leader.
**Items follow the order in the activity sheet.**

## 1. Application Title and Category

**Title (working):** MediMind: A Medication Reminder and Intake Tracker
**Category:** Health and personal care. Database-driven Android application.

## 2. Problem Statement and Target Users

**Problem.** People who take medicines every day, such as maintenance medicines for high blood pressure or diabetes, often forget a dose or cannot remember if they already took it. Paper lists and phone alarms do not record what was actually taken, so no one can see which doses were missed.

**Target users.**
- Adults and older adults who take daily medicines.
- Family members who help manage a relative's medicines.

## 3. Proposed Solution

MediMind lets a user save each medicine with its dose and daily schedule. At each scheduled time the app shows a notification. The user taps Taken or Skip, and the app keeps a history. A simple report shows how many doses were taken and missed over the last 7 or 30 days. All records stay on the phone in a SQLite database and remain after the app is closed.

The app is a reminder and record keeper. It does not give medical advice.

## 4. Objectives

**General objective.** To develop an Android application that helps users take their medicines on time and keep a reliable record of their intake.

**Specific objectives.**
1. To let users add, view, update and delete medicines and their daily schedules in a SQLite database.
2. To remind users at each scheduled time with a notification.
3. To record each dose as taken or skipped and show doses that were missed.
4. To show an adherence report for the last 7 and 30 days.
5. To provide clear validation, messages and empty states so the app is easy to use.

## 5. Core Features

- Medicine list with search and a filter for active and finished medicines.
- Add or edit a medicine: name, dose, instructions, start date, optional end date, one or more daily times, and days of the week.
- Medicine detail screen with schedule and intake history.
- Today screen that lists today's doses with Taken and Skip buttons.
- Delete with a confirmation dialog.
- Messages for success, errors, empty lists and confirmations.

## 6. Database Technology and Data to Be Stored

**Technology:** SQLite through `SQLiteOpenHelper`, in Kotlin. Data stays on the device.

**Data stored:** medicines, their schedules, and a log of what the user did for each dose. No account and no online storage.

## 7. Tables, Fields, Primary Keys and Relationships

**medications**

| Field | Type | Notes |
| --- | --- | --- |
| id | INTEGER | Primary key, autoincrement |
| name | TEXT | Required |
| dose | TEXT | Required, for example "500 mg" |
| instructions | TEXT | Optional, for example "after meals" |
| start_date | TEXT | yyyy-MM-dd, required |
| end_date | TEXT | Optional |
| is_active | INTEGER | 1 or 0 |

**schedules**

| Field | Type | Notes |
| --- | --- | --- |
| id | INTEGER | Primary key, autoincrement |
| medication_id | INTEGER | Foreign key to medications.id, delete cascades |
| time_of_day | TEXT | HH:mm, required |
| days_of_week | TEXT | For example "1,2,3,4,5,6,7" |

**intake_logs**

| Field | Type | Notes |
| --- | --- | --- |
| id | INTEGER | Primary key, autoincrement |
| schedule_id | INTEGER | Foreign key to schedules.id, delete cascades |
| dose_date | TEXT | yyyy-MM-dd |
| status | TEXT | TAKEN or SKIPPED |
| logged_at | TEXT | Date and time the user tapped |

**Relationships.** One medication has many schedules. One schedule has many intake logs. A log is unique for each schedule and date.

**Rule for missed doses.** A dose is not stored until the user acts on it. A dose whose time has passed and has no log is counted as missed.

## 8. Screen and Navigation Plan

| Screen | Purpose | Opens from |
| --- | --- | --- |
| Today | Today's doses, Taken and Skip, empty state | App start, tapping a notification |
| Medications | List with search and filter, add button | Today (menu) |
| Medication Form | Add or edit a medicine and its times | Medications, Detail |
| Medication Detail | Schedule, history, edit and delete | Medications |
| Report | Taken and missed over 7 or 30 days | Today (menu) |

**Intents and data transfer.**
- Medications to Detail: explicit Intent with `medication_id`.
- Detail to Form (edit): explicit Intent with `medication_id`. Add opens the form with no id.
- Notification to Today: explicit Intent with `schedule_id` and the dose date.
- Form returns to Medications, which reloads the list.

## 9. Initial Wireframes

These are text sketches. Image wireframes will replace them before submission.

```
TODAY                         MEDICATIONS
+---------------------+       +---------------------+
| Today, Oct 21       |       | [ Search...       ] |
|                     |       | (All)(Active)(Done) |
| 08:00 Losartan 50mg |       |                     |
|  [Taken]  [Skip]    |       | Losartan 50 mg      |
| 13:00 Metformin 500 |       |  08:00, 20:00       |
|  [Taken]  [Skip]    |       | Metformin 500 mg    |
| 20:00 Losartan 50mg |       |  13:00              |
|  (upcoming)         |       |                [ + ]|
+---------------------+       +---------------------+

FORM                          DETAIL
+---------------------+       +---------------------+
| Name  [___________] |       | Losartan 50 mg      |
| Dose  [___________] |       | After meals         |
| Notes [___________] |       | Times: 08:00, 20:00 |
| Start [__/__/____]  |       | History             |
| End   [__/__/____]  |       |  Oct 20 08:00 Taken |
| Times [08:00] [+]   |       |  Oct 20 20:00 Skip  |
| Days  [M T W T F S S]|      | [Edit]    [Delete]  |
| [Cancel]    [Save]  |       +---------------------+
+---------------------+
```

The Report screen shows two numbers (taken and missed) and a list of missed doses for the chosen period (7 or 30 days).

## 10. Additional Features Beyond CRUD

1. **Dose reminders by notification** at each scheduled time (main extra feature).
2. **Adherence report** for the last 7 and 30 days.
3. **Search and filter** on the medicine list.

If time is short, the notification reminder and the adherence report are the two to protect. Search and filter can be simplified.

## 11. Development Schedule

The due date is around the end of November (to confirm). Coding starts only after the instructor approves this proposal.

| Week | Dates | Work |
| --- | --- | --- |
| 1 | Oct 21 to Oct 27 | Finish this proposal, get instructor approval, set up the Android project |
| 2 | Oct 28 to Nov 3 | Database class and models, Medication Form, Medications list |
| 3 | Nov 4 to Nov 10 | Detail screen, delete with confirmation, validation and messages |
| 4 | Nov 11 to Nov 17 | Today screen with Taken and Skip, notifications |
| 5 | Nov 18 to Nov 24 | Report, search and filter, testing (12 test cases, defects and fixes) |
| 6 | Nov 25 to Nov 30 | Improvements from testing, documentation, user guide, APK, final checks |

Task owners are decided by the group leader and not written here.

## 12. AI-Use Plan

To be completed later. It will state which AI tools are used for which tasks, how outputs are checked and tested, and what the group changed.

## Risks and Limits

- Notifications on recent Android versions need the notification permission and, for exact times, special alarm handling. We must test this on a real phone or emulator early.
- Reminders may need to be set again after the phone restarts. We decide whether this is in scope or documented as a known limit.
- The app stores health information on the device only. It is a reminder tool and not medical advice, and the app says so.
