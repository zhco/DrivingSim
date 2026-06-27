package com.drivingsim.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.drivingsim.exam.ExamManager

/**
 * HUD 覆盖层 —— 方向盘、油门/刹车/离合触屏控件、仪表盘
 * 透明度 40% 叠加在 GameSurfaceView 之上
 */
class HudOverlay(context: Context) : View(context) {

    // 仪表数据
    var speedKmh: Float = 0f        // 速度 km/h
    var rpm: Float = 0f            // 转速 0~1
    var gear: Int = 0              // 档位 0=N, 1-5, -1=R
    var clutch: Float = 0f         // 离合器 0~1

    // 刹车/油门/离合值 (0~1)
    var brakeValue: Float = 0f
    var throttleValue: Float = 0f
    var clutchValue: Float = 0f
    var steerInput: Float = 0f     // 触屏方向盘输入 -1~1

    // 考试状态
    var examManager: ExamManager? = null
    var examMode: Int = 0          // 0=自由, 1=科二, 2=科三
    var cameraLabel: String = "后方跟随"

    // 控件区域（横屏）
    private val pedalZone = RectF()
    private val steerZone = RectF()
    private val gearZone = RectF()
    private val startZone = RectF()

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 方向盘触摸追踪
    private var steerTouchId = -1
    private var steerCenterY = 0f
    private var steerCenterX = 0f
    private var steerAngle = 0f

    init {
        setBackgroundColor(Color.TRANSPARENT)
        textPaint.color = Color.WHITE
        textPaint.textSize = 32f
    }

    // region 触屏事件
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (steerZone.contains(x, y)) {
                    steerTouchId = event.getPointerId(0)
                    steerCenterX = steerZone.centerX()
                    steerCenterY = steerZone.centerY()
                    updateSteer(x, y)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    if (event.getPointerId(i) == steerTouchId) {
                        updateSteer(event.getX(i), event.getY(i))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                steerTouchId = -1
                steerInput = 0f
            }
        }

        // 踏板区域：多指触摸（油门/刹车/离合同时按）
        throttleValue = 0f
        brakeValue = 0f
        clutchValue = 0f
        for (i in 0 until event.pointerCount) {
            val px = event.getX(i)
            val py = event.getY(i)
            if (pedalZone.contains(px, py)) {
                val t = (pedalZone.bottom - py) / pedalZone.height()
                val v = t.coerceIn(0f, 1f)
                val relX = (px - pedalZone.left) / pedalZone.width()
                when {
                    relX < 0.33f -> clutchValue = maxOf(clutchValue, v)
                    relX < 0.66f -> brakeValue = maxOf(brakeValue, v)
                    else -> throttleValue = maxOf(throttleValue, v)
                }
            }
        }
        return true
    }

    private fun updateSteer(x: Float, y: Float) {
        val dx = x - steerCenterX
        val dy = y - steerCenterY
        steerAngle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat() - 90f
        steerInput = (steerAngle / 45f).coerceIn(-1f, 1f)
    }
    // endregion

    // region 绘制
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        drawHudBackground(canvas, w, h)
        drawGearButtons(canvas, w, h)
        drawPedals(canvas, w, h)
        drawSteeringWheel(canvas)
        drawDashboard(canvas, w, h)
        drawExamStatus(canvas, w, h)
    }

    private fun drawHudBackground(canvas: Canvas, w: Float, h: Float) {
        hudPaint.color = Color.argb(60, 0, 0, 0)
        // 底部踏板区
        pedalZone.set(w * 0.65f, h * 0.3f, w, h * 0.85f)
        canvas.drawRoundRect(pedalZone, 16f, 16f, hudPaint)
        // 左侧方向盘区
        steerZone.set(0f, h * 0.2f, w * 0.25f, h * 0.8f)
    }

    private fun drawGearButtons(canvas: Canvas, w: Float, h: Float) {
        val btnW = 100f
        val btnH = 80f
        val startX = w * 0.04f
        val startY = h * 0.15f
        val gap = 8f
        val gears = listOf("R", "N", "1", "2", "3", "4", "5")

        btnPaint.style = Paint.Style.FILL
        textPaint.textSize = 28f
        textPaint.textAlign = Paint.Align.CENTER

        for ((i, label) in gears.withIndex()) {
            val bx = startX + (i % 2) * (btnW + gap)
            val by = startY + (i / 2) * (btnH + gap)
            gearZone.set(bx, by, bx + btnW, by + btnH)

            btnPaint.color = if (gear == (if (label == "R") -1 else if (label == "N") 0 else label.toInt()))
                Color.argb(180, 26, 115, 232) else Color.argb(100, 80, 80, 80)
            canvas.drawRoundRect(gearZone, 12f, 12f, btnPaint)
            textPaint.color = Color.WHITE
            canvas.drawText(label, bx + btnW / 2, by + btnH / 2 + 10f, textPaint)
        }
    }

    private fun drawPedals(canvas: Canvas, w: Float, h: Float) {
        val labels = listOf("离合器", "刹车", "油门")
        val values = listOf(clutchValue, brakeValue, throttleValue)
        val colors = listOf(Color.argb(150, 100, 180, 255), Color.argb(150, 255, 50, 50), Color.argb(150, 0, 230, 118))
        val pw = pedalZone.width() / 3f

        btnPaint.style = Paint.Style.FILL
        textPaint.textSize = 24f

        for (i in labels.indices) {
            val px = pedalZone.left + i * pw
            val barH = pedalZone.height() * values[i]
            btnPaint.color = colors[i]
            canvas.drawRect(px + 8f, pedalZone.bottom - barH, px + pw - 8f, pedalZone.bottom, btnPaint)
            textPaint.color = Color.WHITE
            canvas.drawText(labels[i], px + pw / 2, pedalZone.bottom - barH - 8f, textPaint)
            textPaint.color = Color.argb(120, 255, 255, 255)
            canvas.drawText("${(values[i] * 100).toInt()}%", px + pw / 2, pedalZone.bottom - 16f, textPaint)
        }
    }

    private fun drawSteeringWheel(canvas: Canvas) {
        val cx = steerZone.centerX()
        val cy = steerZone.centerY()
        val radius = steerZone.width() * 0.4f

        canvas.save()
        canvas.rotate(steerAngle, cx, cy)

        wheelPaint.style = Paint.Style.STROKE
        wheelPaint.strokeWidth = 12f
        wheelPaint.color = Color.argb(150, 180, 180, 200)
        canvas.drawCircle(cx, cy, radius, wheelPaint)
        wheelPaint.strokeWidth = 20f
        canvas.drawLine(cx, cy - radius, cx, cy - radius * 0.4f, wheelPaint)

        // 中心
        wheelPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 18f, wheelPaint)

        canvas.restore()
    }

    private fun drawDashboard(canvas: Canvas, w: Float, h: Float) {
        val dx = w * 0.9f
        val dy = h * 0.08f

        textPaint.textSize = 56f
        textPaint.color = Color.parseColor("#00E676")
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${speedKmh.toInt()}", dx, dy + 56f, textPaint)

        textPaint.textSize = 24f
        textPaint.color = Color.argb(200, 255, 255, 255)
        canvas.drawText("km/h", dx, dy + 86f, textPaint)

        // 转速条
        val barW = 200f
        val barH = 12f
        hudPaint.color = Color.argb(80, 255, 255, 255)
        canvas.drawRoundRect(dx - barW, dy + 100f, dx, dy + 100f + barH, 6f, 6f, hudPaint)
        hudPaint.color = Color.parseColor("#FF6D00")
        canvas.drawRoundRect(dx - barW, dy + 100f, dx - barW + barW * rpm, dy + 100f + barH, 6f, 6f, hudPaint)

        // 档位
        textPaint.textSize = 48f
        textPaint.color = Color.WHITE
        val gearText = when {
            gear == 0 -> "N"
            gear < 0 -> "R"
            else -> gear.toString()
        }
        canvas.drawText(gearText, dx, dy + 148f, textPaint)
    }

    private fun drawExamStatus(canvas: Canvas, w: Float, h: Float) {
        val ex = examManager ?: return
        if (examMode == 0) return

        val dx = w * 0.5f
        val dy = h * 0.06f
        textPaint.textAlign = Paint.Align.CENTER

        textPaint.textSize = 32f
        textPaint.color = Color.parseColor("#FFD600")
        val modeName = if (examMode == 1) "科目二" else "科目三"
        canvas.drawText("$modeName | 得分: ${ex.score}", dx, dy, textPaint)

        textPaint.textSize = 22f
        textPaint.color = Color.argb(200, 255, 255, 255)
        canvas.drawText(ex.currentItem, dx, dy + 32f, textPaint)

        // 扣分列表
        val deductions = ex.deductions.takeLast(5)
        textPaint.textSize = 18f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.argb(180, 255, 80, 80)
        var dy2 = dy + 70f
        for (d in deductions) {
            canvas.drawText("- $d", 16f, dy2, textPaint)
            dy2 += 24f
        }
    }
    // endregion
}
