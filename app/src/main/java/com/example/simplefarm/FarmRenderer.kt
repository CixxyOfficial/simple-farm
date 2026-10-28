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
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Renderer OpenGL ES 2.0, sudut pandang orang pertama.
 * Semua objek digambar dari satu mesh kubus. Hanya tangan yang terlihat.
 */
class FarmRenderer(private val game: Game) : GLSurfaceView.Renderer {

    companion object {
        const val EYE = 1.6f
        const val REACH = 4.5f
        const val SPEED = 4.0f
        const val SOIL_TOP = 0.1f

        private val OFFS = floatArrayOf(-0.22f, -0.22f, 0.22f, -0.22f, -0.22f, 0.22f, 0.22f, 0.22f)

        private const val VERTEX_SRC = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            attribute vec4 aPos;
            attribute vec3 aNormal;
            varying float vLight;
            varying vec3 vWorld;
            void main() {
                gl_Position = uMVP * aPos;
                vec3 n = normalize((uModel * vec4(aNormal, 0.0)).xyz);
                vec3 l = normalize(vec3(0.4, 1.0, 0.6));
                vLight = 0.5 + 0.5 * max(dot(n, l), 0.0);
                vWorld = (uModel * aPos).xyz;
            }
        """

        private const val FRAGMENT_SRC = """
            precision mediump float;
            uniform vec4 uColor;
            uniform float uChecker;
            varying float vLight;
            varying vec3 vWorld;
            void main() {
                float c = mod(floor(vWorld.x) + floor(vWorld.z), 2.0);
                float k = 1.0 - 0.10 * c * uChecker;
                gl_FragColor = vec4(uColor.rgb * vLight * k, uColor.a);
            }
        """
    }

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val local = FloatArray(16)
    private val mvp = FloatArray(16)
    private val ident = FloatArray(16)
    private val handM = FloatArray(16)
    private val itemM = FloatArray(16)
    private var baseM = FloatArray(16)
    private var parent = FloatArray(16)
    private var checkerOn = 0f

    private var program = 0
    private var aPos = 0
    private var aNormal = 0
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uChecker = 0
    private lateinit var cube: FloatBuffer

    // pemain
    private var px = 0.5f
    private var pz = -5f
    private var yaw = 0f          // 0 = menghadap -Z (ke arah toko), positif = belok kiri
    private var pitch = -0.35f    // positif = melihat ke atas

    @Volatile
    private var moveX = 0f
    @Volatile
    private var moveY = 0f

    private var targetValid = false
    private var tileX = 0
    private var tileZ = 0

    private var swing = 0f
    private var walkTime = 0f
    private var bob = 0f
    private var lastNanos = 0L
    private var time = 0f

    init {
        Matrix.setIdentityM(ident, 0)
        parent = ident
    }

    // ---------- kontrol (dipanggil dari UI) ----------

    fun setMove(x: Float, y: Float) {
        moveX = x
        moveY = y
    }

    /** thread GL */
    fun look(dx: Float, dy: Float) {
        yaw -= dx * 0.0045f
        pitch = (pitch - dy * 0.0045f).coerceIn(-1.4f, 1.1f)
    }

    /** thread GL */
    fun useItem() {
        swing = 1f
        if (targetValid) {
            game.use(tileX, tileZ)
        }
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
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uChecker = GLES20.glGetUniformLocation(program, "uChecker")

        cube = buildCube()
        lastNanos = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        val width = if (w > 0) w else 1
        val height = if (h > 0) h else 1
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(proj, 0, 70f, aspect, 0.2f, 150f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        var dt = if (lastNanos == 0L) 0f else (now - lastNanos) / 1_000_000_000f
        lastNanos = now
        if (dt > 0.1f) dt = 0.1f
        time += dt
        swing = max(0f, swing - dt * 3f)

        movePlayer(dt)
        game.update(dt)
        game.setNearShop(hypot(px - 0.5f, pz + 11f) < 4.5f)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        updateCamera()
        computeTarget()
        bindCube()

        baseM = vp
        parent = ident
        drawScene()
        drawHighlight()
        drawHand()
        parent = ident
    }

    // ---------- pemain & kamera ----------

    private fun movePlayer(dt: Float) {
        val mx = moveX
        val my = moveY
        val amount = hypot(mx, my).coerceAtMost(1f)
        if (amount > 0.08f) {
            val fx = -sin(yaw)
            val fz = -cos(yaw)
            val rx = cos(yaw)
            val rz = -sin(yaw)
            val vx = (fx * my + rx * mx) * SPEED * dt
            val vz = (fz * my + rz * mx) * SPEED * dt
            tryMove(vx, 0f)
            tryMove(0f, vz)
            walkTime += dt * amount
            bob = sin(walkTime * 9f) * 0.012f
        } else {
            bob = 0f
        }
    }

    private fun tryMove(dx: Float, dz: Float) {
        val nx = (px + dx).coerceIn(-15.4f, 15.4f)
        val nz = (pz + dz).coerceIn(-15.4f, 15.4f)
        // area toko tidak bisa dilewati
        if (nx > -2.3f && nx < 3.3f && nz < -10.8f) return
        px = nx
        pz = nz
    }

    private fun updateCamera() {
        val cp = cos(pitch)
        val dx = -sin(yaw) * cp
        val dy = sin(pitch)
        val dz = -cos(yaw) * cp
        Matrix.setLookAtM(view, 0, px, EYE, pz, px + dx, EYE + dy, pz + dz, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
    }

    /** Petak yang dibidik crosshair di tengah layar. */
    private fun computeTarget() {
        targetValid = false
        val dy = sin(pitch)
        if (dy >= -0.01f) return
        val t = -EYE / dy
        if (t > REACH) return
        val cp = cos(pitch)
        val hx = px + (-sin(yaw) * cp) * t
        val hz = pz + (-cos(yaw) * cp) * t
        val tx = floor(hx).toInt()
        val tz = floor(hz).toInt()
        if (!game.inBounds(tx, tz)) return
        targetValid = true
        tileX = tx
        tileZ = tz
    }

    // ---------- isi dunia ----------

    private fun drawScene() {
        // tanah di luar pagar + tanah utama (kotak-kotak supaya terasa bergerak)
        drawBox(0f, -0.75f, 0f, 400f, 1f, 400f, 0.33f, 0.60f, 0.25f)
        checkerOn = 1f
        drawBox(0f, -0.5f, 0f, 32f, 1f, 32f, 0.42f, 0.74f, 0.31f)
        checkerOn = 0f

        // tanah cangkulan + tanaman
        for (i in 0 until Game.N * Game.N) {
            if (!game.soil[i]) continue
            val x = (i % Game.N) - Game.ORG + 0.5f
            val z = (i / Game.N) - Game.ORG + 0.5f
            if (game.wet[i] > 0f) {
                drawBox(x, SOIL_TOP / 2f, z, 0.96f, SOIL_TOP, 0.96f, 0.27f, 0.17f, 0.09f)
            } else {
                drawBox(x, SOIL_TOP / 2f, z, 0.96f, SOIL_TOP, 0.96f, 0.47f, 0.31f, 0.17f)
            }
            if (game.crop[i] >= 0) drawCrop(i, x, z)
        }

        drawFence()
        drawShop()
        drawTree(-13.5f, -3f)
        drawTree(-13.5f, 5f)
        drawTree(13.5f, -2f)
        drawTree(13.5f, 8f)
        drawTree(-10f, 14.5f)
        drawTree(10f, 14.5f)
        drawClouds()
    }

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun drawCrop(i: Int, x: Float, z: Float) {
        val g = game.growth[i]
        val t = game.crop[i]
        val col = Game.COLORS[t]
        val pulse = if (g >= 1f) 1f + 0.05f * sin(time * 5f + i) else 1f
        val cr = mix(0.25f, col[0], g)
        val cg = mix(0.70f, col[1], g)
        val cb = mix(0.20f, col[2], g)
        when (t) {
            0 -> {
                // gandum: 4 batang dengan bulir di ujung
                val h = (0.12f + 0.55f * g) * pulse
                val head = 0.08f + 0.20f * g
                for (k in 0 until 4) {
                    val ox = x + OFFS[k * 2]
                    val oz = z + OFFS[k * 2 + 1]
                    drawBox(ox, SOIL_TOP + h / 2f, oz, 0.06f, h, 0.06f, 0.30f, 0.60f, 0.18f)
                    drawBox(ox, SOIL_TOP + h + head / 2f, oz, 0.09f, head, 0.09f, cr, cg, cb)
                }
            }
            1 -> {
                // wortel: daun hijau, bagian oranye muncul saat besar
                val lh = 0.12f + 0.30f * g
                val lw = (0.18f + 0.30f * g) * pulse
                drawBox(x, SOIL_TOP + lh / 2f, z, lw, lh, lw, 0.22f, 0.62f, 0.20f)
                if (g > 0.4f) {
                    val s = 0.08f + 0.14f * g
                    drawBox(x, SOIL_TOP + 0.02f, z, s, 0.05f, s, cr, cg, cb)
                }
            }
            else -> {
                // labu: sulur rendah + labu yang membesar
                val vine = 0.35f + 0.5f * g
                drawBox(x, SOIL_TOP + 0.03f, z, vine, 0.06f, vine, 0.22f, 0.55f, 0.18f)
                val s = (0.10f + 0.40f * g) * pulse
                drawBox(x, SOIL_TOP + s / 2f, z, s, s, s, cr, cg, cb)
                drawBox(x, SOIL_TOP + s + 0.04f, z, 0.06f, 0.08f, 0.06f, 0.25f, 0.45f, 0.15f)
            }
        }
    }

    private fun drawFence() {
        val wr = 0.60f
        val wg = 0.42f
        val wb = 0.24f
        var k = -16
        while (k <= 16) {
            val p = k.toFloat()
            drawBox(p, 0.45f, 15.9f, 0.18f, 0.9f, 0.18f, wr, wg, wb)
            drawBox(p, 0.45f, -15.9f, 0.18f, 0.9f, 0.18f, wr, wg, wb)
            drawBox(15.9f, 0.45f, p, 0.18f, 0.9f, 0.18f, wr, wg, wb)
            drawBox(-15.9f, 0.45f, p, 0.18f, 0.9f, 0.18f, wr, wg, wb)
            k += 2
        }
        drawBox(0f, 0.7f, 15.9f, 32f, 0.1f, 0.1f, wr, wg, wb)
        drawBox(0f, 0.7f, -15.9f, 32f, 0.1f, 0.1f, wr, wg, wb)
        drawBox(15.9f, 0.7f, 0f, 0.1f, 0.1f, 32f, wr, wg, wb)
        drawBox(-15.9f, 0.7f, 0f, 0.1f, 0.1f, 32f, wr, wg, wb)
    }

    private fun drawShop() {
        // meja toko
        drawBox(0.5f, 0.55f, -11.8f, 5.2f, 1.1f, 0.9f, 0.55f, 0.38f, 0.20f)
        drawBox(0.5f, 1.12f, -11.8f, 5.4f, 0.08f, 1.1f, 0.72f, 0.54f, 0.32f)
        // dinding belakang dan samping
        drawBox(0.5f, 1.4f, -14.8f, 5.6f, 2.8f, 0.3f, 0.80f, 0.70f, 0.50f)
        drawBox(-2.1f, 1.4f, -13f, 0.3f, 2.8f, 4.2f, 0.80f, 0.70f, 0.50f)
        drawBox(3.1f, 1.4f, -13f, 0.3f, 2.8f, 4.2f, 0.80f, 0.70f, 0.50f)
        // tiang depan
        drawBox(-2.1f, 1.5f, -11f, 0.2f, 3f, 0.2f, 0.45f, 0.30f, 0.16f)
        drawBox(3.1f, 1.5f, -11f, 0.2f, 3f, 0.2f, 0.45f, 0.30f, 0.16f)
        // atap belang merah putih
        for (k in 0 until 6) {
            val cx = -2.4f + 0.483f + k * 0.967f
            if (k % 2 == 0) {
                drawBox(cx, 3.1f, -12.8f, 0.967f, 0.2f, 4.6f, 0.85f, 0.15f, 0.15f)
            } else {
                drawBox(cx, 3.1f, -12.8f, 0.967f, 0.2f, 4.6f, 0.97f, 0.97f, 0.97f)
            }
        }
        // papan toko
        drawBox(0.5f, 3.6f, -10.7f, 2.2f, 0.5f, 0.1f, 0.95f, 0.80f, 0.20f)
        drawBox(0.5f, 3.6f, -10.64f, 0.3f, 0.3f, 0.05f, 0.80f, 0.55f, 0.05f)
        // penjaga toko
        drawBox(0.5f, 0.25f, -13.2f, 0.4f, 0.5f, 0.25f, 0.20f, 0.20f, 0.30f)
        drawBox(0.5f, 0.85f, -13.2f, 0.5f, 0.7f, 0.3f, 0.20f, 0.45f, 0.80f)
        drawBox(0.5f, 1.4f, -13.2f, 0.34f, 0.34f, 0.34f, 0.93f, 0.74f, 0.58f)
        drawBox(0.5f, 1.62f, -13.2f, 0.42f, 0.08f, 0.42f, 0.45f, 0.30f, 0.16f)
    }

    private fun drawTree(x: Float, z: Float) {
        drawBox(x, 0.75f, z, 0.5f, 1.5f, 0.5f, 0.42f, 0.28f, 0.15f)
        drawBox(x, 2.4f, z, 2.0f, 1.8f, 2.0f, 0.18f, 0.50f, 0.20f)
        drawBox(x, 3.7f, z, 1.3f, 1.2f, 1.3f, 0.20f, 0.56f, 0.22f)
    }

    private fun drawClouds() {
        for (c in 0 until 4) {
            val cx = ((time * 0.8f + c * 17f) % 80f) - 40f
            val cz = -20f + c * 12f
            drawBox(cx, 14f + c, cz, 6f, 1f, 3f, 1f, 1f, 1f)
            drawBox(cx + 1.2f, 14.8f + c, cz, 3.6f, 1f, 2.4f, 1f, 1f, 1f)
        }
    }

    private fun drawHighlight() {
        if (!targetValid) return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        drawBox(tileX + 0.5f, 0.07f, tileZ + 0.5f, 1.02f, 0.14f, 1.02f, 1f, 1f, 1f, 0.35f)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    // ---------- tangan dan item (hanya ini yang terlihat dari pemain) ----------

    private fun drawHand() {
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
        baseM = proj

        val sw = sin(swing * Math.PI.toFloat())
        Matrix.setIdentityM(handM, 0)
        Matrix.translateM(handM, 0, 0.42f, -0.42f + bob, -0.62f)
        Matrix.rotateM(handM, 0, 12f - sw * 55f, 1f, 0f, 0f)
        Matrix.rotateM(handM, 0, -8f, 0f, 1f, 0f)

        // lengan baju + telapak tangan
        parent = handM
        drawBox(0f, 0f, 0.05f, 0.16f, 0.16f, 0.45f, 0.25f, 0.45f, 0.75f)
        drawBox(0f, 0f, -0.27f, 0.14f, 0.14f, 0.14f, 0.93f, 0.74f, 0.58f)

        // item yang dipegang
        System.arraycopy(handM, 0, itemM, 0, 16)
        Matrix.translateM(itemM, 0, 0f, 0.02f, -0.36f)
        Matrix.rotateM(itemM, 0, -30f, 1f, 0f, 0f)
        parent = itemM
        drawHeldItem()
        parent = ident
    }

    private fun drawHeldItem() {
        when (game.selected) {
            0 -> {
                // cangkul: gagang kayu + mata besi
                drawBox(0f, 0.3f, 0f, 0.05f, 0.7f, 0.05f, 0.50f, 0.34f, 0.18f)
                drawBox(0f, 0.64f, -0.1f, 0.07f, 0.05f, 0.24f, 0.62f, 0.64f, 0.68f)
            }
            1 -> {
                // penyiram tanaman
                drawBox(0f, 0.12f, 0f, 0.30f, 0.22f, 0.22f, 0.20f, 0.45f, 0.80f)
                drawBox(0f, 0.20f, -0.22f, 0.05f, 0.05f, 0.25f, 0.30f, 0.55f, 0.88f)
                drawBox(0f, 0.28f, 0.10f, 0.05f, 0.12f, 0.05f, 0.15f, 0.35f, 0.65f)
            }
            else -> {
                val col = Game.COLORS[game.slotType]
                when (game.slotKind) {
                    Game.SEED -> {
                        drawBox(0f, 0.15f, 0f, 0.20f, 0.26f, 0.10f, 0.80f, 0.70f, 0.45f)
                        drawBox(0f, 0.18f, 0.04f, 0.12f, 0.08f, 0.04f, col[0], col[1], col[2])
                    }
                    Game.CROP -> {
                        drawBox(0f, 0.12f, 0f, 0.20f, 0.20f, 0.20f, col[0], col[1], col[2])
                        drawBox(0f, 0.27f, 0f, 0.06f, 0.10f, 0.06f, 0.25f, 0.55f, 0.20f)
                    }
                    else -> {
                    }
                }
            }
        }
    }

    // ---------- helper GL ----------

    private fun drawBox(
        cx: Float, cy: Float, cz: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float,
        a: Float = 1f
    ) {
        Matrix.setIdentityM(local, 0)
        Matrix.translateM(local, 0, cx, cy, cz)
        Matrix.scaleM(local, 0, sx, sy, sz)
        Matrix.multiplyMM(model, 0, parent, 0, local, 0)
        Matrix.multiplyMM(mvp, 0, baseM, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES20.glUniform4f(uColor, r, g, b, a)
        GLES20.glUniform1f(uChecker, checkerOn)
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
