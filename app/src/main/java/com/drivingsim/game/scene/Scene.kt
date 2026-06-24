package com.drivingsim.game.scene

import com.drivingsim.vehicle.Vehicle

/**
 * 场景基类 — 定义考试场地几何约束和碰撞区
 * 所有考试项目场景继承此类
 */
abstract class Scene {

    /** 场景名称 */
    abstract val name: String

    /** 场地边界（用于渲染 + 碰撞） */
    abstract fun getBoundaryPolygon(): List<Pair<Double, Double>>

    /** 道路中心线（用于引导 + 评判偏离） */
    abstract fun getCenterLine(): List<Pair<Double, Double>>

    /** 道路边缘线（压线即不合格） */
    abstract fun getEdgeLines(): List<List<Pair<Double, Double>>>

    /** 障碍物/桩杆位置 */
    abstract fun getObstacles(): List<Pair<Double, Double>>

    /** 触发区（进入/离开某区域时的回调） */
    abstract fun getTriggerZones(): List<TriggerZone>

    /** 场景初始化（加载模型、设置起始位置等） */
    abstract fun init()

    /** 每帧更新 */
    abstract fun update(dt: Float, vehicle: Vehicle)

    /** 当前阶段是否已完成 */
    abstract fun isComplete(): Boolean

    /** 获取当前碰撞检测结果 */
    abstract fun checkCollision(vehicle: Vehicle): com.drivingsim.exam.CollisionResult

    /** 重置场景 */
    open fun reset() {}
}

/**
 * 触发区域 — 进入/离开时分别回调
 */
data class TriggerZone(
    val id: String,
    val polygon: List<Pair<Double, Double>>,
    val onEnter: ((Vehicle) -> Unit)? = null,
    val onExit: ((Vehicle) -> Unit)? = null
)
