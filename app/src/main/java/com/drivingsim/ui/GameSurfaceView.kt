package com.drivingsim.ui

import android.content.Context
import android.opengl.GLSurfaceView

/**
 * 自定义 GLSurfaceView — OpenGL 渲染画布（ES 2.0）
 */
class GameSurfaceView(context: Context) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(2)
    }
}
