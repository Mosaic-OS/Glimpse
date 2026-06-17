package org.lineageos.glimpse.crop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

class CropOverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val handlePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val dimPaint = Paint().apply {
        color = Color.BLACK
        alpha = 160
    }

    private var cropRect = RectF()
    private var validImageRect = RectF()

    private var aspectRatio = 1f
    private var isDragging = false
    private var dragHandle = DragHandle.NONE
    private var lastX = 0f
    private var lastY = 0f

    private val handleSize = 60f
    private val visualHandleRadius = 20f

    enum class DragHandle {
        NONE, CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        LEFT, TOP, RIGHT, BOTTOM
    }

    init {
        validImageRect.set(0f, 0f, 0f, 0f)
    }

    fun setBitmapRect(rect: RectF) {
        validImageRect.set(rect)
        initializeCropRect()
        invalidate()
    }

    private fun initializeCropRect() {
        if (validImageRect.width() <= 0 || validImageRect.height() <= 0) return

        val width = validImageRect.width()
        val height = validImageRect.height()

        val size = min(width, height) * 0.8f

        val initialW: Float
        val initialH: Float

        if (aspectRatio >= 1) {
            initialW = size
            initialH = size / aspectRatio
        } else {
            initialH = size
            initialW = size * aspectRatio
        }

        val left = validImageRect.centerX() - (initialW / 2)
        val top = validImageRect.centerY() - (initialH / 2)

        cropRect = RectF(left, top, left + initialW, top + initialH)
    }

    fun setAspectRatio(ratio: Float) {
        aspectRatio = ratio
        if (validImageRect.width() > 0) {
            initializeCropRect()
            invalidate()
        }
    }

    fun getCropRect(): RectF = cropRect

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (validImageRect.isEmpty) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        canvas.drawRect(0f, 0f, viewW, cropRect.top, dimPaint)
        canvas.drawRect(0f, cropRect.bottom, viewW, viewH, dimPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, viewW, cropRect.bottom, dimPaint)

        canvas.drawRect(cropRect, paint)

        drawHandle(canvas, cropRect.left, cropRect.top)
        drawHandle(canvas, cropRect.right, cropRect.top)
        drawHandle(canvas, cropRect.left, cropRect.bottom)
        drawHandle(canvas, cropRect.right, cropRect.bottom)

        drawHandle(canvas, cropRect.centerX(), cropRect.top)
        drawHandle(canvas, cropRect.centerX(), cropRect.bottom)
        drawHandle(canvas, cropRect.left, cropRect.centerY())
        drawHandle(canvas, cropRect.right, cropRect.centerY())
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, visualHandleRadius, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragHandle = getTouchedHandle(event.x, event.y)
                isDragging = dragHandle != DragHandle.NONE
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    updateCropRect(dx, dy)

                    lastX = event.x
                    lastY = event.y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                dragHandle = DragHandle.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getTouchedHandle(x: Float, y: Float): DragHandle {
        if (isNearPoint(x, y, cropRect.left, cropRect.top)) return DragHandle.TOP_LEFT
        if (isNearPoint(x, y, cropRect.right, cropRect.top)) return DragHandle.TOP_RIGHT
        if (isNearPoint(x, y, cropRect.left, cropRect.bottom)) return DragHandle.BOTTOM_LEFT
        if (isNearPoint(x, y, cropRect.right, cropRect.bottom)) return DragHandle.BOTTOM_RIGHT

        if (isNearPoint(x, y, cropRect.left, cropRect.centerY())) return DragHandle.LEFT
        if (isNearPoint(x, y, cropRect.right, cropRect.centerY())) return DragHandle.RIGHT
        if (isNearPoint(x, y, cropRect.centerX(), cropRect.top)) return DragHandle.TOP
        if (isNearPoint(x, y, cropRect.centerX(), cropRect.bottom)) return DragHandle.BOTTOM

        if (cropRect.contains(x, y)) return DragHandle.CENTER

        return DragHandle.NONE
    }

    private fun isNearPoint(x: Float, y: Float, px: Float, py: Float): Boolean {
        return abs(x - px) < handleSize && abs(y - py) < handleSize
    }

    private fun updateCropRect(dx: Float, dy: Float) {
        val minSize = 60f

        when (dragHandle) {
            DragHandle.CENTER -> {
                cropRect.offset(dx, dy)
                if (cropRect.left < validImageRect.left) cropRect.offset(validImageRect.left - cropRect.left, 0f)
                if (cropRect.top < validImageRect.top) cropRect.offset(0f, validImageRect.top - cropRect.top)
                if (cropRect.right > validImageRect.right) cropRect.offset(validImageRect.right - cropRect.right, 0f)
                if (cropRect.bottom > validImageRect.bottom) cropRect.offset(0f, validImageRect.bottom - cropRect.bottom)
            }

            DragHandle.TOP_LEFT -> {
                adjustCorner(dx, dy, isLeft = true, isTop = true, minSize = minSize)
            }
            DragHandle.TOP_RIGHT -> {
                adjustCorner(dx, dy, isLeft = false, isTop = true, minSize = minSize)
            }
            DragHandle.BOTTOM_LEFT -> {
                adjustCorner(dx, dy, isLeft = true, isTop = false, minSize = minSize)
            }
            DragHandle.BOTTOM_RIGHT -> {
                adjustCorner(dx, dy, isLeft = false, isTop = false, minSize = minSize)
            }

            DragHandle.LEFT -> {
                adjustEdge(dx, isLeftOrTop = true, isHorizontal = true, minSize = minSize)
            }
            DragHandle.RIGHT -> {
                adjustEdge(dx, isLeftOrTop = false, isHorizontal = true, minSize = minSize)
            }
            DragHandle.TOP -> {
                adjustEdge(dy, isLeftOrTop = true, isHorizontal = false, minSize = minSize)
            }
            DragHandle.BOTTOM -> {
                adjustEdge(dy, isLeftOrTop = false, isHorizontal = false, minSize = minSize)
            }

            else -> {}
        }
    }

    private fun adjustCorner(dx: Float, dy: Float, isLeft: Boolean, isTop: Boolean, minSize: Float) {
        val anchorX = if (isLeft) cropRect.right else cropRect.left
        val anchorY = if (isTop) cropRect.bottom else cropRect.top

        var newX = if (isLeft) cropRect.left + dx else cropRect.right + dx
        var newY = if (isTop) cropRect.top + dy else cropRect.bottom + dy

        newX = if (isLeft) {
            newX.coerceIn(validImageRect.left, anchorX - minSize)
        } else {
            newX.coerceIn(anchorX + minSize, validImageRect.right)
        }

        newY = if (isTop) {
            newY.coerceIn(validImageRect.top, anchorY - minSize)
        } else {
            newY.coerceIn(anchorY + minSize, validImageRect.bottom)
        }

        val width = abs(newX - anchorX)
        val height = abs(newY - anchorY)
        val currentAspect = width / height

        if (currentAspect > aspectRatio) {
            val correctedWidth = height * aspectRatio
            newX = if (isLeft) anchorX - correctedWidth else anchorX + correctedWidth
        } else {
            val correctedHeight = width / aspectRatio
            newY = if (isTop) anchorY - correctedHeight else anchorY + correctedHeight
        }

        cropRect.left = minOf(newX, anchorX).coerceAtLeast(validImageRect.left)
        cropRect.right = maxOf(newX, anchorX).coerceAtMost(validImageRect.right)
        cropRect.top = minOf(newY, anchorY).coerceAtLeast(validImageRect.top)
        cropRect.bottom = maxOf(newY, anchorY).coerceAtMost(validImageRect.bottom)
    }

    private fun adjustEdge(delta: Float, isLeftOrTop: Boolean, isHorizontal: Boolean, minSize: Float) {
        if (isHorizontal) {
            val anchorX = if (isLeftOrTop) cropRect.right else cropRect.left
            var newX = if (isLeftOrTop) cropRect.left + delta else cropRect.right + delta

            newX = if (isLeftOrTop) {
                newX.coerceIn(validImageRect.left, anchorX - minSize)
            } else {
                newX.coerceIn(anchorX + minSize, validImageRect.right)
            }

            val newWidth = abs(newX - anchorX)
            val newHeight = newWidth / aspectRatio
            val centerY = cropRect.centerY()

            cropRect.left = minOf(newX, anchorX)
            cropRect.right = maxOf(newX, anchorX)
            cropRect.top = (centerY - newHeight / 2).coerceAtLeast(validImageRect.top)
            cropRect.bottom = (centerY + newHeight / 2).coerceAtMost(validImageRect.bottom)
        } else {
            val anchorY = if (isLeftOrTop) cropRect.bottom else cropRect.top
            var newY = if (isLeftOrTop) cropRect.top + delta else cropRect.bottom + delta

            newY = if (isLeftOrTop) {
                newY.coerceIn(validImageRect.top, anchorY - minSize)
            } else {
                newY.coerceIn(anchorY + minSize, validImageRect.bottom)
            }

            val newHeight = abs(newY - anchorY)
            val newWidth = newHeight * aspectRatio
            val centerX = cropRect.centerX()

            cropRect.top = minOf(newY, anchorY)
            cropRect.bottom = maxOf(newY, anchorY)
            cropRect.left = (centerX - newWidth / 2).coerceAtLeast(validImageRect.left)
            cropRect.right = (centerX + newWidth / 2).coerceAtMost(validImageRect.right)
        }
    }
}