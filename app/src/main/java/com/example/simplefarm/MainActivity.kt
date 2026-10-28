package com.example.simplefarm

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var glView: FarmGLView
    private lateinit var hud: TextView
    private lateinit var msg: TextView
    private val game = Game()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)

        glView = FarmGLView(this, FarmRenderer(game))
        root.addView(
            glView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        hud = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
            setPadding(32, 24, 32, 24)
        }
        root.addView(
            hud,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            )
        )

        msg = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 32)
            text = "Ketuk lahan: tanam (5 koin)  |  ketuk tanaman matang: panen (+12)\n" +
                "Geser: putar kamera  |  Cubit: zoom"
        }
        root.addView(
            msg,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )

        setContentView(root)

        game.listener = { coins, harvested, message ->
            runOnUiThread {
                hud.text = "Koin: $coins    Panen: $harvested"
                if (message != null) msg.text = message
            }
        }
        game.refresh()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
    }
}
