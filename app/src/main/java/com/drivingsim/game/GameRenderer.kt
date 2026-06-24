package com.drivingsim.game

import android.opengl.GLES30
import android.opengl.Matrix
import com.drivingsim.game.scene.Scene

/**
 * OpenGL ES 3.0 渲染器 — 绘制场地、车辆、HUD
 * Stub：后续实现完整的 3D 模型加载和着色器
 */
class GameRenderer {

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var width = 0
    private var height = 0

    fun init() {
        GLES30.glClearColor(0.2f, 0.3f, 0.2f, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    fun resize(w: Int, h: Int) {
        width = w; height = h
        val ratio = w.toFloat() / h
        Matrix.perspectiveM(projMatrix, 0, 60f, ratio, 0.5f, 200f)
    }

    fun draw(world: GameWorld, scene: Scene) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // 第三人称跟随摄像机
        val v = world.vehicle
        val camX = (v.posX - 5.0 * Math.sin(v.yaw)).toFloat()
        val camY = 4.0f
        val camZ = (v.posZ - 5.0 * Math.cos(v.yaw)).toFloat()
        Matrix.setLookAtM(viewMatrix, 0,
            camX, camY, camZ,
            v.posX.toFloat(), 0.5f, v.posZ.toFloat(),
            0f, 1f, 0f
        )
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // --- 绘制场地边界 ---
        drawPolygon(scene.getBoundaryPolygon(), floatArrayOf(0.5f, 0.5f, 0.5f, 1f))

        // --- 绘制中心线 ---
        drawPolyline(scene.getCenterLine(), floatArrayOf(1f, 1f, 0f, 0.6f))

        // --- 绘制边缘线 ---
        scene.getEdgeLines().forEach { line ->
            drawPolyline(line, floatArrayOf(1f, 1f, 1f, 1f))
        }

        // --- 绘制车辆（简化长方体） ---
        drawVehicle(v)

        // --- 绘制障碍物 ---
        scene.getObstacles().forEach { (ox, oz) ->
            drawCone(ox, oz)
        }
    }

    private fun drawPolygon(poly: List<Pair<Double, Double>>, color: FloatArray) {
        // 使用 GL_LINE_LOOP 绘制多边形边框
        // 实现略 — 需要 VAO/VBO
    }

    private fun drawPolyline(line: List<Pair<Double, Double>>, color: FloatArray) {
        // GL_LINE_STRIP
    }

    private fun drawVehicle(v: com.drivingsim.vehicle.Vehicle) {
        // 简化：4.5m×1.8m×1.5m 长方体，朝向由 yaw 决定
    }

    private fun drawCone(x: Double, z: Double) {
        // 锥形桩
    }
}
