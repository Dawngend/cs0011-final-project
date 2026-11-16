package com.example.medimind.model

data class IntakeLog(
    val id: Long = -1L,
    val scheduleId: Long,
    val doseDate: String,
    val status: IntakeStatus,
    val loggedAt: String,
    val stockDeducted: Int = 0,
)
