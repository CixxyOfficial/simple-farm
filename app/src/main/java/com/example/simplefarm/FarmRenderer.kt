package com.example.simplefarm

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Renderer OpenGL ES 2.0: semua objek digambar dari satu mesh kubus. */
class FarmRenderer(private val game: Game) : GLSurfaceView.Renderer {

    companion object {
        const val SOIL_TOP = 0.2f
        private val OFFS = floatArrayOf(-0.4f, -0.4f, 0.4f, -0.4f, -0.4f, 0.4f, 0.4f, 0.4f)

        private const val VERTEX_SRC = """
            uniform mat4 uMVP;
            attribute vec4 aPos;
            attribute vec3 aNormal;
            varying float vLight;
            void main() {
                gl_Position = uMVP * aPos;
                vec3 n = normalize(aNormal);
                vec3 l = normalize(vec3(0.4, 1.0, 0.6));
                vLight = 0.45 + 0.55 * max(dot(n, l), 0.0);
            }
        """

        private const val FRAGMENT_SRC = """
            precision mediump float;
            uniform vec4 uColor;
            varying float vLight;
            void main() {
                gl_FragColor = vec4(uColor.rgb * vLight, uColor.a);
            }
        """
    }

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val inv = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val tmpIn = FloatArray(4)
    private val tmpNear = FloatArray(4)
    private val tmpFar = FloatArray(4)

    private var program = 0
    private var aPos = 0
    private var aNormal = 0
    private var uMvp = 0
    private var uColor = 0
    private lateinit var cube: FloatBuffer

    private var width = 1
    private var height = 1
    private var fit = 1f

    private var yaw = 0.5f
    private var dist = 13f

    private var lastNanos = 0L
    private var time = 0f

    // ---------- kontrol dari sentuhan (dipanggil di thread GL) ----------

    fun rotate(dx: Float) {
        yaw -= dx * 0.008f
    }

    fun zoom(factor: Float) {
        dist = (dist / factor).coerceIn(7f, 22f)
    }

    fun tap(px: Float, py: Float) {
        updateCamera()
        if (!Matrix.invertM(inv, 0, vp, 0)) return
        val nx = 2f * px / width - 1f
        val ny = 1f - 2f * py / height
        unproject(nx, ny, -1f, tmpNear)
        unproject(nx, ny, 1f, tmpFar)
        val dy = tmpFar[1] - tmpNear[1]
        if (abs(dy) < 1e-6f) return
        val t = (SOIL_TOP - tmpNear[1]) / dy
        if (t < 0f) return
        val hx = tmpNear[0] + t * (tmpFar[0] - tmpNear[0])
        val hz = tmpNear[2] + t * (tmpFar[2] - tmpNear[2])
        game.tapWorld(hx, hz)
    }

    private fun unproject(nx: Float, ny: Float, nz: Float, out: FloatArray) {
        tmpIn[0] = nx
        tmpIn[1] = ny
        tmpIn[2] = nz
        tmpIn[3] = 1f
        Matrix.multiplyMV(out, 0, inv, 0, tmpIn, 0)
        val w = out[3]
        out[0] /= w
        out[1] /= w
        out[2] /= w
    }

    // ---------- siklus GLSurfaceView ----------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.55f, 0.78f, 0.95f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SRC)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SRC)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)

        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        uMvp = GLES20.glGetUniformLocation(program, "uMVP")
        uColor = GLES20.glGetUniformLocation(program, "uColor")

        cube = buildCube()
        lastNanos = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = if (w > 0) w else 1
        height = if (h > 0) h else 1
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(proj, 0, 45f, aspect, 0.1f, 100f)
        // kalau layar sempit (portrait) mundurkan kamera supaya kebun tetap muat
        fit = if (aspect < 1.6f) 1.6f / aspect else 1f
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        var dt = if (lastNanos == 0L) 0f else (now - lastNanos) / 1_000_000_000f
        lastNanos = now
        if (dt > 0.1f) dt = 0.1f
        time += dt
        game.update(dt)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        updateCamera()
        bindCube()
        drawScene()
    }

    private fun updateCamera() {
        val pitch = Math.toRadians(52.0).toFloat()
        val d = dist * fit
        val ex = d * cos(pitch) * sin(yaw)
        val ey = d * sin(pitch)
        val ez = d * cos(pitch) * cos(yaw)
        Matrix.setLookAtM(view, 0, ex, ey, ez, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
    }

    // ---------- isi dunia ----------

    private fun drawScene() {
        // tanah rumput
        drawBox(0f, -0.5f, 0f, 40f, 1f, 40f, 0.40f, 0.72f, 0.30f)

        // lahan + tanaman
        for (i in 0 until game.count) {
            val x = game.plotX(i)
            val z = game.plotZ(i)
            val planted = game.state[i] != Game.EMPTY
            if (planted) {
                drawBox(x, SOIL_TOP / 2f, z, 1.7f, SOIL_TOP, 1.7f, 0.33f, 0.21f, 0.11f)
                drawCrop(i)
            } else {
                drawBox(x, SOIL_TOP / 2f, z, 1.7f, SOIL_TOP, 1.7f, 0.47f, 0.31f, 0.17f)
            }
        }

        drawFence()
        drawBarn()
        drawTree(7f, 5f)
        drawTree(8.5f, -3f)
        drawClouds()
    }

    private fun drawCrop(i: Int) {
        val x = game.plotX(i)
        val z = game.plotZ(i)
        val g = game.growth[i]
        val ripe = game.state[i] == Game.RIPE
        val pulse = if (ripe) 1f + 0.08f * sin(time * 5f + i) else 1f
        val h = (0.15f + 0.85f * g) * pulse
        val head = (0.18f + 0.32f * g) * pulse
        val cr = 0.25f + 0.70f * g
        val cg = 0.70f + 0.02f * g
        val cb = 0.20f - 0.10f * g
        for (k in 0 until 4) {
            val ox = x + OFFS[k * 2]
            val oz = z + OFFS[k * 2 + 1]
            drawBox(ox, SOIL_TOP + h / 2f, oz, 0.1f, h, 0.1f, 0.20f, 0.55f, 0.15f)
            drawBox(ox, SOIL_TOP + h + head / 2f, oz, head, head, head, cr, cg, cb)
        }
    }

    private fun drawFence() {
        val wood = floatArrayOf(0.60f, 0.42f, 0.24f)
        for (k in -5..5) {
            val p = k.toFloat()
            drawBox(p, 0.4f, 5f, 0.15f, 0.8f, 0.15f, wood[0], wood[1], wood[2])
            drawBox(p, 0.4f, -5f, 0.15f, 0.8f, 0.15f, wood[0], wood[1], wood[2])
            drawBox(5f, 0.4f, p, 0.15f, 0.8f, 0.15f, wood[0], wood[1], wood[2])
            drawBox(-5f, 0.4f, p, 0.15f, 0.8f, 0.15f, wood[0], wood[1], wood[2])
        }
        drawBox(0f, 0.6f, 5f, 10.4f, 0.1f, 0.1f, wood[0], wood[1], wood[2])
        drawBox(0f, 0.6f, -5f, 10.4f, 0.1f, 0.1f, wood[0], wood[1], wood[2])
        drawBox(5f, 0.6f, 0f, 0.1f, 0.1f, 10.4f, wood[0], wood[1], wood[2])
        drawBox(-5f, 0.6f, 0f, 0.1f, 0.1f, 10.4f, wood[0], wood[1], wood[2])
    }

    private fun drawBarn() {
        drawBox(-8f, 1f, -2f, 3f, 2f, 3f, 0.75f, 0.15f, 0.12f)
        drawBox(-8f, 2.2f, -2f, 3.4f, 0.4f, 3.4f, 0.35f, 0.12f, 0.10f)
        drawBox(-8f, 2.65f, -2f, 2.0f, 0.5f, 3.4f, 0.35f, 0.12f, 0.10f)
        drawBox(-6.45f, 0.7f, -2f, 0.1f, 1.4f, 1.0f, 0.95f, 0.90f, 0.80f)
    }

    private fun drawTree(x: Float, z: Float) {
        drawBox(x, 0.75f, z, 0.5f, 1.5f, 0.5f, 0.42f, 0.28f, 0.15f)
        drawBox(x, 2.4f, z, 2.0f, 1.8f, 2.0f, 0.18f, 0.50f, 0.20f)
        drawBox(x, 3.7f, z, 1.3f, 1.2f, 1.3f, 0.20f, 0.56f, 0.22f)
    }

    private fun drawClouds() {
        for (c in 0 until 3) {
            val cx = ((time * 0.6f + c * 13f) % 40f) - 20f
            val cz = -8f + c * 5f
            drawBox(cx, 9f + c, cz, 4f, 0.8f, 2f, 1f, 1f, 1f)
            drawBox(cx + 0.8f, 9.6f + c, cz, 2.4f, 0.8f, 1.6f, 1f, 1f, 1f)
        }
    }

    // ---------- helper GL ----------

    private fun drawBox(
        cx: Float, cy: Float, cz: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, cx, cy, cz)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform4f(uColor, r, g, b, 1f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36)
    }

    private fun bindCube() {
        cube.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, cube)
        GLES20.glEnableVertexAttribArray(aPos)
        cube.position(3)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 24, cube)
        GLES20.glEnableVertexAttribArray(aNormal)
    }

    private fun compile(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            throw RuntimeException("Shader error: " + GLES20.glGetShaderInfoLog(shader))
        }
        return shader
    }

    /** Kubus 1x1x1 berpusat di origin: 36 vertex, tiap vertex = posisi(3) + normal(3). */
    private fun buildCube(): FloatBuffer {
        // tiap sisi: normal (3) lalu 4 sudut (3 x 4), berlawanan arah jarum jam dilihat dari luar
        val faces = arrayOf(
            floatArrayOf(0f, 0f, 1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, 1f),
            floatArrayOf(0f, 0f, -1f, 1f, -1f, -1f, -1f, -1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f),
            floatArrayOf(1f, 0f, 0f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f),
            floatArrayOf(-1f, 0f, 0f, -1f, -1f, -1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, -1f),
            floatArrayOf(0f, 1f, 0f, -1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, -1f, -1f, 1f, -1f),
            floatArrayOf(0f, -1f, 0f, -1f, -1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f, -1f, -1f, 1f)
        )
        val order = intArrayOf(0, 1, 2, 0, 2, 3)
        val data = ArrayList<Float>()
        for (f in faces) {
            for (c in order) {
                data.add(f[3 + c * 3] * 0.5f)
                data.add(f[4 + c * 3] * 0.5f)
                data.add(f[5 + c * 3] * 0.5f)
                data.add(f[0])
                data.add(f[1])
                data.add(f[2])
            }
        }
        val arr = data.toFloatArray()
        val fb = ByteBuffer.allocateDirect(arr.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        fb.put(arr)
        fb.position(0)
        return fb
    }
}
