# Architecture

This is the plan we follow whichever idea we pick. The rule is to keep it simple and boring, using what the course labs used. Confirm the labs' style before the first commit.

## Stack

- Language: Kotlin
- IDE: Android Studio
- Database: SQLite through `SQLiteOpenHelper`
- Lists: `RecyclerView` with one adapter per list
- Navigation: explicit Intents between Activities, passing a record id as an extra

## Layers

1. **Model.** One Kotlin data class per table. No logic.
2. **Database.** One `SQLiteOpenHelper` class that creates the tables and has the CRUD methods for each table (insert, get one, get all, update, delete). One job per method.
3. **Screens.** One Activity per screen. An Activity reads input, validates it, calls the database class, and shows the result.

There is no extra layer between screens and the database. We add one only if the code demands it.

## Screens (minimum 3)

| Screen | Job |
| --- | --- |
| List | Shows records, empty state, search or filter, button to add |
| Detail | Shows one record, buttons to edit and delete (with a confirmation dialog) |
| Add or Edit form | Required-input validation, saves or updates the record |

The idea we pick may add a fourth screen, such as a report or summary.

## Data Flow

1. The List screen loads records from the database and shows them.
2. Tapping an item opens Detail with an explicit Intent that carries the record id.
3. Detail loads that record from the database by id.
4. Edit opens the form with the same id. Saving returns to the list, which reloads.
5. Delete asks for confirmation, removes the record, and shows a message.

## Database Rules

- Every table has an integer primary key (`INTEGER PRIMARY KEY AUTOINCREMENT`).
- Foreign keys are declared where the idea has related tables.
- Records must persist after the app is closed and reopened.

## Usability and Accessibility

- All text goes in `strings.xml`, colors in `colors.xml`, dimensions in `dimens.xml`.
- Content descriptions on every icon and image.
- Touch targets large enough, readable text size, enough color contrast.
- Messages for success, error, empty state and delete confirmation.

## Testing

- A table of at least 12 test cases: id, what is tested, steps, expected result, actual result, pass or fail.
- A defect log of at least 3 defects: what went wrong, how it was found, the fix.
- At least 2 improvements that came from the test evidence.

## Git Workflow

- `main` always builds and runs.
- One branch per feature, merged by pull request.
- Small commits with plain messages.
- Do not commit build output, `local.properties` or keystores.
