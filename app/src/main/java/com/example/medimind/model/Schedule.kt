package com.example.medimind.model

data class Schedule(
    val id: Long = -1L,
    val medicationId: Long = -1L,
    val timeOfDay: String,
    val daysOfWeek: Set<Int>,
)
