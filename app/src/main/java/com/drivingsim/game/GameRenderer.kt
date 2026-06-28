package com.drivingsim.game

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
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
    private var ready = false

    // FBO for mirrors (0=left, 1=right, 2=rearview)
    private var mirrorFBOs = intArrayOf(0, 0, 0)
    private var mirrorTexs = intArrayOf(0, 0, 0)
    private var mirrorDepthBufs = intArrayOf(0, 0, 0)
    private var mirrorSize = 256
    private var mirrorsReady = false

    // Textured shader for mirror quads
    private var texProgram = 0
    private var texMvpHandle = -1
    private var texSamplerHandle = -1
    private var texPosHandle = -1

    // Viewport save/restore
    private var savedViewport = intArrayOf(0, 0, 0, 0)
    private var screenWidth = 1080
    private var screenHeight = 1920

    fun init() {
        Log.i(TAG, "init() cockpit+FBO")
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
        if (linked[0] == 0) {
            Log.e(TAG, "Link color shader fail: " + GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            program = 0
        }
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
        if (linked[0] == 0) {
            Log.e(TAG, "Link tex shader fail: " + GLES20.glGetProgramInfoLog(texProgram))
            GLES20.glDeleteProgram(texProgram)
            texProgram = 0
        }
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
        for (i in 0..2) {
            // Texture
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mirrorSize, mirrorSize, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            mirrorTexs[i] = tex[0]

            // Depth renderbuffer
            val rbuf = IntArray(1)
            GLES20.glGenRenderbuffers(1, rbuf, 0)
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, rbuf[0])
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, mirrorSize, mirrorSize)
            mirrorDepthBufs[i] = rbuf[0]

            // FBO
            val fb = IntArray(1)
            GLES20.glGenFramebuffers(1, fb, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[0], 0)
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, rbuf[0])

            val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "FBO $i incomplete: $status")
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0)
        mirrorsReady = true
        Log.i(TAG, "FBO mirrors ready")
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
        screenWidth = w; screenHeight = h
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

    // ==================== MAIN DRAW ====================

    fun draw(world: GameWorld, scene: com.drivingsim.game.scene.Scene) {
        if (!ready || program == 0) {
            GLES20.glClearColor(0.1f, 0.1f, 0.15f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }

        val v = world.vehicle
        val yaw = v.yaw
        val px = v.posX.toFloat()
        val pz = v.posZ.toFloat()
        val sinY = Math.sin(yaw).toFloat()
        val cosY = Math.cos(yaw).toFloat()

        // Save current viewport
        val mainVp = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, mainVp, 0)

        // === Render mirror views (FBO) ===
        if (cameraMode == 0 && mirrorsReady) {
            for (mi in 0..2) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mirrorFBOs[mi])
                GLES20.glViewport(0, 0, mirrorSize, mirrorSize)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

                when (mi) {
                    0 -> { // Left mirror — look left-backward
                        val mx = px - cosY * 1.0f; val mz = pz + sinY * 1.0f
                        val tx = mx - sinY * 15f; val tz = mz - cosY * 15f
                        Matrix.setLookAtM(mirrorViewMatrix, 0, mx, 1.0f, mz, tx, 0.6f, tz, 0f, 1f, 0f)
                    }
                    1 -> { // Right mirror — look right-backward
                        val mx = px + cosY * 1.0f; val mz = pz - sinY * 1.0f
                        val tx = mx - sinY * 15f; val tz = mz - cosY * 15f
                        Matrix.setLookAtM(mirrorViewMatrix, 0, mx, 1.0f, mz, tx, 0.6f, tz, 0f, 1f, 0f)
                    }
                    2 -> { // Rearview mirror — look straight back
                        val tx = px - sinY * 20f; val tz = pz - cosY * 20f
                        Matrix.setLookAtM(mirrorViewMatrix, 0, px, 1.4f, pz, tx, 1f, tz, 0f, 1f, 0f)
                    }
                }
                renderMirrorScene(world, scene, mirrorViewMatrix, mirrorProjMatrix)
            }
        }

        // === Main view ===
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(mainVp[0], mainVp[1], mainVp[2], mainVp[3])
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (cameraMode == 0) {
            // Driver's eye — left side, ~1.25m high
            val eyeX = px - 0.35f * cosY
            val eyeZ = pz + 0.35f * sinY
            val lookX = eyeX + sinY * 20f
            val lookZ = eyeZ + cosY * 20f
            Matrix.setLookAtM(viewMatrix, 0, eyeX, 1.25f, eyeZ, lookX, 1.15f, lookZ, 0f, 1f, 0f)
        } else {
            // Follow camera
            val cx = (v.posX - 12.0 * Math.sin(yaw)).toFloat()
            val cz = (v.posZ - 12.0 * Math.cos(yaw)).toFloat()
            Matrix.setLookAtM(viewMatrix, 0, cx, 6f, cz, px, 0f, pz, 0f, 1f, 0f)
        }

        renderMainScene(world, scene, viewMatrix, projMatrix, cameraMode == 0, px, pz, sinY, cosY)
    }

    // ==================== MIRROR SCENE ====================

    private fun renderMirrorScene(world: GameWorld, scene: com.drivingsim.game.scene.Scene, vMat: FloatArray, pMat: FloatArray) {
        GLES20.glUseProgram(program)
        drawGround(vMat, pMat)
        drawPolygon(scene.getBoundaryPolygon(), 1f, 1f, 1f, 0.8f, vMat, pMat)
        drawPolyline(scene.getCenterLine(), 1f, 0.84f, 0f, 0.9f, vMat, pMat)
        for ((ox, oz) in scene.getObstacles()) drawCone(ox, oz, vMat, pMat)
        drawVehicle(world.vehicle, vMat, pMat)
    }

    // ==================== MAIN SCENE ====================

    private fun renderMainScene(world: GameWorld, scene: com.drivingsim.game.scene.Scene, vMat: FloatArray, pMat: FloatArray, cockpitMode: Boolean, px: Float, pz: Float, sinY: Float, cosY: Float) {
        GLES20.glUseProgram(program)
        drawGround(vMat, pMat)
        drawPolygon(scene.getBoundaryPolygon(), 1f, 1f, 1f, 0.8f, vMat, pMat)
        drawPolyline(scene.getCenterLine(), 1f, 0.84f, 0f, 0.9f, vMat, pMat)
        for ((ox, oz) in scene.getObstacles()) drawCone(ox, oz, vMat, pMat)
        drawVehicle(world.vehicle, vMat, pMat)

        if (cockpitMode) {
            drawCockpitInterior(px, pz, sinY, cosY, vMat, pMat)
            if (mirrorsReady) drawMirrorQuads(px, pz, sinY, cosY, vMat, pMat)
        }
    }

    // ==================== COCKPIT INTERIOR ====================

    private fun drawCockpitInterior(px: Float, pz: Float, sinY: Float, cosY: Float, vMat: FloatArray, pMat: FloatArray) {
        GLES20.glUseProgram(program)

        // --- Dashboard ---
        setColor(0.15f, 0.15f, 0.15f, 1f)
        val dashVerts = floatArrayOf(
            px + sinY * 0.6f - cosY * 0.8f, 0.88f, pz + cosY * 0.6f + sinY * 0.8f,
            px + sinY * 0.6f + cosY * 0.8f, 0.88f, pz + cosY * 0.6f - sinY * 0.8f,
            px + sinY * 0.2f - cosY * 0.8f, 0.88f, pz + cosY * 0.2f + sinY * 0.8f,
            px + sinY * 0.2f + cosY * 0.8f, 0.88f, pz + cosY * 0.2f - sinY * 0.8f
        )
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP(vMat, pMat)
        drawTriangles(floatArrayOf(
            dashVerts[0],dashVerts[1],dashVerts[2], dashVerts[3],dashVerts[4],dashVerts[5], dashVerts[6],dashVerts[7],dashVerts[8],
            dashVerts[3],dashVerts[4],dashVerts[5], dashVerts[6],dashVerts[7],dashVerts[8], dashVerts[9],dashVerts[10],dashVerts[11]
        ), 6)

        // Dashboard front face
        setColor(0.12f, 0.12f, 0.12f, 1f)
        val dfVerts = floatArrayOf(
            dashVerts[0], dashVerts[1], dashVerts[2],
            dashVerts[3], dashVerts[4], dashVerts[5],
            dashVerts[3], dashVerts[4]-0.10f, dashVerts[5],
            dashVerts[0], dashVerts[1]-0.10f, dashVerts[2]
        )
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP(vMat, pMat)
        drawTriangles(floatArrayOf(
            dfVerts[0],dfVerts[1],dfVerts[2], dfVerts[3],dfVerts[4],dfVerts[5], dfVerts[6],dfVerts[7],dfVerts[8],
            dfVerts[0],dfVerts[1],dfVerts[2], dfVerts[6],dfVerts[7],dfVerts[8], dfVerts[9],dfVerts[10],dfVerts[11]
        ), 6)

        // --- Steering wheel ---
        setColor(0.2f, 0.2f, 0.2f, 1f)
        val swX = px + sinY * 0.4f; val swY = 1.05f; val swZ = pz + cosY * 0.4f
        val sr = 0.18f; val segs = 16
        val ring = FloatArray(segs * 2 * 3)
        for (i in 0 until segs) {
            val a1 = (i * 2.0 * Math.PI / segs).toFloat()
            val a2 = ((i + 1) * 2.0 * Math.PI / segs).toFloat()
            val bx = i * 6
            // Ring tilted slightly
            ring[bx]   = swX + cosY * sr * Math.cos(a1.toDouble()).toFloat() + sinY * 0.02f
            ring[bx+1] = swY + Math.sin(a1.toDouble()).toFloat() * sr * 0.3f
            ring[bx+2] = swZ - sinY * sr * Math.cos(a1.toDouble()).toFloat() + cosY * 0.02f
            ring[bx+3] = swX + cosY * sr * Math.cos(a2.toDouble()).toFloat() + sinY * 0.02f
            ring[bx+4] = swY + Math.sin(a2.toDouble()).toFloat() * sr * 0.3f
            ring[bx+5] = swZ - sinY * sr * Math.cos(a2.toDouble()).toFloat() + cosY * 0.02f
        }
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP(vMat, pMat)
        val rbuf = ByteBuffer.allocateDirect(ring.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        rbuf.put(ring).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, rbuf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glLineWidth(3f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, segs * 2)
        GLES20.glDisableVertexAttribArray(posHandle)

        // Steering column
        setColor(0.25f, 0.25f, 0.25f, 1f)
        val colVerts = floatArrayOf(swX, swY, swZ, px + sinY * 0.2f, 0.88f, pz + cosY * 0.2f)
        val cb = ByteBuffer.allocateDirect(18*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        cb.put(colVerts).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, cb)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glLineWidth(5f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
        GLES20.glDisableVertexAttribArray(posHandle)

        // --- Engine hood (visible through windshield) ---
        setColor(0.75f, 0.10f, 0.08f, 1f)
        val hoodVerts = floatArrayOf(
            px + sinY * 0.6f - cosY * 0.75f, 1.05f, pz + cosY * 0.6f + sinY * 0.75f,
            px + sinY * 0.6f + cosY * 0.75f, 1.05f, pz + cosY * 0.6f - sinY * 0.75f,
            px + sinY * 2.0f - cosY * 0.75f, 0.65f, pz + cosY * 2.0f + sinY * 0.75f,
            px + sinY * 2.0f + cosY * 0.75f, 0.65f, pz + cosY * 2.0f - sinY * 0.75f
        )
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP(vMat, pMat)
        drawTriangles(floatArrayOf(
            hoodVerts[0],hoodVerts[1],hoodVerts[2], hoodVerts[3],hoodVerts[4],hoodVerts[5], hoodVerts[6],hoodVerts[7],hoodVerts[8],
            hoodVerts[3],hoodVerts[4],hoodVerts[5], hoodVerts[6],hoodVerts[7],hoodVerts[8], hoodVerts[9],hoodVerts[10],hoodVerts[11]
        ), 6)

        // --- A-pillars ---
        setColor(0.12f, 0.12f, 0.12f, 1f)
        val apBaseY = 1.15f; val apH = 1.0f; val apW = 0.06f
        for (side in intArrayOf(-1, 1)) {
            val bx = px + sinY * 0.55f + side * cosY * 0.72f
            val bz = pz + cosY * 0.55f - side * sinY * 0.72f
            val tx = px + sinY * -0.3f + side * cosY * 0.68f
            val tz = pz + cosY * -0.3f - side * sinY * 0.68f
            val av = floatArrayOf(
                bx - cosY * apW, apBaseY, bz + sinY * apW,
                bx + cosY * apW, apBaseY, bz - sinY * apW,
                tx - cosY * apW, apBaseY + apH, tz + sinY * apW,
                tx + cosY * apW, apBaseY + apH, tz - sinY * apW
            )
            Matrix.setIdentityM(modelMatrix, 0)
            combineMVP(vMat, pMat)
            drawTriangles(floatArrayOf(
                av[0],av[1],av[2], av[3],av[4],av[5], av[6],av[7],av[8],
                av[3],av[4],av[5], av[6],av[7],av[8], av[9],av[10],av[11]
            ), 6)
        }
    }

    // ==================== MIRROR QUADS ====================

    private fun drawMirrorQuads(px: Float, pz: Float, sinY: Float, cosY: Float, vMat: FloatArray, pMat: FloatArray) {
        if (texProgram == 0) return
        GLES20.glUseProgram(texProgram)

        // Left side mirror
        drawMirrorQuad(0, px - cosY * 1.15f, 1.15f, pz + sinY * 1.15f, 0.22f, 0.15f, sinY, cosY, vMat, pMat)

        // Right side mirror
        drawMirrorQuad(1, px + cosY * 1.15f, 1.15f, pz - sinY * 1.15f, 0.22f, 0.15f, sinY, cosY, vMat, pMat)

        // Rearview mirror (center top, inside)
        drawMirrorQuad(2, px + sinY * 0.45f, 1.5f, pz + cosY * 0.45f, 0.28f, 0.11f, sinY, cosY, vMat, pMat)
    }

    private fun drawMirrorQuad(texIdx: Int, cx: Float, cy: Float, cz: Float, w: Float, h: Float, sinY: Float, cosY: Float, vMat: FloatArray, pMat: FloatArray) {
        val hw = w / 2; val hh = h / 2

        // Mirror surface quad
        val verts = floatArrayOf(
            cx - sinY * hw - cosY * 0.02f, cy - hh, cz - cosY * hw + sinY * 0.02f,
            cx + sinY * hw - cosY * 0.02f, cy - hh, cz + cosY * hw + sinY * 0.02f,
            cx + sinY * hw - cosY * 0.02f, cy + hh, cz + cosY * hw + sinY * 0.02f,
            cx - sinY * hw - cosY * 0.02f, cy + hh, cz - cosY * hw + sinY * 0.02f
        )
        val texCoords = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        val indices = intArrayOf(0, 1, 2, 0, 2, 3)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(tempMatrix, 0, pMat, 0, vMat, 0)
        Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(texMvpHandle, 1, false, mvpMatrix, 0)

        // Bind mirror texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mirrorTexs[texIdx])
        GLES20.glUniform1i(texSamplerHandle, 0)

        val tv = FloatArray(indices.size * 3)
        val tc = FloatArray(indices.size * 2)
        for (i in indices.indices) {
            tv[i*3] = verts[indices[i]*3]; tv[i*3+1] = verts[indices[i]*3+1]; tv[i*3+2] = verts[indices[i]*3+2]
            tc[i*2] = texCoords[indices[i]*2]; tc[i*2+1] = texCoords[indices[i]*2+1]
        }

        val vbuf = ByteBuffer.allocateDirect(tv.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vbuf.put(tv).position(0)
        GLES20.glVertexAttribPointer(texPosHandle, 3, GLES20.GL_FLOAT, false, 0, vbuf)
        GLES20.glEnableVertexAttribArray(texPosHandle)

        val aTex = GLES20.glGetAttribLocation(texProgram, "aTex")
        val tbuf = ByteBuffer.allocateDirect(tc.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        tbuf.put(tc).position(0)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, tbuf)
        GLES20.glEnableVertexAttribArray(aTex)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, indices.size)

        GLES20.glDisableVertexAttribArray(texPosHandle)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        // Mirror frame border
        GLES20.glUseProgram(program)
        setColor(0.08f, 0.08f, 0.08f, 1f)
        Matrix.setIdentityM(modelMatrix, 0)
        combineMVP(vMat, pMat)
        val fbuf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fbuf.put(verts).position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, fbuf)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glLineWidth(2f)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 4)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    // ==================== MVP ====================

    private fun combineMVP(vMat: FloatArray, pMat: FloatArray) {
        Matrix.multiplyMM(tempMatrix, 0, pMat, 0, vMat, 0)
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

    // ==================== SCENE ELEMENTS ====================

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
        val idx6 = intArrayOf(0,1,2,0,2,3)

        // Body
        setColor(0.75f, 0.10f, 0.08f, 1f)
        val bv = floatArrayOf(
            -bw,0f,-bl, bw,0f,-bl, bw,bh,-bl, -bw,bh,-bl,
            bw,0f, bl, -bw,0f, bl, -bw,bh, bl, bw,bh, bl,
            -bw,0f,-bl, -bw,0f, bl, -bw,bh, bl, -bw,bh,-bl,
            bw,0f, bl, bw,0f,-bl, bw,bh,-bl, bw,bh, bl
        )
        drawIndexedBox(bv, idx6, intArrayOf(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23))

        // Cabin
        setColor(0.55f, 0.06f, 0.04f, 1f)
        val cOffZ = 0.15f
        val cv = floatArrayOf(
            -cw,bh,-cl/2+cOffZ, cw,bh,-cl/2+cOffZ, cw,bh+ch,-cl/2+cOffZ, -cw,bh+ch,-cl/2+cOffZ,
            cw,bh, cl/2+cOffZ, -cw,bh, cl/2+cOffZ, -cw,bh+ch, cl/2+cOffZ, cw,bh+ch, cl/2+cOffZ,
            -cw,bh,-cl/2+cOffZ, -cw,bh, cl/2+cOffZ, -cw,bh+ch, cl/2+cOffZ, -cw,bh+ch,-cl/2+cOffZ,
            cw,bh, cl/2+cOffZ, cw,bh,-cl/2+cOffZ, cw,bh+ch,-cl/2+cOffZ, cw,bh+ch, cl/2+cOffZ
        )
        drawIndexedBox(cv, idx6, intArrayOf(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23))

        // Wheels
        setColor(0.2f, 0.2f, 0.2f, 1f)
        val segs = 8
        val wv = FloatArray(segs * 2 * 3)
        for (i in 0 until segs) {
            val a1 = (i * 2.0 * Math.PI / segs).toFloat()
            val a2 = ((i + 1) * 2.0 * Math.PI / segs).toFloat()
            wv[i*6]   = wr * Math.cos(a1.toDouble()).toFloat()
            wv[i*6+1] = -ww
            wv[i*6+2] = wr * Math.sin(a1.toDouble()).toFloat()
            wv[i*6+3] = wr * Math.cos(a2.toDouble()).toFloat()
            wv[i*6+4] = -ww
            wv[i*6+5] = wr * Math.sin(a2.toDouble()).toFloat()
        }
        val savedModel = modelMatrix.clone()
        for ((wx, wy, wz) in arrayOf(
            Triple(-bw-0.05f, 0.35f, -bl+0.55f), Triple(bw+0.05f, 0.35f, -bl+0.55f),
            Triple(-bw-0.05f, 0.35f,  bl-0.55f), Triple(bw+0.05f, 0.35f,  bl-0.55f)
        )) {
            System.arraycopy(savedModel, 0, modelMatrix, 0, 16)
            Matrix.translateM(modelMatrix, 0, wx, wy, wz)
            combineMVP(vMat, pMat)
            drawTriangles(wv, segs * 2)
        }
    }

    private fun drawIndexedBox(vv: FloatArray, idx6: IntArray, allIdx: IntArray) {
        // Draw 6 faces as separate triangles
        val tv = FloatArray(allIdx.size * 3)
        for (i in allIdx.indices) {
            tv[i*3]   = vv[allIdx[i]*3]
            tv[i*3+1] = vv[allIdx[i]*3+1]
            tv[i*3+2] = vv[allIdx[i]*3+2]
        }
        drawTriangles(tv, allIdx.size)
    }

    private fun drawCone(x: Double, z: Double, vMat: FloatArray, pMat: FloatArray) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x.toFloat(), 0f, z.toFloat())
        combineMVP(vMat, pMat)
        setColor(1f, 0.5f, 0f, 1f)
        val vv = floatArrayOf(0f,0.8f,0f, -0.15f,0f,-0.15f, 0.15f,0f,-0.15f, 0.15f,0f,0.15f, -0.15f,0f,0.15f)
        val ih = shortArrayOf(0,1,2, 0,2,3, 0,3,4, 0,4,1)
        val tv = FloatArray(ih.size*3)
        for (i in ih.indices) { tv[i*3]=vv[ih[i]*3]; tv[i*3+1]=vv[ih[i]*3+1]; tv[i*3+2]=vv[ih[i]*3+2] }
        drawTriangles(tv, ih.size)
    }
}
