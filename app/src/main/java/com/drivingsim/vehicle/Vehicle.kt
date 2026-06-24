package com.drivingsim.vehicle

import com.drivingsim.control.VehicleInput

/**
 * 车辆物理模型 — 简化刚体 + 动力传动
 * 坐标系：X=左右（左为正），Y=上下，Z=前后（前为正）
 */
class Vehicle {

    // --- 位置与姿态 ---
    var posX = 0.0; var posY = 0.0; var posZ = 0.0
    var velX = 0.0; var velY = 0.0; var velZ = 0.0   // 速度 m/s
    var yaw = 0.0     // 朝向角（弧度，0=正前）
    private val steerAngle = 0f   // 当前前轮转角（度）

    // --- 发动机 ---
    var engineRPM = 800.0   // 当前转速
    var currentGear = 0     // 实际档位
    var gearEngaged = false // 档位是否挂入（离合松开后）

    // --- 参数 ---
    data class Params(
        val mass: Double = 1200.0,
        val maxTorque: Double = 180.0,     // Nm
        val idleRPM: Double = 800.0,
        val redlineRPM: Double = 6500.0,
        val gearRatios: DoubleArray = doubleArrayOf(-3.5, 0.0, 3.8, 2.1, 1.4, 1.0, 0.8),
        val finalDrive: Double = 3.7,
        val wheelRadius: Double = 0.33,    // 米
        val wheelBase: Double = 2.6,
        val brakeForce: Double = 15000.0,
        val dragCoeff: Double = 0.35,
        val rollingResistance: Double = 0.015
    )

    private val p = Params()

    /**
     * 每物理帧调用
     */
    fun update(dt: Float, input: VehicleInput) {
        val dtD = dt.toDouble()

        // 1. 踩离合时断开传动
        if (input.clutch > 0.9f) {
            gearEngaged = false
        }

        // 2. 档位切换逻辑
        if (input.clutch > 0.9f && input.targetGear != currentGear && input.targetGear in -1..5) {
            currentGear = input.targetGear
            gearEngaged = false
        }
        // 松离合挂挡
        if (!gearEngaged && input.clutch < 0.6f && currentGear != 0) {
            gearEngaged = true
            // 转速匹配（简化：直接匹配当前车速对应转速）
            val wheelRPM = velZ / (p.wheelRadius * 2.0 * Math.PI) * 60.0
            engineRPM = wheelRPM * p.gearRatios[currentGear + 1] * p.finalDrive
            if (engineRPM < p.idleRPM) engineRPM = p.idleRPM
        }
        // 回空挡立即断开
        if (currentGear == 0) gearEngaged = false

        // 3. 发动机转速计算
        val throttle = input.throttle.toDouble()
        // 扭矩曲线（简化抛物线）
        val torqueRatio = when {
            engineRPM < p.idleRPM -> 0.0
            engineRPM < 3000.0 -> (engineRPM - p.idleRPM) / (3000.0 - p.idleRPM)
            engineRPM < 5500.0 -> 1.0
            else -> 1.0 - (engineRPM - 5500.0) / (p.redlineRPM - 5500.0)
        }
        val engineTorque = p.maxTorque * torqueRatio * throttle

        // 负载扭矩（来自车轮）
        var loadTorque = 0.0
        if (gearEngaged && currentGear != 0) {
            val ratio = p.gearRatios[currentGear + 1] * p.finalDrive
            val wheelTorque = engineTorque * ratio
            val driveForce = wheelTorque / p.wheelRadius
            val wheelRPM = velZ / (p.wheelRadius * 2.0 * Math.PI) * 60.0
            loadTorque = driveForce * p.wheelRadius / ratio
            engineRPM = wheelRPM * ratio
        }
        val engineAccel = (engineTorque - loadTorque) / 0.3 // 曲轴转动惯量简化
        engineRPM += engineAccel * dtD

        // 怠速维持
        if (engineRPM < p.idleRPM && throttle < 0.01) {
            engineRPM += (p.idleRPM - engineRPM) * 0.1
        }

        // 4. 驱动力
        var driveForce = 0.0
        if (gearEngaged && currentGear != 0) {
            val ratio = p.gearRatios[currentGear + 1] * p.finalDrive
            driveForce = engineTorque * ratio / p.wheelRadius
        }

        // 5. 制动力
        val brakeForce = input.brake.toDouble() * p.brakeForce
        // 发动机制动
        val engineBrake = if (!gearEngaged || currentGear == 0) 300.0 else 800.0 * (engineRPM / p.redlineRPM)
        val totalBrake = if (velZ > 0.1) brakeForce + engineBrake else 0.0

        // 6. 阻力
        val speed = Math.abs(velZ)
        val airDrag = 0.5 * 1.225 * p.dragCoeff * 2.2 * speed * speed
        val rolling = p.rollingResistance * p.mass * 9.81
        val resistance = if (velZ > 0) airDrag + rolling else -(airDrag + rolling)

        // 7. 合力 → 加速度
        val netForce = driveForce - totalBrake - resistance
        val accelZ = netForce / p.mass

        // 8. 转向
        val targetSteer = input.steer.toDouble() * 40.0 // 最大前轮转角 40°
        val currentSteer = steerAngle.toDouble()
        val newSteer = currentSteer + (targetSteer - currentSteer) * dtD * 8.0

        // 转向半径 → 角速度
        val wheelSpeed = Math.abs(velZ)
        val angularVel = if (wheelSpeed > 0.5) {
            val turnRadius = p.wheelBase / Math.tan(Math.toRadians(newSteer))
            velZ / turnRadius
        } else 0.0

        // 9. 积分
        velZ += accelZ * dtD
        yaw += angularVel * dtD
        posX += velZ * Math.sin(yaw) * dtD
        posZ += velZ * Math.cos(yaw) * dtD

        // 限幅
        velZ = velZ.coerceIn(-10.0, 55.0) // -36km/h ~ 198km/h

        // 离合半联动蠕动
        if (!gearEngaged && input.clutch in 0.5f..0.89f && currentGear != 0 && Math.abs(velZ) < 1.0) {
            velZ += 0.5 * (1.0 - input.clutch) * dtD * (if (currentGear > 0) 1 else -1)
        }
    }

    /** 车速 km/h */
    val speedKmh: Double get() = velZ * 3.6

    /** 是否在移动（用于中途停车判定） */
    val isMoving: Boolean get() = Math.abs(velZ) > 0.05
}
