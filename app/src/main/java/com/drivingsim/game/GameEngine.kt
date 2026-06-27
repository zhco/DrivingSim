package com.drivingsim.game

import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GameEngine : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "GameEngine"
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
        Log.i(TAG, "onSurfaceCreated")
        try { renderer.init(); Log.i(TAG, "renderer OK") } catch (e: Exception) { Log.e(TAG, "renderer FAIL", e) }
        try { world.init(); Log.i(TAG, "world OK") } catch (e: Exception) { Log.e(TAG, "world FAIL", e) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        try { renderer.resize(width, height) } catch (e: Exception) { Log.e(TAG, "resize FAIL", e) }
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
                    if (inputAggregator.cameraToggle) { inputAggregator.cameraToggle = false; renderer.nextCamera() }
                    world.step(FIXED_DT)
                    examManager.evaluate(world.vehicle, scene)
                    scene.update(FIXED_DT, world.vehicle)
                } catch (e: Exception) { Log.e(TAG, "step error", e) }
                accumulator -= FIXED_DT
            }
            try { renderer.draw(world, scene) } catch (e: Exception) { Log.e(TAG, "draw error", e) }
        } catch (e: Exception) { Log.e(TAG, "frame crash", e) }
    }
}
