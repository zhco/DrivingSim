package com.drivingsim.exam

/**
 * 考试成绩
 */
data class ExamResult(
    val passed: Boolean,
    val score: Int,
    val deductions: List<Deduction> = emptyList()
)
