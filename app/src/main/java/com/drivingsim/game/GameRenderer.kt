package com.drivingsim.game

import android.opengl.GLES20
import android.util.Log
import com.drivingsim.game.scene.Scene
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Minimal renderer - renders a colored triangle to verify GL pipeline.
 * Once confirmed working, we can expand back to full scene.
 */
class GameRenderer {

    companion object {
        private const val TAG = "GameRenderer"
    }

    private var program = 0
    private var ready = false
    private val triangleVerts: FloatBuffer

    init {
        // Pre-build triangle data
        val verts = floatArrayOf(
            -0.5f, -0.5f, 0f,
             0.5f, -0.5f, 0f,
             0.0f,  0.5f, 0f
        )
        val buf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(verts).position(0)
        triangleVerts = buf
    }

    fun init() {
        Log.i(TAG, "init() enter")
        try {
            GLES20.glClearColor(0.1f, 0.15f, 0.25f, 1f)

            val vsSrc = """
                attribute vec3 aPos;
                void main() {
                    gl_Position = vec4(aPos, 1.0);
                }
            """.trimIndent()

            val fsSrc = """
                precision mediump float;
                void main() {
                    gl_FragColor = vec4(1.0, 0.5, 0.0, 1.0);
                }
            """.trimIndent()

            val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc)
            val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)

            if (vs == 0 || fs == 0) {
                Log.e(TAG, "Shader compile failed vs=$vs fs=$fs")
                return
            }

            program = GLES20.glCreateProgram()
            if (program == 0) {
                Log.e(TAG, "glCreateProgram returned 0")
                GLES20.glDeleteShader(vs)
                GLES20.glDeleteShader(fs)
                return
            }

            GLES20.glAttachShader(program, vs)
            GLES20.glAttachShader(program, fs)
            GLES20.glLinkProgram(program)

            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(program)
                Log.e(TAG, "Link failed: $log")
                GLES20.glDeleteShader(vs)
                GLES20.glDeleteShader(fs)
                GLES20.glDeleteProgram(program)
                program = 0
                return
            }

            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            ready = true
            Log.i(TAG, "init() OK program=$program")
        } catch (e: Exception) {
            Log.e(TAG, "init() exception", e)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "glCreateShader($type) returned 0")
            return 0
        }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compile error: $log")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun resize(w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
    }

    fun draw(world: GameWorld, scene: Scene) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (!ready || program == 0) {
            return // Just show clear color (dark blue)
        }

        try {
            GLES20.glUseProgram(program)

            val posHandle = GLES20.glGetAttribLocation(program, "aPos")
            if (posHandle < 0) return

            triangleVerts.position(0)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, triangleVerts)
            GLES20.glEnableVertexAttribArray(posHandle)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
            GLES20.glDisableVertexAttribArray(posHandle)

            // Check for errors
            val err = GLES20.glGetError()
            if (err != GLES20.GL_NO_ERROR) {
                Log.w(TAG, "GL error: 0x${Integer.toHexString(err)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "draw() exception", e)
        }
    }
}
