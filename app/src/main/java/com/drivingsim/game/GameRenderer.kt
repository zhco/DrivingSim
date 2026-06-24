package com.drivingsim.game

import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import com.drivingsim.game.scene.Scene
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class GameRenderer {

    companion object {
        private const val TAG = "GameRenderer"
    }

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    private var width = 0
    private var height = 0
    private var program = 0
    private var mvpHandle = 0
    private var colorHandle = 0
    private var posHandle = 0

    fun init() {
        Log.i(TAG, "init() starting")
        GLES20.glClearColor(0.0f, 0.1f, 0.05f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val vsSrc = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """.trimIndent()

        val fsSrc = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """.trimIndent()

        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)

        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            Log.e(TAG, "Program link failed: " + GLES20.glGetProgramInfoLog(program))
        }

        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        posHandle = GLES20.glGetAttribLocation(program, "aPos")

        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        Log.i(TAG, "init() done, program=$program")
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compile error: " + GLES20.glGetShaderInfoLog(shader))
        }
        return shader
    }

    fun resize(w: Int, h: Int) {
        width = w; height = h
        GLES20.glViewport(0, 0, w, h)
        val ratio = w.toFloat() / h.coerceAtLeast(1)
        Matrix.perspectiveM(projMatrix, 0, 60f, ratio, 0.5f, 200f)
    }

    fun draw(world: GameWorld, scene: Scene) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val v = world.vehicle
        val camX = (v.posX - 8.0 * Math.sin(v.yaw)).toFloat()
        val camY = 5.0f
        val camZ = (v.posZ - 8.0 * Math.cos(v.yaw)).toFloat()
        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ,
            v.posX.toFloat(), 0.0f, v.posZ.toFloat(), 0f, 1f, 0f)

        // 1. Ground
        drawGround()

        // 2. Boundary
        drawPolygon(scene.getBoundaryPolygon(), 1f, 1f, 1f, 0.8f)

        // 3. Center line
        drawPolyline(scene.getCenterLine(), 1f, 1f, 0f, 0.8f)

        // 4. Edge lines
        for (line in scene.getEdgeLines()) {
            drawPolyline(line, 1f, 1f, 1f, 1f)
        }

        // 5. Cones
        for ((ox, oz) in scene.getObstacles()) {
            drawCone(ox, oz)
        }

        // 6. Vehicle
        drawVehicle(v)
    }

    // ====== Ground ======
    private fun drawGround() {
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP()
        setColor(0.12f, 0.22f, 0.10f, 1f)
        val s = 50f
        val verts = floatArrayOf(-s, 0f, -s, s, 0f, -s, s, 0f, s, -s, 0f, -s, s, 0f, s, -s, 0f, s)
        drawTriangles(verts, 6)
    }

    // ====== Polygon outline ======
    private fun drawPolygon(poly: List<Pair<Double, Double>>, r: Float, g: Float, b: Float, a: Float) {
        if (poly.size < 2) return
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP()
        setColor(r, g, b, a)
        val verts = FloatArray(poly.size * 3)
        for (i in poly.indices) {
            verts[i*3] = poly[i].first.toFloat()
            verts[i*3+1] = 0.02f
            verts[i*3+2] = poly[i].second.toFloat()
        }
        drawLineLoop(verts, poly.size)
    }

    // ====== Polyline ======
    private fun drawPolyline(line: List<Pair<Double, Double>>, r: Float, g: Float, b: Float, a: Float) {
        if (line.size < 2) return
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP()
        setColor(r, g, b, a)
        val verts = FloatArray(line.size * 3)
        for (i in line.indices) {
            verts[i*3] = line[i].first.toFloat()
            verts[i*3+1] = 0.04f
            verts[i*3+2] = line[i].second.toFloat()
        }
        GLES20.glLineWidth(3f)
        drawLineStrip(verts, line.size)
    }

    // ====== Vehicle ======
    private fun drawVehicle(v: com.drivingsim.vehicle.Vehicle) {
        val l = 2.25f; val w = 0.9f; val h = 1.4f

        // Body
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, v.posX.toFloat(), 0.3f, v.posZ.toFloat())
        Matrix.rotateM(modelMatrix, 0, Math.toDegrees(v.yaw).toFloat(), 0f, 1f, 0f)
        combineMVP()
        setColor(0.9f, 0.9f, 0.95f, 1f)

        val boxVerts = floatArrayOf(
            -w, -h/2, -l,  w, -h/2, -l,  w, h/2, -l,  -w, h/2, -l,
             w, -h/2,  l, -w, -h/2,  l, -w, h/2,  l,  w, h/2,  l,
            -w, -h/2, -l, -w, -h/2,  l, -w, h/2,  l, -w, h/2, -l,
             w, -h/2,  l,  w, -h/2, -l,  w, h/2, -l,  w, h/2,  l,
            -w, h/2, -l,  w, h/2, -l,  w, h/2,  l, -w, h/2,  l,
        )
        val boxIdx = shortArrayOf(
            0,1,2, 0,2,3, 4,5,6, 4,6,7,
            8,9,10, 8,10,11, 12,13,14, 12,14,15,
            16,17,18, 16,18,19
        )
        drawIndexed(boxVerts, boxIdx)

        // Cabin/windows
        setColor(0.2f, 0.35f, 0.6f, 0.7f)
        val cw = w * 0.75f; val ch = h * 0.35f; val cl = l * 0.45f; val cb = h * 0.15f
        val cabVerts = floatArrayOf(
            -cw, cb+ch, -cl,  cw, cb+ch, -cl,  cw, cb+ch,  cl, -cw, cb+ch,  cl,
            -cw, cb, -cl,  cw, cb, -cl,  cw, cb,  cl, -cw, cb,  cl,
            -cw, cb, -cl, -cw, cb+ch, -cl, -cw, cb+ch,  cl, -cw, cb,  cl,
             cw, cb+ch, -cl,  cw, cb, -cl,  cw, cb,  cl,  cw, cb+ch,  cl,
            -cw, cb+ch, -cl,  cw, cb+ch, -cl,  cw, cb, -cl, -cw, cb, -cl,
            -cw, cb+ch,  cl,  cw, cb+ch,  cl,  cw, cb,  cl, -cw, cb,  cl,
        )
        val cabIdx = shortArrayOf(
            0,1,2, 0,2,3, 4,5,6, 4,6,7,
            8,9,10, 8,10,11, 12,13,14, 12,14,15,
            16,17,18, 16,18,19, 20,21,22, 20,22,23
        )
        drawIndexed(cabVerts, cabIdx)

        // 4 wheels
        setColor(0.08f, 0.08f, 0.08f, 1f)
        val wheelPositions = arrayOf(
            Triple(-w-0.15f, -l+0.7f, 0.33f), Triple(w+0.15f, -l+0.7f, 0.33f),
            Triple(-w-0.15f, l-0.7f, 0.33f), Triple(w+0.15f, l-0.7f, 0.33f)
        )
        val wr = 0.33f; val wtw = 0.2f; val segs = 8
        val wheelVerts = FloatArray(segs * 2 * 3)
        for (i in 0 until segs) {
            val a1 = (i * 2.0 * Math.PI / segs).toFloat()
            val a2 = ((i + 1) * 2.0 * Math.PI / segs).toFloat()
            val base = i * 6
            wheelVerts[base] = (wr * Math.cos(a1.toDouble())).toFloat()
            wheelVerts[base+1] = -wtw
            wheelVerts[base+2] = (wr * Math.sin(a1.toDouble())).toFloat()
            wheelVerts[base+3] = (wr * Math.cos(a2.toDouble())).toFloat()
            wheelVerts[base+4] = -wtw
            wheelVerts[base+5] = (wr * Math.sin(a2.toDouble())).toFloat()
        }

        for ((wx, wz, wy) in wheelPositions) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, v.posX.toFloat(), 0f, v.posZ.toFloat())
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(v.yaw).toFloat(), 0f, 1f, 0f)
            Matrix.translateM(modelMatrix, 0, wx, wy, wz)
            combineMVP()
            drawTriangles(wheelVerts, segs * 2)
        }
    }

    // ====== Cone ======
    private fun drawCone(x: Double, z: Double) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x.toFloat(), 0f, z.toFloat())
        combineMVP()
        setColor(1f, 0.3f, 0f, 1f)

        val verts = floatArrayOf(
            0f, 0.8f, 0f,
            -0.15f, 0f, -0.15f, 0.15f, 0f, -0.15f,
            0.15f, 0f, 0.15f, -0.15f, 0f, 0.15f
        )
        val idx = shortArrayOf(0,1,2, 0,2,3, 0,3,4, 0,4,1)
        drawIndexed(verts, idx)
    }

    // ============ Low-level draw helpers ============
    private fun combineMVP() {
        Matrix.multiplyMM(tempMatrix, 0, projMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
    }

    private fun setColor(r: Float, g: Float, b: Float, a: Float) {
        GLES20.glUniform4f(colorHandle, r, g, b, a)
    }

    private fun drawTriangles(verts: FloatArray, count: Int) {
        val buf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun drawLineLoop(verts: FloatArray, count: Int) {
        val buf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, count)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun drawLineStrip(verts: FloatArray, count: Int) {
        val buf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, count)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun drawIndexed(verts: FloatArray, indices: ShortArray) {
        val vBuf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vBuf.put(verts).position(0)
        val iBuf = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        iBuf.put(indices).position(0)

        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vBuf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.size, GLES20.GL_UNSIGNED_SHORT, iBuf)
        GLES20.glDisableVertexAttribArray(posHandle)
    }
}