package com.drivingsim.control

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.MotionEvent

/**
 * 输入聚合器 — 收集陀螺仪方向盘 + 触屏踏板，产出统一 VehicleInput
 */
class InputAggregator(
    private val sensorManager: SensorManager,
    private val screenWidth: Int,
    private val screenHeight: Int
) : SensorEventListener {

    // --- 传感器状态 ---
    private var gyroYaw = 0f       // 累积偏航角（方向盘）
    private var gyroPitch = 0f     // 俯仰（后视镜视角）
    private var gyroRoll = 0f
    private var lastGyroTime = 0L

    // --- 触屏状态 ---
    private var throttle = 0f      // [0, 1] 油门
    private var brake = 0f         // [0, 1] 刹车
    private var clutch = 1f        // [0, 1] 离合（1=完全踩下/断开）
    private var steerTouch = 0f    // 触屏备用方向盘
    private var targetGear = 0     // 目标档位：-1=R, 0=N, 1~5

    // --- 按钮状态 ---
    private var turnSignal = 0     // -1=左, 0=关, 1=右
    private var handbrake = false
    var cameraToggle = false       // 视角切换请求

    // --- 按钮区域定义（归一化坐标 0-1） ---
    private data class ButtonZone(val x: Float, val y: Float, val w: Float, val h: Float)

    private val throttleZone  = ButtonZone(0.80f, 0.20f, 0.15f, 0.50f)
    private val brakeZone      = ButtonZone(0.80f, 0.72f, 0.15f, 0.25f)
    private val clutchZone     = ButtonZone(0.05f, 0.30f, 0.12f, 0.40f)
    private val gearZones = mapOf(
        -1 to ButtonZone(0.45f, 0.55f, 0.06f, 0.10f), // R
         0 to ButtonZone(0.51f, 0.55f, 0.06f, 0.10f), // N
         1 to ButtonZone(0.40f, 0.45f, 0.06f, 0.10f),
         2 to ButtonZone(0.46f, 0.45f, 0.06f, 0.10f),
         3 to ButtonZone(0.52f, 0.45f, 0.06f, 0.10f),
         4 to ButtonZone(0.46f, 0.35f, 0.06f, 0.10f),
         5 to ButtonZone(0.52f, 0.35f, 0.06f, 0.10f),
    )
    private val turnLeftZone   = ButtonZone(0.20f, 0.85f, 0.10f, 0.08f)
    private val turnRightZone  = ButtonZone(0.35f, 0.85f, 0.10f, 0.08f)

    init {
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    // ============ SensorEventListener ============

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val now = System.nanoTime()
            if (lastGyroTime > 0) {
                val dt = (now - lastGyroTime) / 1_000_000_000f
                gyroYaw += event.values[0] * dt      // 绕Z轴
                gyroPitch += event.values[1] * dt
                gyroRoll += event.values[2] * dt
            }
            lastGyroTime = now
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ============ 触屏处理 ============

    fun onTouchEvent(e: MotionEvent) {
        val nx = e.x / screenWidth
        val ny = e.y / screenHeight

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // 油门滑条
                if (hit(throttleZone, nx, ny)) {
                    throttle = ((throttleZone.y + throttleZone.h - ny) / throttleZone.h)
                        .coerceIn(0f, 1f)
                    brake = 0f  // 油门刹车互斥
                }
                // 刹车
                if (hit(brakeZone, nx, ny)) {
                    brake = 1f
                    throttle = 0f
                }
                // 离合器
                if (hit(clutchZone, nx, ny)) {
                    clutch = ((clutchZone.y + clutchZone.h - ny) / clutchZone.h)
                        .coerceIn(0f, 1f)
                }
                // 档位
                gearZones.forEach { (gear, zone) ->
                    if (hit(zone, nx, ny)) targetGear = gear
                }
            }
            MotionEvent.ACTION_UP -> {
                // 松手：油门怠速回落、刹车松开
                if (hit(throttleZone, nx, ny)) throttle = 0f
                if (hit(brakeZone, nx, ny)) brake = 0f
            }
        }

        // 转向灯（点击切换）
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            if (hit(turnLeftZone, nx, ny))  turnSignal = if (turnSignal == -1) 0 else -1
            if (hit(turnRightZone, nx, ny)) turnSignal = if (turnSignal == 1) 0 else 1
        }
    }

    private fun hit(z: ButtonZone, nx: Float, ny: Float) =
        nx in z.x..(z.x + z.w) && ny in z.y..(z.y + z.h)

    // ============ 产出统一输入 ============

    fun poll(): VehicleInput {
        // 低通滤波方向盘
        val rawSteer = (gyroYaw / Math.PI.toFloat()).coerceIn(-1.5f, 1.5f)
        val steer = rawSteer * 0.7f + steerTouch * 0.3f

        return VehicleInput(
            steer = steer.coerceIn(-1f, 1f),
            throttle = throttle,
            brake = brake,
            clutch = clutch,
            targetGear = targetGear,
            turnSignal = turnSignal,
            handbrake = handbrake,
            viewPitch = gyroPitch.coerceIn(-0.3f, 0.3f),
            viewRoll = gyroRoll.coerceIn(-0.2f, 0.2f)
        )
    }

    fun release() {
        sensorManager.unregisterListener(this)
    }
}

/**
 * 统一车辆控制输入，每帧一帧
 */
data class VehicleInput(
    val steer: Float,        // [-1, 1] 方向盘
    val throttle: Float,     // [0, 1] 油门
    val brake: Float,        // [0, 1] 刹车
    val clutch: Float,       // [0, 1] 离合（含半联动区）
    val targetGear: Int,     // -1=R, 0=N, 1~5
    val turnSignal: Int,     // -1=左, 0=关, 1=右
    val handbrake: Boolean,
    val viewPitch: Float,
    val viewRoll: Float
)
