package com.drivingsim.exam

import com.drivingsim.vehicle.Vehicle

/**
 * 考试结果
 */
data class ExamResult(
    val passed: Boolean,           // 是否通过（≥80分）
    val score: Int,                // 100 起始扣分制
    val deductions: List<Deduction>
)

data class Deduction(
    val timestamp: Float,          // 考试时间点
    val description: String,
    val points: Int                // 扣分值
)

/**
 * 碰撞检测辅助数据结构
 */
data class CollisionResult(
    val hitBoundary: Boolean,      // 压线/出线
    val hitCone: Boolean,          // 碰到桩杆
    val hitVehicle: Boolean,       // 碰到其他车（科目三）
    val hitCurb: Boolean           // 碰到路沿
)

/**
 * 科目二考试规则引擎
 */
object Subject2Rules {

    // 扣分标准
    private const val FAIL = -100     // 直接不合格
    private const val DEDUCT_10 = -10
    private const val DEDUCT_5 = -5

    /**
     * 倒车入库评判
     * @param vehicle 车辆状态
     * @param collision 碰撞检测结果
     * @param inGarage 车身是否完全入库
     * @param elapsed 考试耗时
     * @param midwayStopCount 中途停车次数
     */
    fun reverseParking(
        vehicle: Vehicle,
        collision: CollisionResult,
        inGarage: Boolean,
        elapsed: Float,
        midwayStopCount: Int
    ): ExamResult {
        val deductions = mutableListOf<Deduction>()
        var score = 100

        if (collision.hitBoundary) {
            deductions.add(Deduction(elapsed, "车身出线", FAIL))
            score += FAIL
        }
        if (midwayStopCount > 0) {
            deductions.add(Deduction(elapsed, "中途停车 ${midwayStopCount} 次", DEDUCT_5 * midwayStopCount))
            score += DEDUCT_5 * midwayStopCount
        }
        if (!inGarage) {
            deductions.add(Deduction(elapsed, "未完成入库", FAIL))
            score += FAIL
        }
        if (elapsed > 210f) { // 限时 3.5 分钟
            deductions.add(Deduction(elapsed, "超时", FAIL))
            score += FAIL
        }

        return ExamResult(passed = score >= 80, score = score.coerceAtLeast(0), deductions = deductions)
    }

    /**
     * 侧方停车
     */
    fun sideParking(
        vehicle: Vehicle,
        collision: CollisionResult,
        inSpace: Boolean,
        turnSignalOn: Boolean,
        elapsed: Float
    ): ExamResult {
        val deductions = mutableListOf<Deduction>()
        var score = 100

        if (collision.hitBoundary) {
            deductions.add(Deduction(elapsed, "车轮压道路边缘线", FAIL))
            score += FAIL
        }
        if (!inSpace) {
            deductions.add(Deduction(elapsed, "车身未入库", FAIL))
            score += FAIL
        }
        if (!turnSignalOn) {
            deductions.add(Deduction(elapsed, "出库未打转向灯", DEDUCT_10))
            score += DEDUCT_10
        }
        if (collision.hitCurb) {
            deductions.add(Deduction(elapsed, "触碰路沿", DEDUCT_10))
            score += DEDUCT_10
        }

        return ExamResult(passed = score >= 80, score = score.coerceAtLeast(0), deductions = deductions)
    }

    /**
     * 坡道定点停车与起步
     */
    fun hillStart(
        vehicle: Vehicle,
        stopPositionError: Float,  // 前杠距停止线距离 (m)，负=未到
        rollback: Float,           // 后溜距离 (m)
        stall: Boolean,            // 是否熄火
        elapsed: Float
    ): ExamResult {
        val deductions = mutableListOf<Deduction>()
        var score = 100

        when {
            stopPositionError > 0.5f -> {
                deductions.add(Deduction(elapsed, "前杠超出停止线 50cm 以上", FAIL))
                score += FAIL
            }
            stopPositionError > 0.3f -> {
                deductions.add(Deduction(elapsed, "前杠超出停止线 30cm", DEDUCT_10))
                score += DEDUCT_10
            }
            stopPositionError < -0.5f -> {
                deductions.add(Deduction(elapsed, "前杠未到停止线 50cm 以上", FAIL))
                score += FAIL
            }
            stopPositionError < -0.3f -> {
                deductions.add(Deduction(elapsed, "前杠未到停止线 30cm", DEDUCT_10))
                score += DEDUCT_10
            }
        }

        if (rollback > 0.3f) {
            deductions.add(Deduction(elapsed, "后溜超过 30cm", FAIL))
            score += FAIL
        } else if (rollback > 0.1f) {
            deductions.add(Deduction(elapsed, "后溜 ${(rollback * 100).toInt()}cm", DEDUCT_10))
            score += DEDUCT_10
        }

        if (stall) {
            deductions.add(Deduction(elapsed, "起步熄火", DEDUCT_10))
            score += DEDUCT_10
        }

        return ExamResult(passed = score >= 80, score = score.coerceAtLeast(0), deductions = deductions)
    }

    /**
     * 曲线行驶
     */
    fun curveDriving(
        vehicle: Vehicle,
        collision: CollisionResult,
        finished: Boolean,
        elapsed: Float
    ): ExamResult {
        val deductions = mutableListOf<Deduction>()
        var score = 100

        if (collision.hitBoundary) {
            deductions.add(Deduction(elapsed, "车轮压道路边缘线", FAIL))
            score += FAIL
        }
        if (!finished && elapsed > 120f) {
            deductions.add(Deduction(elapsed, "行驶中判断不合格（可能骑压中心线）", FAIL))
            score += FAIL
        }

        return ExamResult(passed = score >= 80, score = score.coerceAtLeast(0), deductions = deductions)
    }

    /**
     * 直角转弯
     */
    fun rightAngleTurn(
        vehicle: Vehicle,
        collision: CollisionResult,
        turnSignalOn: Boolean,
        elapsed: Float
    ): ExamResult {
        val deductions = mutableListOf<Deduction>()
        var score = 100

        if (collision.hitBoundary) {
            deductions.add(Deduction(elapsed, "车轮压道路边缘线", FAIL))
            score += FAIL
        }
        if (!turnSignalOn) {
            deductions.add(Deduction(elapsed, "转弯未打转向灯", DEDUCT_10))
            score += DEDUCT_10
        }

        return ExamResult(passed = score >= 80, score = score.coerceAtLeast(0), deductions = deductions)
    }
}
