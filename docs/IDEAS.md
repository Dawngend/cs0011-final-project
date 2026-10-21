# Candidate Ideas

The group chose a medication reminder app (not on the original list). See `PROPOSAL.md`. The other ideas below are kept as backups. Each one needs a clear user and a real problem, a database that does real work, and something beyond basic CRUD. The instructor warns against ideas that are too close to basic classroom demos.

## 1. Study and Exam Tracker

- **Users:** students handling several subjects and exams.
- **Problem:** exam dates and review material get scattered, and students do not know what to review next.
- **Tables:** subjects, exams (linked to a subject), flashcards (linked to a subject), review log.
- **Beyond CRUD:** a "due for review" list that picks cards by how recently they were reviewed, plus search and filter by subject.
- **Risk:** the review schedule must be simple to explain and test.

## 2. Expense Tracker With Budgets

- **Users:** students and young workers.
- **Problem:** small daily expenses are not tracked.
- **Tables:** categories, expenses (linked to a category), budgets.
- **Beyond CRUD:** monthly totals per category and a warning when a budget is almost used up.
- **Risk:** very common, so it may look like a basic demo. It needs a clear difference.

## 3. Community Lost and Found

- **Users:** a school or barangay community.
- **Problem:** lost items are announced in scattered chats.
- **Tables:** items (lost or found, status), categories, contact notes.
- **Beyond CRUD:** image attachment, status filter, and search.
- **Risk:** image handling adds testing work.

## 4. Habit and Workout Log

- **Users:** people building a gym or habit routine.
- **Problem:** progress is not recorded, so it is hard to see streaks.
- **Tables:** habits or exercises, daily entries (linked to a habit).
- **Beyond CRUD:** streak count and a weekly report.
- **Risk:** streak logic needs careful test cases.

## How We Pick

The group should compare them on three points: how clear the problem is, how much real database work there is, and how well we can finish it with 5 people before the deadline. The group decides and the instructor approves.
