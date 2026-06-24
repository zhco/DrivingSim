package com.drivingsim.exam

/**
 * 碰撞检测结果
 */
data class CollisionResult(
    val hasCollision: Boolean,
    val contactPoints: List<Pair<Double, Double>>,
    val obstacleId: String = ""
)
