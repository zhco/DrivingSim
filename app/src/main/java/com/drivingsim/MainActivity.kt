package com.drivingsim

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.WindowManager
import com.drivingsim.control.InputAggregator
import com.drivingsim.exam.ExamManager
import com.drivingsim.game.GameEngine
import com.drivingsim.game.GameWorld
import com.drivingsim.game.GameRenderer
import com.drivingsim.game.scene.Subject2Scene
import com.drivingsim.ui.GameSurfaceView

class MainActivity : Activity() {

    private lateinit var glSurfaceView: GameSurfaceView
    private lateinit var engine: GameEngine
    private lateinit var inputAggregator: InputAggregator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        glSurfaceView = GameSurfaceView(this)
        setContentView(glSurfaceView)

        // 初始化引擎核心
        engine = GameEngine()
        engine.world = GameWorld()
        engine.renderer = GameRenderer()
        engine.examManager = ExamManager()

        // 输入聚合器
        inputAggregator = InputAggregator(
            sensorManager = getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager,
            screenWidth = resources.displayMetrics.widthPixels,
            screenHeight = resources.displayMetrics.heightPixels
        )
        engine.inputAggregator = inputAggregator

        // 设置触屏回调
        glSurfaceView.setOnTouchListener { _, event ->
            inputAggregator.onTouchEvent(event)
            true
        }

        // 默认加载科目二（后续可通过 UI 选择）
        engine.scene = Subject2Scene(Subject2Scene.Project.SIDE_PARKING)
        engine.scene.init()
        engine.examManager.startSubject2()

        // 启动渲染循环
        glSurfaceView.setEGLContextClientVersion(3)
        glSurfaceView.setRenderer(engine)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        inputAggregator.release()
    }
}
