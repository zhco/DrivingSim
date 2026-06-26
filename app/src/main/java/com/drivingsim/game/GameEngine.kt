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
        Log.i(TAG, "onSurfaceCreated enter")
        try {
            renderer.init()
            Log.i(TAG, "renderer.init() OK")
        } catch (e: Exception) {
            Log.e(TAG, "renderer.init() FAILED", e)
        }
        // Skip world.init for now - minimize potential crash sources
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged ${width}x${height}")
        try {
            renderer.resize(width, height)
        } catch (e: Exception) {
            Log.e(TAG, "resize failed", e)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        try {
            renderer.draw(world, scene)
        } catch (e: Exception) {
            Log.e(TAG, "onDrawFrame error", e)
        }
    }
}
