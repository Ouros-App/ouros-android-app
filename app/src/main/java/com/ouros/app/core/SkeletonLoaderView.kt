package com.ouros.app.core

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class SkeletonLoaderView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyle: Int = 0
) : View(context, attributes, defaultStyle) {

    enum class Mode {
        DASHBOARD,
        GOALS
    }

    var mode: Mode = Mode.DASHBOARD
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderMatrix = Matrix()
    private var shimmerShader: LinearGradient? = null
    private var shimmerPosition = -1f

    private val animator = ValueAnimator.ofFloat(-1f, 1f).apply {
        duration = 1_200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            shimmerPosition = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0) return

        shimmerShader = LinearGradient(
            -width.toFloat(),
            0f,
            width.toFloat(),
            0f,
            intArrayOf(BASE_COLOR, HIGHLIGHT_COLOR, BASE_COLOR),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = shimmerShader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shader = shimmerShader ?: return

        shaderMatrix.setTranslate(shimmerPosition * width * 1.5f, 0f)
        shader.setLocalMatrix(shaderMatrix)

        when (mode) {
            Mode.DASHBOARD -> drawDashboard(canvas)
            Mode.GOALS -> drawGoals(canvas)
        }
    }

    private fun drawDashboard(canvas: Canvas) {
        val horizontalPadding = dp(20f)
        val availableWidth = width - horizontalPadding * 2f

        drawDashboardCards(
            canvas = canvas,
            top = dp(20f),
            horizontalPadding = horizontalPadding,
            availableWidth = availableWidth
        )

        var sectionTop = dp(145f)
        repeat(3) {
            drawDashboardChart(
                canvas = canvas,
                top = sectionTop,
                horizontalPadding = horizontalPadding,
                availableWidth = availableWidth
            )
            sectionTop += dp(230f)
        }
    }

    private fun drawDashboardCards(
        canvas: Canvas,
        top: Float,
        horizontalPadding: Float,
        availableWidth: Float
    ) {
        val gap = dp(6f)
        val cardWidth = (availableWidth - gap * 2f) / 3f

        repeat(3) { index ->
            val left = horizontalPadding + index * (cardWidth + gap)
            canvas.drawRoundRect(
                left,
                top,
                left + cardWidth,
                top + dp(90f),
                dp(12f),
                dp(12f),
                paint
            )
        }
    }

    private fun drawDashboardChart(
        canvas: Canvas,
        top: Float,
        horizontalPadding: Float,
        availableWidth: Float
    ) {
        canvas.drawRoundRect(
            horizontalPadding,
            top,
            horizontalPadding + dp(95f),
            top + dp(20f),
            dp(6f),
            dp(6f),
            paint
        )
        canvas.drawRoundRect(
            horizontalPadding,
            top + dp(34f),
            horizontalPadding + availableWidth,
            top + dp(195f),
            dp(12f),
            dp(12f),
            paint
        )
    }

    private fun drawGoals(canvas: Canvas) {
        val horizontalPadding = dp(20f)
        val availableWidth = width - horizontalPadding * 2f
        val circleCenterX = horizontalPadding + dp(56f)
        val circleCenterY = dp(72f)

        canvas.drawCircle(
            circleCenterX,
            circleCenterY,
            dp(52f),
            paint
        )

        val informationLeft = horizontalPadding + dp(135f)
        val informationRight = horizontalPadding + availableWidth

        canvas.drawRoundRect(
            informationLeft,
            dp(48f),
            informationRight,
            dp(66f),
            dp(6f),
            dp(6f),
            paint
        )

        canvas.drawRoundRect(
            informationLeft,
            dp(78f),
            informationRight,
            dp(88f),
            dp(5f),
            dp(5f),
            paint
        )

        var sectionTop = dp(160f)

        sectionTop = drawGoalsSection(
            canvas = canvas,
            top = sectionTop,
            horizontalPadding = horizontalPadding,
            availableWidth = availableWidth,
            itemCount = 6,
            showAddButton = true
        )

        drawGoalsSection(
            canvas = canvas,
            top = sectionTop + dp(24f),
            horizontalPadding = horizontalPadding,
            availableWidth = availableWidth,
            itemCount = 2,
            showAddButton = false
        )
    }

    private fun drawGoalsSection(
        canvas: Canvas,
        top: Float,
        horizontalPadding: Float,
        availableWidth: Float,
        itemCount: Int,
        showAddButton: Boolean
    ): Float {
        canvas.drawRoundRect(
            horizontalPadding,
            top,
            horizontalPadding + dp(140f),
            top + dp(20f),
            dp(6f),
            dp(6f),
            paint
        )

        if (showAddButton) {
            canvas.drawCircle(
                horizontalPadding + availableWidth - dp(12f),
                top + dp(10f),
                dp(10f),
                paint
            )
        }

        var itemTop = top + dp(34f)

        repeat(itemCount) {
            canvas.drawRoundRect(
                horizontalPadding,
                itemTop,
                horizontalPadding + availableWidth,
                itemTop + dp(48f),
                dp(8f),
                dp(8f),
                paint
            )
            itemTop += dp(56f)
        }

        return itemTop
    }

    fun start() {
        if (!animator.isStarted) animator.start()
    }

    fun stop() {
        animator.cancel()
        shimmerPosition = -1f
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private companion object {
        const val BASE_COLOR = 0xFFD9DDE1.toInt()
        const val HIGHLIGHT_COLOR = 0xFFF4F5F6.toInt()
    }
}
