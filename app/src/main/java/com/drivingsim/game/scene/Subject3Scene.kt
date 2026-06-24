package com.drivingsim.game.scene

import com.drivingsim.exam.CollisionResult
import com.drivingsim.vehicle.Vehicle

/**
 * 科目三城市道路场景（约 3km 环形路线）
 */
class Subject3Scene : Scene() {

    override val name = "科目三"

    data class RoadPoint(val x: Double, val z: Double, val width: Double = 7.0)

    val route = listOf(
        RoadPoint(0.0, 0.0), RoadPoint(0.0, 50.0), RoadPoint(0.0, 120.0),
        RoadPoint(0.0, 220.0), RoadPoint(0.0, 300.0), RoadPoint(0.0, 380.0),
        RoadPoint(3.5, 420.0), RoadPoint(7.0, 460.0), RoadPoint(7.0, 540.0),
        RoadPoint(7.0, 600.0), RoadPoint(3.5, 640.0), RoadPoint(0.0, 680.0),
        RoadPoint(0.0, 760.0), RoadPoint(0.0, 840.0), RoadPoint(10.0, 880.0),
        RoadPoint(20.0, 920.0), RoadPoint(20.0, 1000.0), RoadPoint(20.0, 1080.0),
        RoadPoint(20.0, 1160.0), RoadPoint(10.0, 1200.0), RoadPoint(0.0, 1240.0),
        RoadPoint(0.0, 1300.0), RoadPoint(0.0, 1380.0), RoadPoint(0.0, 1450.0),
        RoadPoint(0.0, 1500.0)
    )

    data class Zone(val name: String, val startZ: Double, val endZ: Double, val speedLimit: Double, val actions: List<String>)
    val zones = listOf(
        Zone("起步", 0.0, 50.0, 20.0, listOf("打左转向灯", "观察后视镜", "挂一档", "松手刹")),
        Zone("直线行驶", 120.0, 220.0, 30.0, listOf("保持直线", "车速30km/h")),
        Zone("通过路口", 280.0, 320.0, 20.0, listOf("减速", "观察左右")),
        Zone("变更车道", 400.0, 480.0, 25.0, listOf("打右转向灯3秒", "观察右后视镜")),
        Zone("超车", 520.0, 620.0, 30.0, listOf("左转向灯", "加速超车", "右转向灯回位")),
        Zone("学校区域", 740.0, 840.0, 20.0, listOf("减速至20km/h", "禁止鸣笛")),
        Zone("右转弯", 860.0, 940.0, 15.0, listOf("打右转向灯", "减速")),
        Zone("公交站台", 980.0, 1080.0, 20.0, listOf("减速", "观察")),
        Zone("左转弯", 1140.0, 1260.0, 15.0, listOf("打左转向灯", "减速", "观察对向来车")),
        Zone("靠边停车", 1280.0, 1450.0, 15.0, listOf("打右转向灯", "距路边30cm以内", "拉手刹", "回空档"))
    )

    fun getCurrentZone(carZ: Double): Zone? = zones.find { carZ in it.startZ..it.endZ }

    // --- Scene abstract implementations ---

    override fun getBoundaryPolygon(): List<Pair<Double, Double>> {
        val pts = mutableListOf<Pair<Double, Double>>()
        for (p in route) {
            pts.add((p.x - 10.0) to p.z)
            pts.add((p.x + 10.0) to p.z)
        }
        return pts
    }

    override fun getCenterLine(): List<Pair<Double, Double>> =
        route.map { it.x to it.z }

    override fun getEdgeLines(): List<List<Pair<Double, Double>>> {
        val left = route.map { (it.x - 3.5) to it.z }
        val right = route.map { (it.x + 3.5) to it.z }
        return listOf(left, right)
    }

    override fun getObstacles(): List<Pair<Double, Double>> = emptyList()

    override fun getTriggerZones(): List<TriggerZone> = zones.map { z ->
        TriggerZone(z.name, listOf(
            (-10.0 to z.startZ), (10.0 to z.startZ),
            (10.0 to z.endZ), (-10.0 to z.endZ)
        ))
    }

    override fun init() {}

    override fun update(dt: Float, vehicle: Vehicle) {}

    override fun isComplete(): Boolean = false

    override fun checkCollision(vehicle: Vehicle): CollisionResult =
        CollisionResult(false, false, false, false)

    override fun reset() {}
}
