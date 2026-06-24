package com.drivingsim.game

import com.drivingsim.control.VehicleInput
import com.drivingsim.vehicle.Vehicle

/**
 * 物理世界管理 — 目前单车辆，后续可扩展 AI 车辆（科目三）
 */
class GameWorld {

    val vehicle = Vehicle()

    fun init() {
        // 科目二初始位置（侧方停车出发点）
        vehicle.posX = 1.0
        vehicle.posZ = 0.5
        vehicle.yaw = 0.0
    }

    fun applyInput(input: VehicleInput, dt: Float) {
        // 输入直接透传给车辆模型
    }

    fun step(dt: Float) {
        // 物理步进暂时由 Vehicle.update 内部完成
        // JBullet 集成点：world.stepSimulation(dt)
    }

    fun reset() {
        init()
        vehicle.engineRPM = 800.0
        vehicle.currentGear = 0
    }
}
