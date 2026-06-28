package com.drivingsim.game

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.drivingsim.game.scene.Scene
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GameRenderer {

    companion object {
        private const val TAG = "GameRenderer"
    }

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)
    private val mirrorProjMatrix = FloatArray(16)
    private val mirrorViewMatrix = FloatArray(16)

    private var program = 0
    private var mvpHandle = -1
    private var colorHandle = -1
    private var posHandle = -1
    private var texHandle = -1
    private var useTexHandle = -1
    private var ready = false

    // FBO for mirrors
    private var mirrorFBOs = intArrayOf(0, 0, 0)  // left, right, rearview
    private var mirrorTexs = intArrayOf(0, 0, 0)
    private var mirrorDepthBufs = intArrayOf(0, 0, 0)
    private var mirrorSize = 256
    private var mirrorsReady = false

    // Textured shader program
    private var texProgram = 0
    private var texMvpHandle = -1
    private var texSamplerHandle = -1
    private var texPosHandle = -1

    fun init() {
        Log.i(TAG, "init()")
        GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // Color shader
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
        if (vs == 0 || fs == 0) { Log.e(TAG, "Color shader fail"); return }

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) { Log.e(TAG, "Link color: "+GLES20.glGetProgramInfoLog(program)); GLES20.glDeleteProgram(program); program = 0 }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        if (program != 0) {
            mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
            colorHandle = GLES20.glGetUniformLocation(program, "uColor")
            posHandle = GLES20.glGetAttribLocation(program, "aPos")
        }

        // Textured shader (for mirror quads)
        val texVsSrc = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            attribute vec2 aTex;
            varying vec2 vTex;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
                vTex = aTex;
            }
        """.trimIndent()

        val texFsSrc = """
            precision mediump float;
            uniform sampler2D uTex;
            varying vec2 vTex;
            void main() {
                gl_FragColor = texture2D(uTex, vTex);
            }
        """.trimIndent()

        val tvs = loadShader(GLES20.GL_VERTEX_SHADER, texVsSrc)
        val tfs = loadShader(GLES20.GL_FRAGMENT_SHADER, texFsSrc)
        if (tvs == 0 || tfs == 0) { Log.e(TAG, "Tex shader fail"); return }

        texProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(texProgram, tvs)
        GLES20.glAttachShader(texProgram, tfs)
        GLES20.glLinkProgram(texProgram)
        GLES20.glGetProgramiv(texProgram, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) { Log.e(TAG, "Link tex: "+GLES20.glGetProgramInfoLog(texProgram)); GLES20.glDeleteProgram(texProgram); texProgram = 0 }
        GLES20.glDeleteShader(tvs)
        GLES20.glDeleteShader(tfs)

        if (texProgram != 0) {
            texMvpHandle = GLES20.glGetUniformLocation(texProgram, "uMVP")
            texSamplerHandle = GLES20.glGetUniformLocation(texProgram, "uTex")
            texPosHandle = GLES20.glGetAttribLocation(texProgram, "aPos")
        }

        setupMirrors()
        ready = true
    }

    private fun setupMirrors() {
        // Create FBO textures
        for (i in 0..2) {
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mirrorSize, mirrorSize, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            mirrorTexs[i] = tex[0]

            // Depth buffer
            val db = IntArray(1)
            GLES20.glGenRenderbuffers(1, db, 0)
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, db[0])
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, mirrorSize, mirrorSize)
            mirrorDepthBufs[i] = db[0]

            // FBO
            val fb = IntArray(1)
            GLES20.glGenFramebuffers(1, fb, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[0], 0)
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, db[0])
            mirrorFBOs[i] = fb[0]
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0)
        mirrorsReady = true
        Log.i(TAG, "Mirror FBOs ready")
    }

    private fun loadShader(type: Int, source: String): Int {
        val s = GLES20.glCreateShader(type)
        if (s == 0) return 0
        GLES20.glShaderSource(s, source)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { GLES20.glDeleteShader(s); return 0 }
        return s
    }

    fun resize(w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        Matrix.perspectiveM(projMatrix, 0, 70f, w.toFloat() / h.coerceAtLeast(1), 0.3f, 200f)
        Matrix.perspectiveM(mirrorProjMatrix, 0, 55f, 1f, 0.3f, 150f)
    }

    var cameraMode = 0
    val cameraModes = arrayOf("驾驶舱", "后方跟随")

    fun nextCamera(): String {
        cameraMode = (cameraMode + 1) % cameraModes.size
        return cameraModes[cameraMode]
    }

    // --- Main draw ---

    fun draw(world: GameWorld, scene: Scene) {
        if (!ready || program == 0) {
            GLES20.glClearColor(0.1f, 0.1f, 0.15f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }

        val v = world.vehicle
        val yaw = v.yaw; val px = v.posX.toFloat(); val pz = v.posZ.toFloat()
        val sinY = Math.sin(yaw).toFloat(); val cosY = Math.cos(yaw).toFloat()

        if (cameraMode == 0 && mirrorsReady) {
            // === Render mirror views ===
            for (mi in 0..2) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mirrorFBOs[mi])
                GLES20.glViewport(0, 0, mirrorSize, mirrorSize)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

                when (mi) {
                    0 -> { // Left mirror - looking left-back
                        val mx = px - cosY * 1.0f; val mz = pz + sinY * 1.0f
                        val tx = mx - sinY * 15f; val tz = mz - cosY * 15f
                        Matrix.setLookAtM(mirrorViewMatrix, 0, mx, 1.0f, mz, tx, 0.6f, tz, 0f, 1f, 0f)
                    }
                    1 -> { // Right mirror - looking right-back
                        val mx = px + cosY * 1.0f; val mz = pz - sinY * 1.0f
                        val tx = mx - sinY * 15f; val tz = mz - cosY * 15f
                        Matrix.setLookAtM(mirrorViewMatrix, 0, mx, 1.0f, mz, tx, 0.6f, tz, 0f, 1f, 0f)
                    }
                    2 -> { // Rearview mirror - looking back
                        val mx = px; val mz = pz
                        val tx = px - sinY * 20f; val tz = pz - cosY * 20f
                        Matrix.setLookAtM(mirrorViewMatrix, 0, mx, 1.4f, mz, tx, 1f, tz, 0f, 1f, 0f)
                    }
                }

                renderScene(world, scene, mirrorViewMatrix, mirrorProjMatrix, false, false)
            }

            // Back to default framebuffer
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }

        // === Main view ===
        GLES20.glViewport(0, 0, GLES20.GL_MAX_VIEWPORT_DIMS_IV?.let { it[0] } ?: 1080, GLES20.GL_MAX_VIEWPORT_DIMS_IV?.let { it[1] } ?: 1920)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (cameraMode == 0) {
            // Driver's seat view
            val eyeX = px - 0.35f * cosY  // left side (driver seat in China)
            val eyeZ = pz + 0.35f * sinY
            val lookX = eyeX + sinY * 20f
            val lookZ = eyeZ + cosY * 20f
            Matrix.setLookAtM(viewMatrix, 0, eyeX, 1.25f, eyeZ, lookX, 1.15f, lookZ, 0f, 1f, 0f)
        } else {
            // Follow cam
            val cx = (v.posX - 12.0 * Math.sin(yaw)).toFloat()
            val cz = (v.posZ - 12.0 * Math.cos(yaw)).toFloat()
            Matrix.setLookAtM(viewMatrix, 0, cx, 6f, cz, px, 0f, pz, 0f, 1f, 0f)
        }

        GLRestore.saveViewport()
        val vp = getCurrentViewport()
        GLES20.glViewport(0, 0, vp[0], vp[1])

        renderScene(world, scene, viewMatrix, projMatrix, true, cameraMode == 0)
    }

    private fun getCurrentViewport(): IntArray {
        val vp = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
        return vp
    }

    private var savedVp = IntArray(4)

    private fun saveViewport() {
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, savedVp, 0)
    }
    private fun restoreViewport() {
        GLES20.glViewport(savedVp[0], savedVp[1], savedVp[2], savedVp[3])
    }

    private fun renderScene(world: GameWorld, scene: Scene, vMat: FloatArray, pMat: FloatArray, drawCockpit: Boolean, cockpitMode: Boolean) {
        GLES20.glUseProgram(program)

        val v = world.vehicle
        val yaw = v.yaw; val px = v.posX.toFloat(); val pz = v.posZ.toFloat()
        val sinY = Math.sin(yaw).toFloat(); val cosY = Math.cos(yaw).toFloat()

        drawGround(vMat, pMat)
        drawPolygon(scene.getBoundaryPolygon(), 1f, 1f, 1f, 0.8f, vMat, pMat)
        drawPolyline(scene.getCenterLine(), 1f, 0.84f, 0f, 0.9f, vMat, pMat)
        for ((ox, oz) in scene.getObstacles()) drawCone(ox, oz, vMat, pMat)
        drawVehicle(v, vMat, pMat)

        if (drawCockpit && cockpitMode && mirrorsReady) {
            drawCockpitInterior(px, pz, sinY, cosY, vMat, pMat)
            drawMirrorQuads(px, pz, sinY, cosY, vMat, pMat)
        }
    }

    // --- Cockpit interior ---

    private fun drawCockpitInterior(px: Float, pz: Float, sinY: Float, cosY: Float, vMat: FloatArray, pMat: FloatArray) {
        // Dashboard (dark gray)
        setColor(0.15f, 0.15f, 0.15f, 1f)
        val dashW = 1.6f; val dashD = 0.4f; val dashH = 0.15f
        val dashOffZ = 0.2f  // in front of driver
        val dashVCenter = floatArrayOf(
            px + sinY * (dashOffZ + dashD), pz + cosY * (dashOffZ + dashD),
            px + sinY * dashOffZ, pz + cosY * dashOffZ,
            0.85f  // height
        )
        // Simple dashboard as a flat surface
        drawFlatRect(
            px + sinY * dashOffZ, 0.85f, pz + cosY * dashOffZ,
            dashW, dashD, dashH,
            sinY, cosY, vMat, pMat
        )

        // Steering wheel (circle approximation)
        setColor(0.2f, 0.2f, 0.2f, 1f)
        val swX = px + sinY * 0.5f; val swY = 1.0f; val swZ = pz + cosY * 0.5f
        drawSteeringWheel(swX, swY, swZ, sinY, cosY, vMat, pMat)

        // Hood (visible through windshield)
        setColor(0.75f, 0.10f, 0.08f, 1f)
        val hoodLen = 1.8f; val hoodW = 0.85f
        val hoodStart = 0.5f  // starts after windshield
        val hoodVerts = floatArrayOf(
            px + sinY * hoodStart - cosY * hoodW, 1.05f, pz + cosY * hoodStart + sinY * hoodW,
            px + sinY * hoodStart + cosY * hoodW, 1.05f, pz + cosY * hoodStart - sinY * hoodW,
            px + sinY * (hoodStart + hoodLen) - cosY * hoodW, 0.65f, pz + cosY * (hoodStart + hoodLen) + sinY * hoodW,
            px + sinY * (hoodStart + hoodLen) + cosY * hoodW, 0.65f, pz + cosY * (hoodStart + hoodLen) - sinY * hoodW
        )
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP(vMat, pMat)
        drawTriangles(floatArrayOf(
            hoodVerts[0], hoodVerts[1], hoodVerts[2], hoodVerts[3], hoodVerts[4], hoodVerts[5], hoodVerts[6], hoodVerts[7], hoodVerts[8],
            hoodVerts[3], hoodVerts[4], hoodVerts[5], hoodVerts[6], hoodVerts[7], hoodVerts[8], hoodVerts[9], hoodVerts[10], hoodVerts[11]
        ), 6)

        // A-pillars (left and right)
        setColor(0.12f, 0.12f, 0.12f, 1f)
        val apillarH = 1.3f; val apillarW = 0.08f
        val apBaseY = 1.15f
        for (side in intArrayOf(-1, 1)) {
            val baseX = px + sinY * 0.7f + side * cosY * 0.75f
            val baseZ = pz + cosY * 0.7f - side * sinY * 0.75f
            val topX = px + sinY * -0.3f + side * cosY * 0.7f
            val topZ = pz + cosY * -0.3f - side * sinY * 0.7f
            val av = floatArrayOf(
                baseX - cosY * apillarW, apBaseY, baseZ + sinY * apillarW,
                baseX + cosY * apillarW, apBaseY, baseZ - sinY * apillarW,
                topX - cosY * apillarW, apBaseY + apillarH, topZ + sinY * apillarW,
                topX + cosY * apillarW, apBaseY + apillarH, topZ - sinY * apillarW
            )
            Matrix.setIdentityM(modelMatrix, 0)
            combineMVP(vMat, pMat)
            drawTriangles(floatArrayOf(
                av[0], av[1], av[2], av[3], av[4], av[5], av[6], av[7], av[8],
                av[3], av[4], av[5], av[6], av[7], av[8], av[9], av[10], av[11]
            ), 6)
        }
    }

    private fun drawFlatRect(cx: Float, cy: Float, cz: Float, w: Float, d: Float, h: Float, sinY: Float, cosY: Float, vMat: FloatArray, pMat: FloatArray) {
        val hw = w / 2; val hd = d / 2
        val vv = floatArrayOf(
            cx - cosY * hw + sinY * hd, cy, cz + sinY * hw + cosY * hd,
            cx + cosY * hw + sinY * hd, cy, cz - sinY * hw + cosY * hd,
            cx + cosY * hw - sinY * hd, cy + h, cz - sinY * hw - cosY * hd,
            cx - cosY * hw - sinY * hd, cy + h, cz + sinY * hw - cosY * hd
        )
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP(vMat, pMat)
        drawTriangles(floatArrayOf(vv[0],vv[1],vv[2],vv[3],vv[4],vv[5],vv[6],vv[7],vv[8], vv[0],vv[1],vv[2],vv[6],vv[7],vv[8],vv[9],vv[10],vv[11]), 6)
    }

    private fun drawSteeringWheel(cx: Float, cy: Float, cz: Float, sinY: Float, cosY: Float, vMat: FloatArray, pMat: FloatArray) {
        val r = 0.2f; val segments = 12
        val verts = FloatArray(segments * 3)
        for (i in 0 until segments) {
            val angle = (i * 2.0 * Math.PI / segments).toFloat()
            verts[i * 3] = cx + cosY * r * Math.cos(angle.toDouble()).toFloat() + sinY * Math.sin(angle.toDouble()).toFloat() * 0.01f
            verts[i * 3 + 1] = cy + Math.sin(angle.toDouble()).toFloat() * r * 0.3f
            verts[i * 3 + 2] = cz - sinY * r * Math.cos(angle.toDouble()).toFloat() + cosY * Math.sin(angle.toDouble()).toFloat() * 0.01f
        }
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP(vMat, pMat)
        setColor(0.25f, 0.25f, 0.25f, 1f)
        val lineVerts = FloatArray(segments * 6)
        for (i in 0 until segments) {
            val j = (i + 1) % segments
            lineVerts[i * 6] = verts[i * 3]; lineVerts[i * 6 + 1] = verts[i * 3 + 1]; lineVerts[i * 6 + 2] = verts[i * 3 + 2]
            lineVerts[i * 6 + 3] = verts[j * 3]; lineVerts[i * 6 + 4] = verts[j * 3 + 1]; lineVerts[i * 6 + 5] = verts[j * 3 + 2]
        }
        val buf = ByteBuffer.allocateDirect(lineVerts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(lineVerts).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glLineWidth(3f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, segments * 2)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    // --- Mirror quads ---

    private fun drawMirrorQuads(px: Float, pz: Float, sinY: Float, cosY: Float, vMat: FloatArray, pMat: FloatArray) {
        if (texProgram == 0) return

        GLES20.glUseProgram(texProgram)

        // Left side mirror
        drawSingleMirror(0,
            px - cosY * 1.15f, 1.15f, pz + sinY * 1.15f,
            0.25f, 0.18f, sinY, cosY, vMat, pMat, true)

        // Right side mirror
        drawSingleMirror(1,
            px + cosY * 1.15f, 1.15f, pz - sinY * 1.15f,
            0.25f, 0.18f, sinY, cosY, vMat, pMat, false)

        // Rearview mirror (center top)
        drawSingleMirror(2,
            px + sinY * 0.5f, 1.55f, pz + cosY * 0.5f,
            0.3f, 0.12f, sinY, cosY, vMat, pMat, true)
    }

    private fun drawSingleMirror(texIdx: Int, cx: Float, cy: Float, cz: Float, w: Float, h: Float, sinY: Float, cosY: Float, vMat: FloatArray, pMat: FloatArray, facingBack: Boolean) {
        val hw = w / 2; val hh = h / 2

        // Mirror surface faces backwards (towards driver)
        val mx = cx; val my = cy; val mz = cz
        val texCoords = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        val verts = floatArrayOf(
            mx - sinY * hw, my - hh, mz - cosY * hw,
            mx + sinY * hw, my - hh, mz + cosY * hw,
            mx + sinY * hw, my + hh, mz + cosY * hw,
            mx - sinY * hw, my + hh, mz - cosY * hw
        )
        val indices = intArrayOf(0, 1, 2, 0, 2, 3)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(tempMatrix, 0, pMat, 0, vMat, 0)
        Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(texMvpHandle, 1, false, mvpMatrix, 0)

        val tv = FloatArray(indices.size * 3)
        val tc = FloatArray(indices.size * 2)
        for (i in indices.indices) {
            tv[i*3] = verts[indices[i]*3]; tv[i*3+1] = verts[indices[i]*3+1]; tv[i*3+2] = verts[indices[i]*3+2]
            tc[i*2] = texCoords[indices[i]*2]; tc[i*2+1] = texCoords[indices[i]*2+1]
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mirrorTexs[texIdx])
        GLES20.glUniform1i(texSamplerHandle, 0)

        val vbuf = ByteBuffer.allocateDirect(tv.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vbuf.put(tv).position(0)
        GLES20.glVertexAttribPointer(texPosHandle, 3, GLES20.GL_FLOAT, false, 0, vbuf)
        GLES20.glEnableVertexAttribArray(texPosHandle)

        val tbuf = ByteBuffer.allocateDirect(tc.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        tbuf.put(tc).position(0)
        val aTex = GLES20.glGetAttribLocation(texProgram, "aTex")
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, tbuf)
        GLES20.glEnableVertexAttribArray(aTex)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, indices.size)

        GLES20.glDisableVertexAttribArray(texPosHandle)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        // Mirror frame (border)
        GLES20.glUseProgram(program)
        setColor(0.1f, 0.1f, 0.1f, 1f)
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP(vMat, pMat)
        val frameVerts = floatArrayOf(
            mx - sinY * hw, my - hh, mz - cosY * hw,
            mx + sinY * hw, my - hh, mz + cosY * hw,
            mx + sinY * hw, my + hh, mz + cosY * hw,
            mx - sinY * hw, my + hh, mz - cosY * hw
        )
        val fbuf = ByteBuffer.allocateDirect(frameVerts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fbuf.put(frameVerts).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, fbuf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glLineWidth(2f)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 4)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    // --- MVP ---

    private fun combineMVP(vMat: FloatArray, pMat: FloatArray) {
        Matrix.multiplyMM(tempMatrix, 0, pMat, 0, vMat, 0)
        Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
    }

    private fun combineMVP() {
        // uses viewMatrix/projMatrix directly
        Matrix.multiplyMM(tempMatrix, 0, projMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
    }

    private fun setColor(r: Float, g: Float, b: Float, a: Float) {
        GLES20.glUniform4f(colorHandle, r, g, b, a)
    }

    private fun drawTriangles(verts: FloatArray, count: Int) {
        val buf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    // --- Scene elements (with explicit vMat/pMat) ---

    private fun drawGround(vMat: FloatArray, pMat: FloatArray) {
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP(vMat, pMat)
        setColor(0.47f, 0.53f, 0.43f, 1f)
        val s = 30f
        drawTriangles(floatArrayOf(-s, 0f, -s, s, 0f, -s, s, 0f, s, -s, 0f, -s, s, 0f, s, -s, 0f, s), 6)
    }

    private fun drawPolygon(poly: List<Pair<Double, Double>>, r: Float, g: Float, b: Float, a: Float, vMat: FloatArray, pMat: FloatArray) {
        if (poly.size < 2) return
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP(vMat, pMat)
        setColor(r, g, b, a)
        val verts = FloatArray(poly.size * 3)
        for (i in poly.indices) {
            verts[i*3] = poly[i].first.toFloat()
            verts[i*3+1] = 0.02f
            verts[i*3+2] = poly[i].second.toFloat()
        }
        val buf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, poly.size)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun drawPolyline(line: List<Pair<Double, Double>>, r: Float, g: Float, b: Float, a: Float, vMat: FloatArray, pMat: FloatArray) {
        if (line.size < 2) return
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP(vMat, pMat)
        setColor(r, g, b, a)
        val verts = FloatArray(line.size * 3)
        for (i in line.indices) {
            verts[i*3] = line[i].first.toFloat()
            verts[i*3+1] = 0.04f
            verts[i*3+2] = line[i].second.toFloat()
        }
        val buf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, line.size)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun drawVehicle(v: com.drivingsim.vehicle.Vehicle, vMat: FloatArray, pMat: FloatArray) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, v.posX.toFloat(), 0.3f, v.posZ.toFloat())
        Matrix.rotateM(modelMatrix, 0, Math.toDegrees(v.yaw).toFloat(), 0f, 1f, 0f)
        combineMVP(vMat, pMat)

        val bl = 2.2f; val bw = 0.85f; val bh = 0.65f
        val cl = 1.0f; val cw = 0.78f; val ch = 0.55f
        val wr = 0.32f; val ww = 0.2f
        val ih_box = intArrayOf(0,1,2,0,2,3, 4,5,6,4,6,7, 8,9,10,8,10,11, 12,13,14,12,14,15)

        // Body
        setColor(0.75f, 0.10f, 0.08f, 1f)
        val bv = floatArrayOf(
            -bw, 0f, -bl,  bw, 0f, -bl,  bw, bh, -bl,  -bw, bh, -bl,
             bw, 0f,  bl, -bw, 0f,  bl, -bw, bh,  bl,  bw, bh,  bl,
            -bw, 0f, -bl, -bw, 0f,  bl, -bw, bh,  bl, -bw, bh, -bl,
             bw, 0f,  bl,  bw, 0f, -bl,  bw, bh, -bl,  bw, bh,  bl
        )
        drawIndexedBox(bv, ih_box)

        // Body top
        setColor(0.82f, 0.12f, 0.08f, 1f)
        val bv_top = floatArrayOf(-bw, bh, -bl, bw, bh, -bl, bw, bh, bl, -bw, bh, bl)
        drawIndexedBox(bv_top, intArrayOf(0,1,2,0,2,3))

        // Cabin
        setColor(0.55f, 0.06f, 0.04f, 1f)
        val cOffZ = 0.15f
        val cv = floatArrayOf(
            -cw, bh, -cl/2+cOffZ,  cw, bh, -cl/2+cOffZ,  cw, bh+ch, -cl/2+cOffZ,  -cw, bh+ch, -cl/2+cOffZ,
             cw, bh,  cl/2+cOffZ, -cw, bh,  cl/2+cOffZ, -cw, bh+ch,  cl/2+cOffZ,  cw, bh+ch,  cl/2+cOffZ,
            -cw, bh, -cl/2+cOffZ, -cw, bh,  cl/2+cOffZ, -cw, bh+ch,  cl/2+cOffZ, -cw, bh+ch, -cl/2+cOffZ,
             cw, bh,  cl/2+cOffZ,  cw, bh, -cl/2+cOffZ,  cw, bh+ch, -cl/2+cOffZ,  cw, bh+ch,  cl/2+cOffZ
        )
        drawIndexedBox(cv, ih_box)

        // Cabin top
        setColor(0.62f, 0.08f, 0.04f, 1f)
        val cv_top = floatArrayOf(-cw, bh+ch, -cl/2+cOffZ, cw, bh+ch, -cl/2+cOffZ, cw, bh+ch, cl/2+cOffZ, -cw, bh+ch, cl/2+cOffZ)
        drawIndexedBox(cv_top, intArrayOf(0,1,2,0,2,3))

        // Wheels
        setColor(0.2f, 0.2f, 0.2f, 1f)
        val segs = 8
        val wv = FloatArray(segs*2*3)
        for (i in 0 until segs) {
            val a1 = (i * 2.0 * Math.PI / segs).toFloat()
            val a2 = ((i + 1) * 2.0 * Math.PI / segs).toFloat()
            val b = i * 6
            wv[b] = wr * Math.cos(a1.toDouble()).toFloat(); wv[b+1] = -ww; wv[b+2] = wr * Math.sin(a1.toDouble()).toFloat()
            wv[b+3] = wr * Math.cos(a2.toDouble()).toFloat(); wv[b+4] = -ww; wv[b+5] = wr * Math.sin(a2.toDouble()).toFloat()
        }
        for ((wx, wy, wz) in arrayOf(
            Triple(-bw-0.05f, 0.35f, -bl+0.55f), Triple(bw+0.05f, 0.35f, -bl+0.55f),
            Triple(-bw-0.05f, 0.35f,  bl-0.55f), Triple(bw+0.05f, 0.35f,  bl-0.55f)
        )) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, v.posX.toFloat(), 0f, v.posZ.toFloat())
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(v.yaw).toFloat(), 0f, 1f, 0f)
            Matrix.translateM(modelMatrix, 0, wx, wy, wz)
            combineMVP(vMat, pMat)
            drawTriangles(wv, segs*2)
        }
    }

    private fun drawIndexedBox(vv: FloatArray, ih: IntArray) {
        val tv = FloatArray(ih.size * 3)
        for (i in ih.indices) { tv[i*3] = vv[ih[i]*3]; tv[i*3+1] = vv[ih[i]*3+1]; tv[i*3+2] = vv[ih[i]*3+2] }
        drawTriangles(tv, ih.size)
    }

    private fun drawCone(x: Double, z: Double, vMat: FloatArray, pMat: FloatArray) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x.toFloat(), 0f, z.toFloat())
        combineMVP(vMat, pMat)
        setColor(1f, 0.5f, 0f, 1f)
        val vv = floatArrayOf(0f, 0.8f, 0f, -0.15f, 0f, -0.15f, 0.15f, 0f, -0.15f, 0.15f, 0f, 0.15f, -0.15f, 0f, 0.15f)
        val ih = shortArrayOf(0,1,2, 0,2,3, 0,3,4, 0,4,1)
        val tv = FloatArray(ih.size*3)
        for (i in ih.indices) { tv[i*3]=vv[ih[i]*3]; tv[i*3+1]=vv[ih[i]*3+1]; tv[i*3+2]=vv[ih[i]*3+2] }
        drawTriangles(tv, ih.size)
    }

    // GLRestore helper object
    object GLRestore {
        private var vp = IntArray(4)
        fun saveViewport() { GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0) }
        fun restoreViewport() { GLES20.glViewport(vp[0], vp[1], vp[2], vp[3]) }
    }
}
