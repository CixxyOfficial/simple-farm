package com.example.simplefarm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** Analog virtual untuk berjalan (kiri bawah). */
class JoystickView(
    context: Context,
    private val onMove: (Float, Float) -> Unit
) : View(context) {

    private val d = resources.displayMetrics.density
    private val size = (150f * d).toInt()

    private val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f * d
    }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private var kx = 0f
    private var ky = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = width / 2f - 4f * d
        canvas.drawCircle(cx, cy, r, outer)
        canvas.drawCircle(cx, cy, r, ring)
        canvas.drawCircle(cx + kx, cy + ky, r * 0.38f, knob)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = width / 2f * 0.6f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                var dx = e.x - cx
                var dy = e.y - cy
                val len = hypot(dx, dy)
                if (len > maxR) {
                    dx = dx / len * maxR
                    dy = dy / len * maxR
                }
                kx = dx
                ky = dy
                onMove(dx / maxR, -dy / maxR)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                kx = 0f
                ky = 0f
                onMove(0f, 0f)
                invalidate()
            }
        }
        return true
    }
}

/** Hotbar 3 slot (tengah bawah): cangkul, penyiram, dan 1 slot item dari tas. */
class HotbarView(
    context: Context,
    private val game: Game,
    private val onPick: (Int) -> Unit
) : View(context) {

    private val d = resources.displayMetrics.density
    private val cell = 62f * d
    private val gap = 6f * d

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 160, 160, 160)
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
    }
    private val borderSel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f * d
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val tiny = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textSize = 11f * d
    }
    private val countText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * d
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
        setShadowLayer(3f * d, 1f, 1f, Color.BLACK)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((3 * cell + 2 * gap + 6f * d).toInt(), (cell + 6f * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        for (i in 0 until 3) {
            val x = 3f * d + i * (cell + gap)
            val y = 3f * d
            val rect = RectF(x, y, x + cell, y + cell)
            canvas.drawRoundRect(rect, 6f * d, 6f * d, bg)
            canvas.drawRoundRect(rect, 6f * d, 6f * d, if (game.selected == i) borderSel else border)
            drawIcon(canvas, i, x, y)
            canvas.drawText("${i + 1}", x + 5f * d, y + 14f * d, tiny)
        }
    }

    private fun drawIcon(c: Canvas, slot: Int, x: Float, y: Float) {
        when (slot) {
            0 -> {
                // cangkul
                line.color = Color.rgb(140, 95, 50)
                c.drawLine(x + cell * 0.30f, y + cell * 0.82f, x + cell * 0.68f, y + cell * 0.28f, line)
                fill.color = Color.rgb(170, 175, 185)
                c.drawRect(x + cell * 0.52f, y + cell * 0.18f, x + cell * 0.86f, y + cell * 0.32f, fill)
            }
            1 -> {
                // penyiram
                fill.color = Color.rgb(60, 130, 215)
                c.drawRect(x + cell * 0.22f, y + cell * 0.42f, x + cell * 0.62f, y + cell * 0.78f, fill)
                line.color = Color.rgb(90, 160, 235)
                c.drawLine(x + cell * 0.62f, y + cell * 0.52f, x + cell * 0.86f, y + cell * 0.36f, line)
                line.color = Color.rgb(40, 100, 180)
                c.drawLine(x + cell * 0.28f, y + cell * 0.42f, x + cell * 0.44f, y + cell * 0.26f, line)
            }
            else -> {
                val kind = game.slotKind
                if (kind == Game.NONE) return
                val col = Game.COLORS[game.slotType]
                val color = Color.rgb(
                    (col[0] * 255f).toInt(),
                    (col[1] * 255f).toInt(),
                    (col[2] * 255f).toInt()
                )
                if (kind == Game.SEED) {
                    fill.color = Color.rgb(205, 180, 115)
                    c.drawRect(x + cell * 0.28f, y + cell * 0.25f, x + cell * 0.72f, y + cell * 0.80f, fill)
                    fill.color = color
                    c.drawCircle(x + cell * 0.5f, y + cell * 0.5f, cell * 0.12f, fill)
                } else {
                    fill.color = color
                    c.drawRoundRect(
                        RectF(x + cell * 0.25f, y + cell * 0.30f, x + cell * 0.75f, y + cell * 0.78f),
                        8f * d, 8f * d, fill
                    )
                    fill.color = Color.rgb(70, 140, 50)
                    c.drawRect(x + cell * 0.45f, y + cell * 0.16f, x + cell * 0.55f, y + cell * 0.32f, fill)
                }
                c.drawText("${game.slotCount()}", x + cell - 6f * d, y + cell - 6f * d, countText)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            val i = ((e.x - 3f * d) / (cell + gap)).toInt()
            if (i in 0..2) onPick(i)
        }
        return true
    }
}
