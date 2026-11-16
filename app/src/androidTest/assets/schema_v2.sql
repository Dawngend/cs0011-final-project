CREATE TABLE medications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    dose TEXT NOT NULL,
    instructions TEXT,
    start_date TEXT NOT NULL,
    end_date TEXT,
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1))
);

CREATE TABLE schedules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    medication_id INTEGER NOT NULL,
    time_of_day TEXT NOT NULL,
    days_of_week TEXT NOT NULL,
    effective_from TEXT NOT NULL,
    effective_until TEXT,
    is_current INTEGER NOT NULL DEFAULT 1 CHECK (is_current IN (0, 1)),
    FOREIGN KEY (medication_id) REFERENCES medications(id) ON DELETE CASCADE,
    CHECK (effective_until IS NULL OR effective_until >= effective_from)
);

CREATE TABLE intake_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id INTEGER NOT NULL,
    dose_date TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('TAKEN', 'SKIPPED')),
    logged_at TEXT NOT NULL,
    FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE CASCADE,
    UNIQUE (schedule_id, dose_date)
);

CREATE UNIQUE INDEX idx_schedules_current_time
    ON schedules(medication_id, time_of_day) WHERE is_current = 1;
CREATE INDEX idx_schedules_medication ON schedules(medication_id);
CREATE INDEX idx_logs_date ON intake_logs(dose_date);