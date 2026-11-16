CREATE TABLE medications (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL,
    dose          TEXT    NOT NULL,
    instructions  TEXT,
    start_date    TEXT    NOT NULL,
    end_date      TEXT,
    is_active     INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1))
);

CREATE TABLE schedules (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    medication_id INTEGER NOT NULL,
    time_of_day   TEXT    NOT NULL,
    days_of_week  TEXT    NOT NULL,
    FOREIGN KEY (medication_id) REFERENCES medications(id) ON DELETE CASCADE
);

CREATE TABLE intake_logs (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id   INTEGER NOT NULL,
    dose_date     TEXT    NOT NULL,
    status        TEXT    NOT NULL CHECK (status IN ('TAKEN', 'SKIPPED')),
    logged_at     TEXT    NOT NULL,
    FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE CASCADE,
    UNIQUE (schedule_id, dose_date)
);

CREATE INDEX idx_schedules_medication ON schedules(medication_id);
CREATE INDEX idx_logs_date ON intake_logs(dose_date);