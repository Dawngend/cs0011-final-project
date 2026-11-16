package com.example.medimind.model

data class Medication(
    val id: Long = -1L,
    val name: String,
    val dose: String,
    val instructions: String? = null,
    val startDate: String,
    val endDate: String? = null,
    val isActive: Boolean = true,
    val stockQuantity: Int? = null,
    val unitsPerDose: Int = 1,
    val lowStockThreshold: Int = 5,
    val lowStockNotified: Boolean = false,
)
