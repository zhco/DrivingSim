package com.drivingsim.exam

import com.drivingsim.vehicle.Vehicle

/**
 * 科目三道路驾驶评判规则
 */
object Subject3Rules {

    private const val FAIL = -100
    private const val DEDUCT_10 = -10
    private const val DEDUCT_5 = -5

    /**
     * 起步评判
     */
    fun startOff(
        rearviewChecked: Boolean,     // 是否观察后视镜
        turnSignalOn: Boolean,        // 是否打转向灯
        signalDuration: Float,        // 转向灯持续时间（秒）
        handbrakeReleased: Boolean,   // 是否松手刹
        clutchSmooth: Boolean,        // 离合是否平顺
        stall: Boolean                // 是否熄火
    ): ExamResult {
        val d = mutableListOf<Deduction>()
        var s = 100

        if (!rearviewChecked)       { d.add(Deduction(0f, "起步未观察后视镜", DEDUCT_5)); s += DEDUCT_5 }
        if (!turnSignalOn)          { d.add(Deduction(0f, "起步未打转向灯", DEDUCT_10)); s += DEDUCT_10 }
        if (signalDuration < 3f)    { d.add(Deduction(0f, "转向灯不足3秒", DEDUCT_10)); s += DEDUCT_10 }
        if (!handbrakeReleased)     { d.add(Deduction(0f, "未松手刹起步", DEDUCT_10)); s += DEDUCT_10 }
        if (!clutchSmooth)          { d.add(Deduction(0f, "离合使用不当", DEDUCT_10)); s += DEDUCT_10 }
        if (stall)                  { d.add(Deduction(0f, "起步熄火", DEDUCT_10)); s += DEDUCT_10 }

        return ExamResult(passed = s >= 80, score = s.coerceAtLeast(0), deductions = d)
    }

    /**
     * 通过路口
     */
    fun passIntersection(
        speedKmh: Double,
        redLightViolation: Boolean,
        stopLineViolation: Boolean,
        observed: Boolean
    ): ExamResult {
        val d = mutableListOf<Deduction>()
        var s = 100

        if (redLightViolation) {
            d.add(Deduction(0f, "闯红灯", FAIL)); s += FAIL
        }
        if (stopLineViolation) {
            d.add(Deduction(0f, "越线停车", DEDUCT_10)); s += DEDUCT_10
        }
        if (speedKmh > 30.0) {
            d.add(Deduction(0f, "路口未减速（${speedKmh.toInt()}km/h）", DEDUCT_10)); s += DEDUCT_10
        }
        if (!observed) {
            d.add(Deduction(0f, "未观察路口交通情况", DEDUCT_5)); s += DEDUCT_5
        }
        return ExamResult(passed = s >= 80, score = s.coerceAtLeast(0), deductions = d)
    }

    /**
     * 变更车道
     */
    fun laneChange(
        turnSignalOn: Boolean,
        signalDuration: Float,
        shoulderCheck: Boolean,
        consecutiveChange: Boolean,  // 是否连续变更两条车道
        safeDistance: Boolean
    ): ExamResult {
        val d = mutableListOf<Deduction>()
        var s = 100

        if (!turnSignalOn)           { d.add(Deduction(0f, "变道未打转向灯", FAIL)); s += FAIL }
        if (signalDuration < 3f)     { d.add(Deduction(0f, "转向灯不足3秒", DEDUCT_10)); s += DEDUCT_10 }
        if (!shoulderCheck)          { d.add(Deduction(0f, "未观察后方来车", FAIL)); s += FAIL }
        if (consecutiveChange)       { d.add(Deduction(0f, "连续变更两条车道", FAIL)); s += FAIL }
        if (!safeDistance)           { d.add(Deduction(0f, "未保持安全车距", FAIL)); s += FAIL }

        return ExamResult(passed = s >= 80, score = s.coerceAtLeast(0), deductions = d)
    }

    /**
     * 靠边停车
     */
    fun roadsideParking(
        distanceToCurb: Float,       // 距路沿距离 (m)
        turnSignalOn: Boolean,
        signalDuration: Float,
        rearviewChecked: Boolean,
        handbrakeOn: Boolean,
        neutralGear: Boolean,
        stall: Boolean
    ): ExamResult {
        val d = mutableListOf<Deduction>()
        var s = 100

        when {
            distanceToCurb > 0.5f -> {
                d.add(Deduction(0f, "距路沿超过 50cm", FAIL)); s += FAIL
            }
            distanceToCurb > 0.3f -> {
                d.add(Deduction(0f, "距路沿 ${(distanceToCurb * 100).toInt()}cm", DEDUCT_10)); s += DEDUCT_10
            }
        }
        if (!turnSignalOn)            { d.add(Deduction(0f, "靠边未打转向灯", DEDUCT_10)); s += DEDUCT_10 }
        if (signalDuration < 3f)      { d.add(Deduction(0f, "转向灯不足3秒", DEDUCT_10)); s += DEDUCT_10 }
        if (!rearviewChecked)         { d.add(Deduction(0f, "未观察右后方", FAIL)); s += FAIL }
        if (!handbrakeOn)             { d.add(Deduction(0f, "未拉手刹", DEDUCT_10)); s += DEDUCT_10 }
        if (!neutralGear)             { d.add(Deduction(0f, "未回空挡", DEDUCT_10)); s += DEDUCT_10 }
        if (stall)                    { d.add(Deduction(0f, "停车熄火", DEDUCT_10)); s += DEDUCT_10 }

        return ExamResult(passed = s >= 80, score = s.coerceAtLeast(0), deductions = d)
    }

    /**
     * 通用评判项
     */
    fun generalRules(
        seatbeltOn: Boolean,
        speeding: Boolean,
        wrongGear: Boolean,
        lookDownGear: Boolean       // 低头看档
    ): ExamResult {
        val d = mutableListOf<Deduction>()
        var s = 100

        if (!seatbeltOn)      { d.add(Deduction(0f, "未系安全带", FAIL)); s += FAIL }
        if (speeding)         { d.add(Deduction(0f, "超速行驶", FAIL)); s += FAIL }
        if (wrongGear)        { d.add(Deduction(0f, "档位与车速不匹配", DEDUCT_10)); s += DEDUCT_10 }
        if (lookDownGear)     { d.add(Deduction(0f, "低头看档", DEDUCT_5)); s += DEDUCT_5 }

        return ExamResult(passed = s >= 80, score = s.coerceAtLeast(0), deductions = d)
    }
}
