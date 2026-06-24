package com.drivingsim.game.scene

/**
 * 科目三城市道路场景
 * 包含直线行驶、变道、超车、通过路口、学校区域、公交站台、靠边停车等路段
 */
class Subject3Scene : Scene() {

    override val name = "科目三"

    // 路线特征点（世界坐标，X=横向，Z=行驶方向）
	data class RoadPoint(val x: Float, val z: Float, val width: Float = 7f)

	// 科目三完整路线（约 3km 环形城市道路）
	val route = listOf(
		RoadPoint(0f, 0f),          // 起点
		RoadPoint(0f, 50f),
		RoadPoint(0f, 120f),        // 直线行驶起点
		RoadPoint(0f, 220f),        // 直线行驶终点
		RoadPoint(0f, 300f),        // 通过路口
		RoadPoint(0f, 380f),
		RoadPoint(3.5f, 420f),      // 变道（右）
		RoadPoint(7f, 460f),
		RoadPoint(7f, 540f),        // 超车区域
		RoadPoint(7f, 600f),
		RoadPoint(3.5f, 640f),      // 变道（左）
		RoadPoint(0f, 680f),
		RoadPoint(0f, 760f),        // 学校区域
		RoadPoint(0f, 840f),
		RoadPoint(10f, 880f),       // 右转
		RoadPoint(20f, 920f),
		RoadPoint(20f, 1000f),      // 公交站台
		RoadPoint(20f, 1080f),
		RoadPoint(20f, 1160f),
		RoadPoint(10f, 1200f),      // 左转
		RoadPoint(0f, 1240f),
		RoadPoint(0f, 1300f),       // 靠边停车区域
		RoadPoint(0f, 1380f),
		RoadPoint(0f, 1450f),       // 终点
		RoadPoint(0f, 1500f)
	)

	// 科目三专属区域定义
	data class Zone(val name: String, val startZ: Float, val endZ: Float, val speedLimit: Float, val actions: List<String>)
	val zones = listOf(
		Zone("起步", 0f, 50f, 20f, listOf("打左转向灯", "观察后视镜", "挂一档", "松手刹")),
		Zone("直线行驶", 120f, 220f, 30f, listOf("保持直线", "车速30km/h")),
		Zone("通过路口", 280f, 320f, 20f, listOf("减速", "观察左右")),
		Zone("变更车道", 400f, 480f, 25f, listOf("打右转向灯3秒", "观察右后视镜")),
		Zone("超车", 520f, 620f, 30f, listOf("左转向灯", "加速超车", "右转向灯回位")),
		Zone("学校区域", 740f, 840f, 20f, listOf("减速至20km/h", "禁止鸣笛")),
		Zone("右转弯", 860f, 940f, 15f, listOf("打右转向灯", "减速")),
		Zone("公交站台", 980f, 1080f, 20f, listOf("减速", "观察")),
		Zone("左转弯", 1140f, 1260f, 15f, listOf("打左转向灯", "减速", "观察对向来车")),
		Zone("靠边停车", 1280f, 1450f, 15f, listOf("打右转向灯", "车身距路边30cm以内", "拉手刹", "回空档"))
	)

	/** 获取车辆当前位置所在区域（科目三） */
	fun getCurrentZone(carZ: Float): Zone? {
		if (carZ < 0f) return null
		return zones.find { carZ >= it.startZ && carZ <= it.endZ }
	}

	/** 科目三专用：判断车是否在车道内（含偏移量检查） */
	fun isInLane(carX: Float, carZ: Float, laneOffset: Float = 3.5f): Boolean {
		var minDist = Float.MAX_VALUE
		for (i in 0 until route.size - 1) {
			val a = route[i]
			val b = route[i + 1]
			// 点到线段的距离
			val dx = b.x - a.x
			val dz = b.z - a.z
			val lenSq = dx * dx + dz * dz
			if (lenSq < 0.01f) continue
			var t = ((carX - a.x) * dx + (carZ - a.z) * dz) / lenSq
			t = t.coerceIn(0f, 1f)
			val projX = a.x + t * dx
			val projZ = a.z + t * dz
			val dist = Math.sqrt(((carX - projX) * (carX - projX) + (carZ - projZ) * (carZ - projZ)).toDouble())
			if (dist < minDist) minDist = dist.toFloat()
		}
		return minDist < laneOffset + 3.5f // 车道宽 ~7m，一半 + 容差
	}
}
