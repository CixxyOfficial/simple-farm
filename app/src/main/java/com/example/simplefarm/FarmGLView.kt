package com.example.simplefarm

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.hypot

/** View OpenGL: ketuk = tanam/panen, geser = putar kamera, cubit = zoom. */
class FarmGLView(context: Context, private val renderer: FarmRenderer) : GLSurfaceView(context) {

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val f = detector.scaleFactor
                queueEvent { renderer.zoom(f) }
                return true
            }
        }
    )

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                lastX = e.x
                dragging = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // dua jari = cubit, jangan dianggap ketukan
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount == 1) {
                    if (!dragging && hypot(e.x - downX, e.y - downY) > 24f) {
                        dragging = true
                    }
                    if (dragging) {
                        val dx = e.x - lastX
                        queueEvent { renderer.rotate(dx) }
                    }
                }
                lastX = e.x
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    val x = e.x
                    val y = e.y
                    queueEvent { renderer.tap(x, y) }
                    performClick()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
