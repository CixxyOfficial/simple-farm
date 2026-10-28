package com.example.simplefarm

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val game = Game()
    private lateinit var renderer: FarmRenderer
    private lateinit var glView: FarmGLView
    private lateinit var coinsText: TextView
    private lateinit var msgText: TextView
    private lateinit var shopBtn: Button
    private lateinit var hotbar: HotbarView
    private lateinit var panelRoot: FrameLayout
    private lateinit var panelTitle: TextView
    private lateinit var panelList: LinearLayout

    private var panelMode = 0   // 0 = tutup, 1 = tas, 2 = toko

    private val clearMsg = Runnable { msgText.text = "" }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        renderer = FarmRenderer(game)
        glView = FarmGLView(this, renderer)

        val root = FrameLayout(this)
        root.isMotionEventSplittingEnabled = true
        root.addView(
            glView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // crosshair di tengah layar
        val cross = TextView(this).apply {
            text = "+"
            textSize = 30f
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        root.addView(cross, wrap(Gravity.CENTER))

        // koin (kiri atas)
        coinsText = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
        root.addView(coinsText, wrap(Gravity.TOP or Gravity.START, 16, 12, 0, 0))

        // pesan (tengah atas)
        msgText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
            gravity = Gravity.CENTER
        }
        root.addView(msgText, wrap(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0))

        // tombol Tas + Toko (kanan atas)
        val topRight = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bagBtn = makeButton("Tas") { openPanel(1) }
        shopBtn = makeButton("Toko") { openPanel(2) }
        shopBtn.visibility = View.GONE
        topRight.addView(bagBtn)
        topRight.addView(shopBtn)
        root.addView(topRight, wrap(Gravity.TOP or Gravity.END, 0, 8, 12, 0))

        // analog (kiri bawah)
        val joystick = JoystickView(this) { x, y -> renderer.setMove(x, y) }
        root.addView(joystick, wrap(Gravity.BOTTOM or Gravity.START, 28, 0, 0, 24))

        // hotbar (tengah bawah)
        hotbar = HotbarView(this, game) { slot ->
            glView.queueEvent { game.selectSlot(slot) }
        }
        root.addView(hotbar, wrap(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10))

        // panel tas / toko
        panelRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(170, 0, 0, 0))
            isClickable = true
            visibility = View.GONE
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(240, 40, 40, 40))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        panelTitle = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
        }
        panelList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(panelList) }
        val closeBtn = makeButton("Tutup") { closePanel() }
        box.addView(panelTitle)
        box.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        box.addView(closeBtn)
        val boxParams = FrameLayout.LayoutParams(dp(440), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        boxParams.setMargins(0, dp(16), 0, dp(16))
        panelRoot.addView(box, boxParams)
        root.addView(
            panelRoot,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        game.onChange = { runOnUiThread { refreshUi() } }
        game.onMessage = { m -> runOnUiThread { showMessage(m) } }

        setContentView(root)
        goImmersive()
        refreshUi()
        showMessage("Jalan ke toko di depan (analog kiri), beli bibit, lalu cangkul dan tanam!")
    }

    // ---------- helper UI ----------

    private fun wrap(gravity: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): FrameLayout.LayoutParams {
        val p = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            gravity
        )
        p.setMargins(dp(l), dp(t), dp(r), dp(b))
        return p
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(210, 60, 100, 60))
            setOnClickListener { onClick() }
        }
    }

    private fun showMessage(m: String) {
        msgText.text = m
        msgText.removeCallbacks(clearMsg)
        msgText.postDelayed(clearMsg, 3500L)
    }

    private fun refreshUi() {
        coinsText.text = "Koin: ${game.coins}"
        shopBtn.visibility = if (game.nearShop) View.VISIBLE else View.GONE
        hotbar.invalidate()
        if (panelMode != 0) rebuildPanel()
    }

    // ---------- panel tas & toko ----------

    private fun openPanel(mode: Int) {
        panelMode = mode
        panelRoot.visibility = View.VISIBLE
        renderer.setMove(0f, 0f)
        rebuildPanel()
    }

    private fun closePanel() {
        panelMode = 0
        panelRoot.visibility = View.GONE
    }

    private fun addHeader(t: String) {
        panelList.addView(TextView(this).apply {
            text = t
            textSize = 16f
            setTextColor(Color.YELLOW)
            setPadding(0, dp(10), 0, dp(4))
        })
    }

    private fun addInfo(t: String) {
        panelList.addView(TextView(this).apply {
            text = t
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(4))
        })
    }

    private fun addAction(t: String, onClick: () -> Unit) {
        panelList.addView(makeButton(t, onClick))
    }

    private fun rebuildPanel() {
        panelList.removeAllViews()
        if (panelMode == 2) {
            panelTitle.text = "Toko   (Koin: ${game.coins})"
            addHeader("Beli bibit")
            for (t in 0 until 3) {
                addAction("${Game.NAMES[t]}  -  ${Game.SEED_PRICE[t]} koin   (punya: ${game.seeds[t]})") {
                    glView.queueEvent { game.buySeed(t) }
                }
            }
            addHeader("Jual hasil panen")
            var any = false
            for (t in 0 until 3) {
                val n = game.produce[t]
                if (n > 0) {
                    any = true
                    addAction("Jual ${Game.NAMES[t]} x$n  =  +${n * Game.SELL_PRICE[t]} koin") {
                        glView.queueEvent { game.sellCrop(t) }
                    }
                }
            }
            if (any) {
                addAction("Jual semua") { glView.queueEvent { game.sellAll() } }
            } else {
                addInfo("Belum ada hasil panen.")
            }
        } else {
            panelTitle.text = "Tas"
            addInfo("Pilih item untuk dipasang di slot 3 hotbar.")
            var any = false
            addHeader("Bibit")
            for (t in 0 until 3) {
                val n = game.seeds[t]
                if (n > 0) {
                    any = true
                    addAction("Bibit ${Game.NAMES[t]} x$n  >  slot 3") {
                        glView.queueEvent { game.setSlot(Game.SEED, t) }
                    }
                }
            }
            addHeader("Hasil panen")
            for (t in 0 until 3) {
                val n = game.produce[t]
                if (n > 0) {
                    any = true
                    addAction("${Game.NAMES[t]} x$n  >  slot 3") {
                        glView.queueEvent { game.setSlot(Game.CROP, t) }
                    }
                }
            }
            if (!any) addInfo("Tas kosong. Beli bibit di toko.")
        }
    }

    // ---------- layar penuh ----------

    @Suppress("DEPRECATION")
    private fun goImmersive() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (panelMode != 0) closePanel() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }

    override fun onPause() {
        super.onPause()
        renderer.setMove(0f, 0f)
        glView.onPause()
    }
}
