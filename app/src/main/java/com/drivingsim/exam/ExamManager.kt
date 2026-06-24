package com.drivingsim.exam

import com.drivingsim.game.scene.Scene
import com.drivingsim.vehicle.Vehicle

/**
 * 考试管理器 — 状态机驱动科目二/三全部流程
 */
class ExamManager {

    enum class Phase {
        IDLE,
        // 科目二
        S2_REVERSE_PARKING,
        S2_SIDE_PARKING,
        S2_HILL_START,
        S2_CURVE_DRIVING,
        S2_RIGHT_ANGLE,
        S2_FINISHED,
        // 科目三
        S3_START_OFF,
        S3_ROAD_DRIVING,
        S3_PARKING,
        S3_FINISHED
    }

    var currentPhase = Phase.IDLE
        private set

    private var phaseElapsed = 0f
    private var midwayStopCount = 0
    private var wasStopped = false

    // 科目二各项目结果
    val s2Results = mutableMapOf<Phase, ExamResult>()
    // 科目三累计扣分
    val s3Deductions = mutableListOf<Deduction>()

    fun reset() {
        currentPhase = Phase.IDLE
        phaseElapsed = 0f
        midwayStopCount = 0
        wasStopped = false
        s2Results.clear()
        s3Deductions.clear()
    }

    fun startSubject2() {
        reset()
        currentPhase = Phase.S2_SIDE_PARKING  // 实际顺序由考场决定，这里从侧方开始
    }

    fun startSubject3() {
        reset()
        currentPhase = Phase.S3_START_OFF
    }

    /**
     * 每帧由 GameEngine 调用
     */
    fun evaluate(vehicle: Vehicle, scene: Scene) {
        phaseElapsed += 1f / 60f

        // 中途停车检测（对科目二所有项目有效）
        if (currentPhase in listOf(Phase.S2_REVERSE_PARKING, Phase.S2_SIDE_PARKING,
                Phase.S2_HILL_START, Phase.S2_CURVE_DRIVING, Phase.S2_RIGHT_ANGLE)) {

            if (!vehicle.isMoving && !wasStopped) {
                midwayStopCount++
                wasStopped = true
            }
            if (vehicle.isMoving) {
                wasStopped = false
            }
        }
    }

    /**
     * 场景回调：当前项目完成，进入下一项目
     */
    fun onPhaseComplete(result: ExamResult) {
        s2Results[currentPhase] = result
        currentPhase = when (currentPhase) {
            Phase.S2_SIDE_PARKING    -> Phase.S2_REVERSE_PARKING
            Phase.S2_REVERSE_PARKING -> Phase.S2_HILL_START
            Phase.S2_HILL_START      -> Phase.S2_CURVE_DRIVING
            Phase.S2_CURVE_DRIVING   -> Phase.S2_RIGHT_ANGLE
            Phase.S2_RIGHT_ANGLE     -> Phase.S2_FINISHED
            else -> currentPhase
        }
        phaseElapsed = 0f
        midwayStopCount = 0
    }

    fun getMidwayStopCount() = midwayStopCount
    fun getPhaseElapsed() = phaseElapsed

    /**
     * 科目二总成绩
     */
    fun getSubject2Total(): ExamResult {
        var total = 100
        val allDeductions = mutableListOf<Deduction>()
        s2Results.values.forEach { r ->
            total += r.score - 100
            allDeductions.addAll(r.deductions)
        }
        // 任一项目直接不合格 → 总评不合格
        val anyFail = s2Results.values.any { it.score < 80 }
        return ExamResult(
            passed = !anyFail && total >= 80,
            score = total.coerceAtLeast(0),
            deductions = allDeductions
        )
    }

    /**
     * 科目三成绩
     */

    /** HUD 兼容属性 */
    val score: Int
        get() {
            val s2Total = getSubject2Total().score
            val s3Total = getSubject3Total().score
            return if (currentPhase.name.startsWith("S2")) s2Total else s3Total
        }

    val currentItem: String
        get() = when (currentPhase) {
            Phase.S2_REVERSE_PARKING -> "倒车入库"
            Phase.S2_SIDE_PARKING -> "侧方停车"
            Phase.S2_HILL_START -> "坡道起步"
            Phase.S2_CURVE_DRIVING -> "曲线行驶"
            Phase.S2_RIGHT_ANGLE -> "直角转弯"
            Phase.S3_START_OFF -> "起步"
            Phase.S3_ROAD_DRIVING -> "道路驾驶"
            Phase.S3_PARKING -> "靠边停车"
            Phase.S2_FINISHED, Phase.S3_FINISHED -> "考试结束"
            else -> "等待开始"
        }

    val deductions: List<String>
        get() {
            val list = mutableListOf<String>()
            if (currentPhase.name.startsWith("S2")) {
                s2Results.values.forEach { r -> r.deductions.forEach { list.add(it.description) } }
            } else {
                s3Deductions.forEach { list.add(it.description) }
            }
            if (midwayStopCount > 0) list.add("中途停车 x$midwayStopCount (-5x$midwayStopCount)")
            return list
        }


    fun getSubject3Total(): ExamResult {
        var total = 100
        s3Deductions.forEach { total += it.points }
        val passed = total >= 90 // 科目三 90 分合格
        return ExamResult(passed = passed, score = total.coerceAtLeast(0), deductions = s3Deductions.toList())
    }
}
