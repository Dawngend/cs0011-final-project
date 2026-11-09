# Final Checklist

Source: `FP-Final-Project-Documentation.doc` from the instructor (sent 2026-11-09). It sets out the build steps, the 15 documentation sections, the rubric and the deliverables. `PLAN.md` has the overview. This file is the working checklist, so tick items as they are done.

**Status on 2026-11-09:** the proposal was submitted on Canvas on 2026-11-07. The instructor's approval is still open, and no code is written yet. The due date is still unconfirmed (about the end of November).

## Gate

- [ ] Instructor approves the proposal. Do not start coding before this.
- [ ] Confirm the exact due date and the group file naming with the instructor.

## Step 2: Design (the drafts exist, the diagrams do not)

- [x] Wireframes for the screens: `docs/wireframes/` and `WIREFRAMES.md`
- [x] Database tables and CREATE TABLE statements: `DESIGN_DETAIL.md`, section 2
- [x] Intent extras per screen: `DESIGN_DETAIL.md`, section 4
- [ ] Entity-Relationship Diagram (ERD) drawn from the tables
- [ ] Screen navigation diagram (which screen opens which, with the Intent extras)
- [ ] UML use case diagram
- [ ] Functional and non-functional requirements list

## Step 3: Build (70 points)

| Rubric item | Points | Done when |
| --- | --- | --- |
| Problem solution and core functions | 15 | All main features work |
| Database and CRUD | 20 | Create, Read, Update and Delete all work, and data survives closing the app |
| Navigation and data transfer | 10 | At least 3 screens, explicit Intents, correct extras |
| Interface and usability | 10 | Consistent, accessible, easy to follow |
| Validation, feedback and reliability | 8 | Validation, success, error, empty-state and confirmation messages, no crashes |
| Additional feature beyond CRUD | 7 | Reminders (notifications), adherence report, search and filter work |

- [ ] Android Studio project in Kotlin
- [ ] At least 3 screens with explicit Intents and data passed between them
- [ ] SQLite with full CRUD and persistent data
- [ ] Input validation, confirmation messages, error handling and empty-state feedback
- [ ] At least one feature beyond CRUD
- [ ] App matches the approved proposal

## Step 4: Test and Debug

- [ ] 12 or more test cases run (`TEST_CASES_DRAFT.md` has 20 planned)
- [ ] 3 or more defects found, with cause and fix (`DEFECTS_AND_IMPROVEMENTS_LOG.md`)
- [ ] 2 or more improvements based on the test results
- [ ] Retest after each fix
- [ ] Screenshots as evidence (emulator or real phone)

## Step 5: Documentation PDF (30 points)

The guide requires these 15 sections. The right column says which draft in this repo feeds each one.

| # | Section | Draft or source | Done |
| --- | --- | --- | --- |
| 1 | Project title and information | `PROPOSAL.md` | [ ] |
| 2 | Project description and overview | `PROPOSAL.md` | [ ] |
| 3 | Problem statement, target users and objectives | `PROPOSAL.md` | [ ] |
| 4 | Application features and functional requirements | `PROPOSAL.md`, `DESIGN_DETAIL.md` | [ ] |
| 5 | Tools and technologies used | `ARCHITECTURE.md` | [ ] |
| 6 | System architecture and data flow | `ARCHITECTURE.md` (needs a diagram) | [ ] |
| 7 | Database design and ERD | `DESIGN_DETAIL.md` (ERD still to draw) | [ ] |
| 8 | UML use case diagram | not started | [ ] |
| 9 | User interface design, wireframes and screen navigation | `WIREFRAMES.md` (navigation diagram still to draw) | [ ] |
| 10 | Application screenshots and feature descriptions | after the build | [ ] |
| 11 | Test cases and testing results | `TEST_CASES_DRAFT.md` | [ ] |
| 12 | Identified defects and implemented corrections | `DEFECTS_AND_IMPROVEMENTS_LOG.md` | [ ] |
| 13 | Application improvements | `DEFECTS_AND_IMPROVEMENTS_LOG.md` | [ ] |
| 14 | User guide | `USER_GUIDE_OUTLINE.md` | [ ] |
| 15 | Responsible AI-use declaration | `AI_USE_PLAN_DRAFT.md` | [ ] |

Rubric for the documentation: proposal and definition 6, architecture, database and interface design 7, testing, defects and improvements 10, user guide and screenshots 4, AI-use declaration 3.

The guide says all diagrams, tables, screenshots and descriptions must match the finished app, so write sections 6 to 10 after the build, not before.

The AI-use declaration must name the tools, the purposes, the significant prompts, how the output was checked, and what was changed. Fill it from what really happened.

## Step 6: Presentation and Demo

- [ ] Short slides: title, problem, target users, objectives, main functions
- [ ] Live install and run of the APK
- [ ] Walk through the screens, navigation and database operations
- [ ] Show the extra feature
- [ ] Explain the database design and the main code decisions
- [ ] Show test results, fixed defects and improvements
- [ ] Every member can explain the code, the database and any AI-assisted part

## Step 7: Submission

- [ ] Project ZIP: `Surname_FirstName_ProjectTitle.zip`
- [ ] APK: `Surname_FirstName_ProjectTitle.apk`
- [ ] Approved proposal
- [ ] Documentation PDF: `Surname_FirstName_Documentation.pdf`
- [ ] Demo
- [ ] Every file opens, and the APK installs and runs on a clean device
- [ ] Group naming convention checked with the instructor
