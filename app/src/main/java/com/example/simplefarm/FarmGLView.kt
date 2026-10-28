package com.example.simplefarm

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import kotlin.math.hypot

/**
 * View OpenGL layar penuh: geser = melihat sekeliling, ketuk = pakai item
 * pada petak yang dibidik crosshair. (Analog jalan ada di view terpisah.)
 */
class FarmGLView(context: Context, private val renderer: FarmRenderer) : GLSurfaceView(context) {

    private var activeId = -1
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var moved = false

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeId = e.getPointerId(0)
                downX = e.x
                downY = e.y
                lastX = e.x
                lastY = e.y
                downTime = e.eventTime
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = e.findPointerIndex(activeId)
                if (idx >= 0) {
                    val x = e.getX(idx)
                    val y = e.getY(idx)
                    if (hypot(x - downX, y - downY) > 24f) moved = true
                    if (moved) {
                        val dx = x - lastX
                        val dy = y - lastY
                        queueEvent { renderer.look(dx, dy) }
                    }
                    lastX = x
                    lastY = y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && e.eventTime - downTime < 400L) {
                    queueEvent { renderer.useItem() }
                    performClick()
                }
                activeId = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                activeId = -1
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
