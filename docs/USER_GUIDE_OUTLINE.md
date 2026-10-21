# User Guide Outline (Draft)

**Status:** draft for the group to review. This is an outline only. The text and the screenshots are written after the app works. Every screenshot must be a real one from the finished app, with a label.

## 1. About MediMind

- What the app does in two sentences.
- Who it is for.
- The note: "MediMind is a reminder tool. It does not give medical advice."

## 2. Install and First Start

- Install the APK (allow install from this source, if the phone asks).
- Allow notifications when asked. Say why.
- Allow exact alarms if asked. Say what happens if not (reminders can be a few minutes late).
- Screenshot: permission dialog.

## 3. The Today Screen

- How to read the list of doses and their status (upcoming, taken, skipped, missed).
- Tap Taken or Skip.
- Open the menu to reach Medications and Report.
- Screenshot: Today with doses. Screenshot: Today empty state.

## 4. Add a Medicine

- Open Medications, tap +.
- Fill in each field. Table of fields with the rules and the error messages.
- Add times and choose days.
- Save and what the success message looks like.
- Screenshot: empty Form. Screenshot: a validation error.

## 5. View, Edit and Delete a Medicine

- Tap a medicine to open its details and history.
- Edit: change fields and save.
- Delete: the confirmation dialog and what is deleted.
- Screenshot: Detail. Screenshot: delete confirmation.

## 6. Search and Filter

- Type in the search box.
- Switch between All, Active and Done.
- Screenshot: search result and filter.

## 7. Reminders

- What the notification looks like and what tapping it does.
- If a reminder does not come: notification permission, exact alarm setting, battery saver.
- What happens after the phone restarts (match the decision in `DESIGN_DETAIL.md`, section 7.5).
- Screenshot: notification.

## 8. The Report

- Choose 7 or 30 days.
- How to read taken, skipped, missed and the percent.
- The list of missed doses.
- Screenshot: Report.

## 9. Messages You May See

- Table copied from the message catalog in `DESIGN_DETAIL.md`: message, meaning, what to do.

## 10. Known Limits

- Data stays on this phone only. No account and no backup. Uninstalling the app removes it.
- Reminders can be late when the phone saves battery.
- A dose saved for a time that already passed today shows as missed.
- Time zone or clock changes are picked up the next time the app opens.
- Copy any other limit found during testing from the defects log.

## 11. Screenshot Checklist

| Screenshot | Section | File name | Taken |
| --- | --- | --- | --- |
| Permission dialog | 2 | | |
| Today with doses | 3 | | |
| Today empty | 3 | | |
| Form empty | 4 | | |
| Form validation error | 4 | | |
| Medications list | 5 | | |
| Detail | 5 | | |
| Delete confirmation | 5 | | |
| Search and filter | 6 | | |
| Notification | 7 | | |
| Report | 8 | | |
