package com.drivingsim.game

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 主游戏循环 — 固定 60FPS 步进
 * 驱动：Physics → Input → Exam → Render
 */
class GameEngine : GLSurfaceView.Renderer {

    companion object {
        const val TARGET_FPS = 60
        const val FIXED_DT = 1.0f / TARGET_FPS
    }

    private var lastFrameTime = 0L
    private var accumulator = 0.0f

    lateinit var world: GameWorld
    lateinit var renderer: GameRenderer
    lateinit var inputAggregator: com.drivingsim.control.InputAggregator
    lateinit var examManager: com.drivingsim.exam.ExamManager
    lateinit var scene: com.drivingsim.game.scene.Scene

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        renderer.init()
        world.init()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        renderer.resize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val delta = ((now - lastFrameTime) / 1_000_000_000.0f).coerceAtMost(0.05f)
        lastFrameTime = now
        accumulator += delta

        while (accumulator >= FIXED_DT) {
            // 核心步进顺序
            val input = inputAggregator.poll()           // 1. 采集传感器+触屏
            world.applyInput(input, FIXED_DT)            // 2. 物理世界响应输入
            world.step(FIXED_DT)                         // 3. 物理步进
            examManager.evaluate(world.vehicle, scene)   // 4. 考试评判
            scene.update(FIXED_DT, world.vehicle)        // 5. 场景逻辑
            accumulator -= FIXED_DT
        }

        renderer.draw(world, scene)
    }
}
