package com.drivingsim.game

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.drivingsim.game.scene.Scene
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GameRenderer {
    companion object { private const val TAG = "GameRenderer" }

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)
    private var program = 0
    private var mvpHandle = 0
    private var colorHandle = 0
    private var posHandle = 0
    private var ready = false

    fun init() {
        Log.i(TAG, "init()")
        GLES20.glClearColor(0.0f, 0.15f, 0.05f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val vsSrc = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            void main() { gl_Position = uMVP * vec4(aPos, 1.0); }
        """.trimIndent()
        val fsSrc = """
            precision mediump float;
            uniform vec4 uColor;
            void main() { gl_FragColor = uColor; }
        """.trimIndent()

        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        if (vs == 0 || fs == 0) { Log.e(TAG,"shader fail"); return }

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val s = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, s, 0)
        if (s[0] == 0) { Log.e(TAG,"link fail"); GLES20.glDeleteProgram(program); program=0 }

        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        if (program != 0) {
            mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
            colorHandle = GLES20.glGetUniformLocation(program, "uColor")
            posHandle = GLES20.glGetAttribLocation(program, "aPos")
            ready = true
            Log.i(TAG,"init OK")
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { GLES20.glDeleteShader(shader); return 0 }
        return shader
    }

    fun resize(w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        val ratio = w.toFloat() / h.coerceAtLeast(1)
        Matrix.perspectiveM(projMatrix, 0, 60f, ratio, 0.5f, 200f)
    }

    fun draw(world: GameWorld, scene: Scene) {
        if (!ready || program == 0) {
            GLES20.glClearColor(0.1f, 0.1f, 0.15f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val v = world.vehicle
        val camX = (v.posX - 8.0 * Math.sin(v.yaw)).toFloat()
        val camZ = (v.posZ - 8.0 * Math.cos(v.yaw)).toFloat()
        Matrix.setLookAtM(viewMatrix, 0, camX, 5f, camZ, v.posX.toFloat(), 0f, v.posZ.toFloat(), 0f, 1f, 0f)

        // Ground
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(tempMatrix, 0, projMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(colorHandle, 0.12f, 0.22f, 0.10f, 1f)

        val gs = 50f
        val gv = floatArrayOf(-gs, 0f, -gs, gs, 0f, -gs, gs, 0f, gs, -gs, 0f, -gs, gs, 0f, gs, -gs, 0f, gs)
        drawArray(gv, 6)

        // Polygon
        val poly = scene.getBoundaryPolygon()
        if (poly.size >= 2) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniform4f(colorHandle, 1f, 1f, 1f, 0.8f)
            val pv = FloatArray(poly.size * 3)
            for (i in poly.indices) { pv[i*3]=poly[i].first.toFloat(); pv[i*3+1]=0.02f; pv[i*3+2]=poly[i].second.toFloat() }
            drawArrayLine(pv, poly.size, GLES20.GL_LINE_LOOP)
        }

        // Center line
        val cl = scene.getCenterLine()
        if (cl.size >= 2) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniform4f(colorHandle, 1f, 1f, 0f, 0.8f)
            val lv = FloatArray(cl.size * 3)
            for (i in cl.indices) { lv[i*3]=cl[i].first.toFloat(); lv[i*3+1]=0.04f; lv[i*3+2]=cl[i].second.toFloat() }
            drawArrayLine(lv, cl.size, GLES20.GL_LINE_STRIP)
        }

        // Cones
        for ((ox, oz) in scene.getObstacles()) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, ox.toFloat(), 0f, oz.toFloat())
            Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniform4f(colorHandle, 1f, 0.3f, 0f, 1f)
            val cv = floatArrayOf(
                0f, 0.8f, 0f,
                -0.15f, 0f, -0.15f, 0.15f, 0f, -0.15f,
                0.15f, 0f, 0.15f, -0.15f, 0f, 0.15f
            )
            val ci = shortArrayOf(0,1,2, 0,2,3, 0,3,4, 0,4,1)
            val tv = FloatArray(ci.size * 3)
            for (i in ci.indices) { tv[i*3]=cv[ci[i]*3]; tv[i*3+1]=cv[ci[i]*3+1]; tv[i*3+2]=cv[ci[i]*3+2] }
            drawArray(tv, ci.size)
        }

        // Vehicle
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, v.posX.toFloat(), 0.3f, v.posZ.toFloat())
        Matrix.rotateM(modelMatrix, 0, Math.toDegrees(v.yaw).toFloat(), 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(colorHandle, 0.9f, 0.85f, 0.8f, 1f)

        val bw=0.95f; val bl=2.5f; val bh=1.5f
        val bv = floatArrayOf(
            -bw,0f,-bl, bw,0f,-bl, bw,bh,-bl, -bw,bh,-bl,
             bw,0f, bl,-bw,0f, bl,-bw,bh, bl, bw,bh, bl,
            -bw,0f,-bl,-bw,0f, bl,-bw,bh, bl,-bw,bh,-bl,
             bw,0f, bl, bw,0f,-bl, bw,bh,-bl, bw,bh, bl,
            -bw,bh,-bl, bw,bh,-bl, bw,bh, bl,-bw,bh, bl
        )
        val bi = shortArrayOf(
            0,1,2,0,2,3, 4,5,6,4,6,7,
            8,9,10,8,10,11, 12,13,14,12,14,15,
            16,17,18,16,18,19
        )
        val btv = FloatArray(bi.size * 3)
        for (i in bi.indices) { btv[i*3]=bv[bi[i]*3]; btv[i*3+1]=bv[bi[i]*3+1]; btv[i*3+2]=bv[bi[i]*3+2] }
        drawArray(btv, bi.size)

        // Wheels
        GLES20.glUniform4f(colorHandle, 0.08f, 0.08f, 0.08f, 1f)
        val wr=0.35f; val ww=0.25f; val segs=8
        val wv = FloatArray(segs * 2 * 3)
        for (i in 0 until segs) {
            val a1 = (i * 2.0 * Math.PI / segs).toFloat()
            val a2 = ((i + 1) * 2.0 * Math.PI / segs).toFloat()
            wv[i*6]=wr*Math.cos(a1.toDouble()).toFloat()
            wv[i*6+1]=-ww
            wv[i*6+2]=wr*Math.sin(a1.toDouble()).toFloat()
            wv[i*6+3]=wr*Math.cos(a2.toDouble()).toFloat()
            wv[i*6+4]=-ww
            wv[i*6+5]=wr*Math.sin(a2.toDouble()).toFloat()
        }
        val wp = arrayOf(
            Triple(-bw-0.1f, 0f, -bl+0.6f), Triple(bw+0.1f, 0f, -bl+0.6f),
            Triple(-bw-0.1f, 0f,  bl-0.6f), Triple(bw+0.1f, 0f,  bl-0.6f)
        )
        for ((wx, wy, wz) in wp) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, v.posX.toFloat(), 0f, v.posZ.toFloat())
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(v.yaw).toFloat(), 0f, 1f, 0f)
            Matrix.translateM(modelMatrix, 0, wx, wy, wz)
            Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
            drawArray(wv, segs * 2)
        }

        val err = GLES20.glGetError()
        if (err != GLES20.GL_NO_ERROR) Log.w(TAG, "GL err: 0x${Integer.toHexString(err)}")
    }

    private fun drawArray(verts: FloatArray, count: Int) {
        val buf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun drawArrayLine(verts: FloatArray, count: Int, mode: Int) {
        val buf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glDrawArrays(mode, 0, count)
        GLES20.glDisableVertexAttribArray(posHandle)
    }
}
