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
        try {
            renderer.init()
            android.util.Log.i("GameEngine", "renderer.init() OK")
        } catch (e: Exception) {
            android.util.Log.e("GameEngine", "renderer.init() FAILED", e)
        }
        try {
            world.init()
            android.util.Log.i("GameEngine", "world.init() OK")
        } catch (e: Exception) {
            android.util.Log.e("GameEngine", "world.init() FAILED", e)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        renderer.resize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        try {
            val now = System.nanoTime()
            val delta = ((now - lastFrameTime) / 1_000_000_000.0f).coerceAtMost(0.05f)
            lastFrameTime = now
            accumulator += delta

            while (accumulator >= FIXED_DT) {
                try {
                    val input = inputAggregator.poll()
                    world.applyInput(input, FIXED_DT)
                    world.step(FIXED_DT)
                    examManager.evaluate(world.vehicle, scene)
                    scene.update(FIXED_DT, world.vehicle)
                } catch (e: Exception) {
                    android.util.Log.e("GameEngine", "Physics step error", e)
                }
                accumulator -= FIXED_DT
            }

            try {
                renderer.draw(world, scene)
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "Render error", e)
            }
        } catch (e: Exception) {
            android.util.Log.e("GameEngine", "onDrawFrame crash", e)
        }
    }
}
