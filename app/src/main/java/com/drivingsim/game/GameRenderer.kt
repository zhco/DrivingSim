package com.drivingsim.game

import android.opengl.GLES30
import android.opengl.Matrix
import com.drivingsim.game.scene.Scene
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GameRenderer {

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private var width = 0
    private var height = 0
    private var program = 0
    private var mvpHandle = 0
    private var colorHandle = 0
    private var posHandle = 0

    fun init() {
        GLES30.glClearColor(0.0f, 0.15f, 0.05f, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        // 编译着色器
        val vs = compileShader(GLES30.GL_VERTEX_SHADER,
            """#version 300 es
            uniform mat4 uMVP;
            in vec3 aPos;
            void main() { gl_Position = uMVP * vec4(aPos, 1.0); }""")
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER,
            """#version 300 es
            precision mediump float;
            uniform vec4 uColor;
            out vec4 fragColor;
            void main() { fragColor = uColor; }""")

        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)

        mvpHandle = GLES30.glGetUniformLocation(program, "uMVP")
        colorHandle = GLES30.glGetUniformLocation(program, "uColor")
        posHandle = GLES30.glGetAttribLocation(program, "aPos")

        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
    }

    fun resize(w: Int, h: Int) {
        width = w; height = h
        val ratio = w.toFloat() / h
        Matrix.perspectiveM(projMatrix, 0, 60f, ratio, 0.5f, 200f)
    }

    fun draw(world: GameWorld, scene: Scene) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)

        val v = world.vehicle
        // 追尾摄像机：位于车辆后方上方
        val camX = (v.posX - 8.0 * Math.sin(v.yaw)).toFloat()
        val camY = 5.0f
        val camZ = (v.posZ - 8.0 * Math.cos(v.yaw)).toFloat()
        Matrix.setLookAtM(viewMatrix, 0,
            camX, camY, camZ,
            v.posX.toFloat(), 0.0f, v.posZ.toFloat(),
            0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // 1. 绘制地面
        drawGround()

        // 2. 场地边界（白色线框）
        drawPolygon(scene.getBoundaryPolygon(),
            floatArrayOf(1f, 1f, 1f, 0.8f))

        // 3. 中心线（黄色虚线）
        drawPolyline(scene.getCenterLine(),
            floatArrayOf(1f, 1f, 0f, 0.8f))

        // 4. 边缘线（白色实线）
        scene.getEdgeLines().forEach { line ->
            drawPolyline(line, floatArrayOf(1f, 1f, 1f, 1f))
        }

        // 5. 障碍物锥桶
        scene.getObstacles().forEach { (ox, oz) ->
            drawCone(ox, oz)
        }

        // 6. 车辆
        drawVehicle(v)
    }

    // ============== 地面 ==============
    private fun drawGround() {
        Matrix.setIdentityM(modelMatrix, 0)
        val m = FloatArray(16)
        Matrix.multiplyMM(m, 0, mvpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, m, 0)
        GLES30.glUniform4f(colorHandle, 0.15f, 0.25f, 0.12f, 1f)

        val w = 50f
        val verts = floatArrayOf(
            -w, 0f, -w,   w, 0f, -w,   w, 0f, w,
            -w, 0f, -w,   w, 0f, w,   -w, 0f, w
        )
        drawArrays(verts)
    }

    // ============== 多边形线框 ==============
    private fun drawPolygon(poly: List<Pair<Double, Double>>, color: FloatArray) {
        if (poly.size < 2) return
        Matrix.setIdentityM(modelMatrix, 0)
        val m = FloatArray(16)
        Matrix.multiplyMM(m, 0, mvpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, m, 0)
        GLES30.glUniform4fv(colorHandle, 1, color, 0)

        val verts = FloatArray(poly.size * 3)
        for (i in poly.indices) {
            verts[i * 3] = poly[i].first.toFloat()
            verts[i * 3 + 1] = 0.02f
            verts[i * 3 + 2] = poly[i].second.toFloat()
        }
        // LINE_LOOP
        val vao = createVAO(verts)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_LINE_LOOP, 0, poly.size)
        GLES30.glBindVertexArray(0)
    }

    // ============== 折线 ==============
    private fun drawPolyline(line: List<Pair<Double, Double>>, color: FloatArray) {
        if (line.size < 2) return
        Matrix.setIdentityM(modelMatrix, 0)
        val m = FloatArray(16)
        Matrix.multiplyMM(m, 0, mvpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, m, 0)
        GLES30.glUniform4fv(colorHandle, 1, color, 0)

        val verts = FloatArray(line.size * 3)
        for (i in line.indices) {
            verts[i * 3] = line[i].first.toFloat()
            verts[i * 3 + 1] = 0.04f
            verts[i * 3 + 2] = line[i].second.toFloat()
        }
        val vao = createVAO(verts)
        GLES30.glBindVertexArray(vao)
        GLES30.glLineWidth(3f)
        GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, line.size)
        GLES30.glBindVertexArray(0)
    }

    // ============== 车辆 ==============
    private fun drawVehicle(v: com.drivingsim.vehicle.Vehicle) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, v.posX.toFloat(), 0f, v.posZ.toFloat())
        Matrix.rotateM(modelMatrix, 0, Math.toDegrees(v.yaw).toFloat(), 0f, 1f, 0f)

        val m = FloatArray(16)
        Matrix.multiplyMM(m, 0, mvpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, m, 0)

        // 车身：4.5m长 x 1.8m宽 x 1.4m高，颜色白色
        val l = 2.25f; val w = 0.9f; val h = 1.4f; val b = 0.3f
        val bodyVerts = floatArrayOf(
            -w, b, -l,   w, b, -l,   w, b+h, -l,   -w, b+h, -l,  // 前
             w, b,  l,  -w, b,  l,  -w, b+h,  l,   w, b+h,  l,  // 后
            -w, b, -l,  -w, b,  l,  -w, b+h,  l,  -w, b+h, -l,  // 左
             w, b,  l,   w, b, -l,   w, b+h, -l,   w, b+h,  l,  // 右
            -w, b+h, -l,  w, b+h, -l,  w, b+h,  l,  -w, b+h,  l,  // 顶
        )
        val indices = shortArrayOf(
            0,1,2,0,2,3,  4,5,6,4,6,7,
            8,9,10,8,10,11,  12,13,14,12,14,15,
            16,17,18,16,18,19
        )
        drawColoredMesh(bodyVerts, indices, floatArrayOf(0.95f, 0.95f, 0.95f, 1f))

        // 车窗：深蓝
        val gx = 1.0f; val gh = 0.5f
        val glassVerts = floatArrayOf(
            -w+0.1f, b+h-gh, -gx,  w-0.1f, b+h-gh, -gx,  w-0.1f, b+h, -gx,  -w+0.1f, b+h, -gx,
             w-0.1f, b+h-gh,  gx, -w+0.1f, b+h-gh,  gx, -w+0.1f, b+h,  gx,  w-0.1f, b+h,  gx,
        )
        val glassIdx = shortArrayOf(0,1,2,0,2,3, 4,5,6,4,6,7)
        drawColoredMesh(glassVerts, glassIdx, floatArrayOf(0.2f, 0.4f, 0.7f, 0.6f))

        // 车轮（4个）
        val wheelPos = arrayOf(-w-0.15f to -1.5f, w+0.15f to -1.5f, -w-0.15f to 1.5f, w+0.15f to 1.5f)
        for ((wx, wz) in wheelPos) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, v.posX.toFloat(), 0f, v.posZ.toFloat())
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(v.yaw).toFloat(), 0f, 1f, 0f)
            Matrix.translateM(modelMatrix, 0, wx, 0.33f, wz)
            val wm = FloatArray(16)
            Matrix.multiplyMM(wm, 0, mvpMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(mvpHandle, 1, false, wm, 0)
            drawWheel()
        }
    }

    private fun drawWheel() {
        GLES30.glUniform4f(colorHandle, 0.1f, 0.1f, 0.1f, 1f)
        val r = 0.33f; val tw = 0.2f; val segs = 12
        val verts = FloatArray(segs * 6)
        for (i in 0 until segs) {
            val a1 = (i * 2 * Math.PI / segs).toFloat()
            val a2 = ((i + 1) * 2 * Math.PI / segs).toFloat()
            val x1 = r * Math.cos(a1.toDouble()).toFloat()
            val z1 = r * Math.sin(a1.toDouble()).toFloat()
            val x2 = r * Math.cos(a2.toDouble()).toFloat()
            val z2 = r * Math.sin(a2.toDouble()).toFloat()
            val base = i * 6
            verts[base] = x1; verts[base+1] = -tw; verts[base+2] = z1
            verts[base+3] = x2; verts[base+4] = -tw; verts[base+5] = z2
        }
        val vao = createVAO(verts)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, segs * 2)
        GLES30.glBindVertexArray(0)
    }

    // ============== 锥桶 ==============
    private fun drawCone(x: Double, z: Double) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x.toFloat(), 0f, z.toFloat())
        val m = FloatArray(16)
        Matrix.multiplyMM(m, 0, mvpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, m, 0)
        GLES30.glUniform4f(colorHandle, 1f, 0.4f, 0f, 1f)

        val verts = floatArrayOf(
            0f, 0.8f, 0f,
            -0.15f, 0f, -0.15f,  0.15f, 0f, -0.15f,  0.15f, 0f, 0.15f,  -0.15f, 0f, 0.15f
        )
        val idx = shortArrayOf(0,1,2, 0,2,3, 0,3,4, 0,4,1)
        drawColoredMesh(verts, idx, floatArrayOf(1f, 0.3f, 0f, 1f))
    }

    // ============== 工具方法 ==============
    private fun drawColoredMesh(verts: FloatArray, indices: ShortArray, color: FloatArray) {
        GLES30.glUniform4fv(colorHandle, 1, color, 0)
        val vao = createIndexedVAO(verts, indices)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indices.size, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun drawArrays(verts: FloatArray) {
        val vao = createVAO(verts)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, verts.size / 3)
        GLES30.glBindVertexArray(0)
    }

    private fun createVAO(verts: FloatArray): Int {
        val vaoArr = IntArray(1)
        val vboArr = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        GLES30.glGenBuffers(1, vboArr, 0)
        GLES30.glBindVertexArray(vaoArr[0])
        val buf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboArr[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glVertexAttribPointer(posHandle, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(posHandle)
        return vaoArr[0]
    }

    private fun createIndexedVAO(verts: FloatArray, indices: ShortArray): Int {
        val vaoArr = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        val vboArr = IntArray(2)
        GLES30.glGenBuffers(2, vboArr, 0)
        GLES30.glBindVertexArray(vaoArr[0])
        val vBuf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vBuf.put(verts).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboArr[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, vBuf, GLES30.GL_STATIC_DRAW)
        val iBuf = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        iBuf.put(indices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vboArr[1])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, iBuf, GLES30.GL_STATIC_DRAW)
        GLES30.glVertexAttribPointer(posHandle, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(posHandle)
        return vaoArr[0]
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compile error: $log")
        }
        return shader
    }
}