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
) {
    /** True while the medicine is active and its end date, if there is one, has not passed. */
    fun isCurrentOn(today: String): Boolean = isActive && (endDate == null || endDate >= today)
}

/** One row of the Medications list: the medicine and its dose times in clock order. */
data class MedicationRow(val medication: Medication, val times: List<String>)
