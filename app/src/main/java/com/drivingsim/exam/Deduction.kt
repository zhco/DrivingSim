package com.drivingsim.exam

/**
 * 扣分项
 */
data class Deduction(
    val points: Int,          // 扣分（负数）
    val description: String,
    val timestamp: Float = 0f
)
