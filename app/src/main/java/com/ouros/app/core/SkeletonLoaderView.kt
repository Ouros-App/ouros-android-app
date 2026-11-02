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
import androidx.core.view.isVisible

/**
 * Componente de apoio a aplicativo. Reúne comportamento relacionado a essa funcionalidade para manter o fluxo principal mais simples.
 */
class SkeletonLoaderView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyle: Int = 0
) : View(context, attributes, defaultStyle) {

    enum class Mode {
        DASHBOARD,
        GOALS,
        RANK,
        RECORD,
        NOTIFICATIONS,
        TIP_DETAIL
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
            Mode.RANK -> drawRank(canvas)
            Mode.RECORD -> drawRecord(canvas)
            Mode.NOTIFICATIONS -> drawNotifications(canvas)
            Mode.TIP_DETAIL -> drawTipDetail(canvas)
        }
    }

    private fun drawDashboard(canvas: Canvas) {
        val horizontalPadding = contentHorizontalPadding()
        val availableWidth = width - horizontalPadding * 2f
        val contentTop = contentTopPadding()

        drawDashboardCards(
            canvas = canvas,
            top = contentTop + dp(8f),
            horizontalPadding = horizontalPadding,
            availableWidth = availableWidth
        )

        var sectionTop = contentTop + dp(145f)
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
        val horizontalPadding = contentHorizontalPadding()
        val availableWidth = width - horizontalPadding * 2f
        val circleCenterX = horizontalPadding + dp(79f)
        val circleCenterY = contentTopPadding() + dp(85f)

        canvas.drawCircle(
            circleCenterX,
            circleCenterY,
            dp(74f),
            paint
        )

        val informationLeft = horizontalPadding + dp(158f + 20f)
        val informationRight = horizontalPadding + availableWidth

        canvas.drawRoundRect(
            informationLeft,
            contentTopPadding() + dp(53f),
            informationRight,
            contentTopPadding() + dp(71f),
            dp(6f),
            dp(6f),
            paint
        )

        canvas.drawRoundRect(
            informationLeft,
            contentTopPadding() + dp(83f),
            informationRight,
            contentTopPadding() + dp(93f),
            dp(5f),
            dp(5f),
            paint
        )

        var sectionTop = contentTopPadding() + dp(170f + 28f)

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
                itemTop + dp(64f),
                dp(8f),
                dp(8f),
                paint
            )
            itemTop += dp(72f)
        }

        return itemTop
    }

    private fun drawRank(canvas: Canvas) {
        val horizontalPadding = contentHorizontalPadding()
        val availableWidth = width - horizontalPadding * 2f
        val startTop = contentTopPadding()

        // Card superior de liga e posicao.
        canvas.drawRoundRect(
            horizontalPadding,
            startTop,
            horizontalPadding + availableWidth,
            startTop + dp(136f),
            dp(15f),
            dp(15f),
            paint
        )

        // Titulo 'Proximos resultados em'.
        canvas.drawRoundRect(
            horizontalPadding,
            startTop + dp(156f),
            horizontalPadding + dp(180f),
            startTop + dp(156f + 18f),
            dp(6f),
            dp(6f),
            paint
        )

        // Contador de tempo restante.
        canvas.drawRoundRect(
            horizontalPadding,
            startTop + dp(182f),
            horizontalPadding + dp(120f),
            startTop + dp(182f + 24f),
            dp(6f),
            dp(6f),
            paint
        )

        // Lista de produtores ranqueados.
        var itemTop = startTop + dp(222f)
        repeat(7) {
            // Posicao (numero)
            canvas.drawRoundRect(
                horizontalPadding,
                itemTop + dp(18f),
                horizontalPadding + dp(20f),
                itemTop + dp(38f),
                dp(4f),
                dp(4f),
                paint
            )

            // Avatar circular
            canvas.drawCircle(
                horizontalPadding + dp(28f + 23f),
                itemTop + dp(28f),
                dp(23f),
                paint
            )

            // Nome da granja / produtor
            canvas.drawRoundRect(
                horizontalPadding + dp(86f),
                itemTop + dp(18f),
                horizontalPadding + dp(200f),
                itemTop + dp(38f),
                dp(6f),
                dp(6f),
                paint
            )

            // Pontuacao / CGI (alinhado a direita)
            canvas.drawRoundRect(
                horizontalPadding + availableWidth - dp(50f),
                itemTop + dp(18f),
                horizontalPadding + availableWidth,
                itemTop + dp(38f),
                dp(6f),
                dp(6f),
                paint
            )

            itemTop += dp(56f)
        }
    }

    private fun drawRecord(canvas: Canvas) {
        val horizontalPadding = contentHorizontalPadding()
        val availableWidth = width - horizontalPadding * 2f
        val top = contentTopPadding()
        canvas.drawRoundRect(horizontalPadding, top, horizontalPadding + availableWidth, top + dp(112f), dp(18f), dp(18f), paint)
        val simulatorTop = top + dp(136f)
        canvas.drawRoundRect(horizontalPadding, simulatorTop, horizontalPadding + availableWidth, simulatorTop + dp(140f), dp(18f), dp(18f), paint)
        var sectionTop = simulatorTop + dp(172f)
        repeat(2) {
            canvas.drawRoundRect(horizontalPadding, sectionTop, horizontalPadding + dp(170f), sectionTop + dp(22f), dp(6f), dp(6f), paint)
            var rowTop = sectionTop + dp(34f)
            repeat(3) {
                canvas.drawRoundRect(horizontalPadding, rowTop, horizontalPadding + availableWidth, rowTop + dp(64f), dp(10f), dp(10f), paint)
                rowTop += dp(72f)
            }
            sectionTop = rowTop + dp(24f)
        }
    }

    private fun drawNotifications(canvas: Canvas) {
        val horizontalPadding = dp(20f)
        val availableWidth = width - horizontalPadding * 2f
        var top = dp(12f)
        repeat(6) {
            canvas.drawRoundRect(horizontalPadding, top, horizontalPadding + availableWidth, top + dp(88f), dp(14f), dp(14f), paint)
            top += dp(100f)
        }
    }

    private fun drawTipDetail(canvas: Canvas) {
        val horizontalPadding = dp(24f)
        val availableWidth = width - horizontalPadding * 2f
        val top = dp(12f)
        canvas.drawRoundRect(horizontalPadding, top, horizontalPadding + availableWidth * 0.78f, top + dp(24f), dp(6f), dp(6f), paint)
        canvas.drawRoundRect(horizontalPadding, top + dp(30f), horizontalPadding + dp(120f), top + dp(48f), dp(6f), dp(6f), paint)
        canvas.drawRoundRect(horizontalPadding, top + dp(60f), horizontalPadding + availableWidth, top + dp(245f), dp(12f), dp(12f), paint)
        var lineTop = top + dp(264f)
        repeat(8) { index ->
            val right = if (index % 3 == 2) horizontalPadding + availableWidth * 0.72f else horizontalPadding + availableWidth
            canvas.drawRoundRect(horizontalPadding, lineTop, right, lineTop + dp(12f), dp(5f), dp(5f), paint)
            lineTop += dp(24f)
        }
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
        if (isVisible) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun contentTopPadding(): Float =
        resources.getDimension(com.ouros.app.R.dimen.main_content_top_padding)

    private fun contentHorizontalPadding(): Float =
        resources.getDimension(com.ouros.app.R.dimen.main_content_horizontal_padding)

    private companion object {
        const val BASE_COLOR = 0xFFD9DDE1.toInt()
        const val HIGHLIGHT_COLOR = 0xFFF4F5F6.toInt()
    }
}
