package com.example.medimind.model

data class DoseItem(
    val scheduleId: Long,
    val medicationId: Long,
    val name: String,
    val dose: String,
    val instructions: String?,
    val timeOfDay: String,
    val doseDate: String,
    val status: DoseStatus,
)
