package com.drivingsim.game.scene

import com.drivingsim.exam.CollisionResult
import com.drivingsim.vehicle.Vehicle

/**
 * 科目二场景集合 — 5 个项目各自的场地定义
 */
class Subject2Scene(private val project: Project) : Scene() {

    enum class Project {
        REVERSE_PARKING,  // 倒车入库
        SIDE_PARKING,     // 侧方停车
        HILL_START,       // 坡道起步
        CURVE_DRIVING,    // 曲线行驶
        RIGHT_ANGLE       // 直角转弯
    }

    override val name: String get() = when (project) {
        Project.REVERSE_PARKING -> "倒车入库"
        Project.SIDE_PARKING    -> "侧方停车"
        Project.HILL_START      -> "坡道定点停车与起步"
        Project.CURVE_DRIVING   -> "曲线行驶"
        Project.RIGHT_ANGLE     -> "直角转弯"
    }

    override fun getBoundaryPolygon(): List<Pair<Double, Double>> = when (project) {
        // 车库外框 16m × 7m
        Project.REVERSE_PARKING -> listOf(
            0.0 to 0.0, 16.0 to 0.0, 16.0 to 7.0, 0.0 to 7.0
        )
        // 侧方车位 1.5 倍车长 × 车宽+0.6m ≈ 7m×2.5m，操作区域 12m×4m
        Project.SIDE_PARKING -> listOf(
            0.0 to 0.0, 12.0 to 0.0, 12.0 to 4.0, 0.0 to 4.0
        )
        // 坡道：长 20m，宽 7m
        Project.HILL_START -> listOf(
            0.0 to 0.0, 20.0 to 0.0, 20.0 to 7.0, 0.0 to 7.0
        )
        // S弯：外框
        Project.CURVE_DRIVING -> listOf(
            0.0 to 0.0, 30.0 to 0.0, 30.0 to 8.0, 0.0 to 8.0
        )
        // 直角弯：L形通道 3.5m宽
        Project.RIGHT_ANGLE -> listOf(
            0.0 to 0.0, 12.0 to 0.0, 12.0 to 3.5,
            3.5 to 3.5, 3.5 to 12.0, 0.0 to 12.0
        )
    }

    override fun getCenterLine(): List<Pair<Double, Double>> = when (project) {
        Project.REVERSE_PARKING -> listOf(8.0 to 0.0, 8.0 to 7.0)
        Project.SIDE_PARKING    -> listOf(6.0 to 0.0, 6.0 to 4.0)
        Project.HILL_START      -> listOf(10.0 to 0.0, 10.0 to 7.0)
        Project.CURVE_DRIVING -> listOf(
            2.0 to 4.0, 8.0 to 2.0, 15.0 to 3.0,
            22.0 to 5.0, 28.0 to 4.0
        )
        Project.RIGHT_ANGLE -> listOf(
            1.75 to 0.0, 1.75 to 3.5,
            1.75 to 6.0, 3.5 to 6.0
        )
    }

    override fun getEdgeLines(): List<List<Pair<Double, Double>>> {
        val b = getBoundaryPolygon()
        // 简化为：取每条边作为边缘线
        return b.indices.map { i ->
            listOf(b[i], b[(i + 1) % b.size])
        }
    }

    override fun getObstacles(): List<Pair<Double, Double>> = when (project) {
        // 倒车入库：4 根桩杆标记车库角
        Project.REVERSE_PARKING -> listOf(
            6.0 to 0.5, 10.0 to 0.5, 6.0 to 6.5, 10.0 to 6.5
        )
        else -> emptyList()
    }

    override fun getTriggerZones(): List<TriggerZone> = when (project) {
        // 倒车入库：进入车库区域
        Project.REVERSE_PARKING -> listOf(
            TriggerZone(id = "garage", polygon = listOf(
                5.5 to 0.2, 10.5 to 0.2, 10.5 to 6.8, 5.5 to 6.8
            ))
        )
        Project.HILL_START -> listOf(
            TriggerZone(id = "stop_line", polygon = listOf(
                9.5 to 6.8, 10.5 to 6.8, 10.5 to 7.0, 9.5 to 7.0
            ))
        )
        else -> emptyList()
    }

    // --- 碰撞检测 ---
    private var lastCollision = CollisionResult(false, false, false, false)

    override fun checkCollision(vehicle: Vehicle): CollisionResult {
        val vx = vehicle.posX
        val vz = vehicle.posZ
        val hitBoundary = !isInsidePolygon(vx, vz, getBoundaryPolygon())
        val hitCone = getObstacles().any { (ox, oz) ->
            val dx = vx - ox; val dz = vz - oz
            dx * dx + dz * dz < 0.25 // 0.5m 半径
        }
        lastCollision = CollisionResult(
            hitBoundary = hitBoundary,
            hitCone = hitCone,
            hitVehicle = false,
            hitCurb = false
        )
        return lastCollision
    }

    override fun isComplete(): Boolean {
        // 由 ExamManager 驱动阶段切换，场景本身不主动标记完成
        return false
    }

    override fun init() {}
    override fun update(dt: Float, vehicle: Vehicle) {
        checkCollision(vehicle)
    }

    private fun isInsidePolygon(x: Double, z: Double, poly: List<Pair<Double, Double>>): Boolean {
        var inside = false
        val n = poly.size
        var j = n - 1
        for (i in 0 until n) {
            val (xi, zi) = poly[i]
            val (xj, zj) = poly[j]
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
