package com.drivingsim

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import com.drivingsim.control.InputAggregator
import com.drivingsim.exam.ExamManager
import com.drivingsim.game.GameEngine
import com.drivingsim.game.GameWorld
import com.drivingsim.game.GameRenderer
import com.drivingsim.game.scene.Subject2Scene
import com.drivingsim.ui.GameSurfaceView

class MainActivity : Activity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var glSurfaceView: GameSurfaceView
    private lateinit var engine: GameEngine
    private lateinit var inputAggregator: InputAggregator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )

            glSurfaceView = GameSurfaceView(this)
            setContentView(glSurfaceView)

            engine = GameEngine()
            engine.world = GameWorld()
            engine.renderer = GameRenderer()
            engine.examManager = ExamManager()

            inputAggregator = InputAggregator(
                sensorManager = getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager,
                screenWidth = resources.displayMetrics.widthPixels,
                screenHeight = resources.displayMetrics.heightPixels
            )
            engine.inputAggregator = inputAggregator

            glSurfaceView.setOnTouchListener { _, event ->
                inputAggregator.onTouchEvent(event)
                true
            }

            engine.scene = Subject2Scene(Subject2Scene.Project.SIDE_PARKING)
            engine.scene.init()
            engine.examManager.startSubject2()

            // EGL context version already set in GameSurfaceView.init{}
            glSurfaceView.setRenderer(engine)
            glSurfaceView.renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY

            Log.i(TAG, "onCreate OK")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate CRASH", e)
            // Re-throw so the system can show crash dialog
            throw RuntimeException("Init failed", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try { glSurfaceView.onPause() } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        try { glSurfaceView.onResume() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try { inputAggregator.release() } catch (_: Exception) {}
    }
}
