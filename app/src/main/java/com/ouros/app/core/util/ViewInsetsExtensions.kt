package com.ouros.app.core.util

import android.content.res.Resources
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView

/**
 * Converte valor inteiro de dp para pixels (px) usando as métricas do sistema.
 */
val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

/**
 * Ajusta a margem inferior de uma View com base nas barras de navegação do sistema.
 * Preserva a margem original configurada no layout XML e adiciona a altura
 * das barras de navegação (WindowInsetsCompat.Type.navigationBars()).
 */
fun View.applyBottomSystemBarMargin(baseMarginBottomPx: Int? = null) {
    val initialBottomMargin = baseMarginBottomPx
        ?: (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin
        ?: 0

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = initialBottomMargin + navBars.bottom
        }
        insets
    }

    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(v)
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}

/**
 * Ajusta o padding inferior de uma View (RecyclerView, ScrollView, etc.) com base
 * nas barras de navegação do sistema, preservando o padding inferior original.
 */
fun View.applyBottomSystemBarPadding(basePaddingBottomPx: Int? = null) {
    val initialBottomPadding = basePaddingBottomPx ?: paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        view.updatePadding(bottom = initialBottomPadding + navBars.bottom)
        insets
    }

    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(v)
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}

/**
 * Mantém o formulário ou container fixo na tela (sem ScrollView), elevando-o fisicamente
 * via translationY em perfeita sincronia com o teclado virtual (IME), de modo que o
 * input e o botão de ação fiquem presos/apoiados diretamente em cima do teclado.
 *
 * Ao fechar o teclado, retorna suavemente à posição original de descanso.
 *
 * @param bottomAnchorView View cuja base inferior deve ficar imediatamente acima do teclado.
 *                         Se null, usa a base do próprio container.
 * @param extraMarginPx Espaçamento extra (respiro) acima do topo do teclado.
 * @param viewsToFadeOut Lista de views secundárias (ex: botões no rodapé como "Esqueci a senha")
 *                       que devem esmaecer suavemente (alpha de 1 para 0) enquanto o teclado sobe.
 * @param collapsingViews Lista de views de topo (ex: fotos de perfil ou cards de métricas) que
 *                        devem esmaecer e recolher quando o teclado subir.
 */
fun View.applyImeFixedTranslation(
    bottomAnchorView: View? = null,
    extraMarginPx: Int = 16.dp,
    viewsToFadeOut: List<View> = emptyList(),
    collapsingViews: List<View> = emptyList()
) {
    val allFadeViews = (viewsToFadeOut + collapsingViews).distinct()

    fun isChildOrDescendant(view: View): Boolean {
        var currentParent = view.parent
        while (currentParent != null) {
            if (currentParent === this) return true
            currentParent = currentParent.parent
        }
        return false
    }

    fun calculateTargetTranslation(imeBottom: Int): Float {
        if (imeBottom <= 0) return 0f

        val anchor = bottomAnchorView ?: this
        val root = rootView
        val rootHeight = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val keyboardTop = rootHeight - imeBottom

        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val anchorSelfTranslation = if (anchor != this) anchor.translationY else 0f
        val anchorBottomOriginal = location[1] + anchor.height - translationY - anchorSelfTranslation

        val neededShiftForAnchor = anchorBottomOriginal - (keyboardTop - extraMarginPx)

        val containerLocation = IntArray(2)
        getLocationInWindow(containerLocation)
        val containerTopOriginal = containerLocation[1] - translationY
        val insets = ViewCompat.getRootWindowInsets(this)
        val statusBarTop = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 24.dp
        val topMarginPx = statusBarTop + 8.dp

        val focused = findFocus()
        val targetShift = if (focused != null && isChildOrDescendant(focused)) {
            var directChild: View = focused
            while (directChild.parent != null && directChild.parent !== this && directChild.parent is View) {
                directChild = directChild.parent as View
            }
            val focusedField = if (directChild.parent === this) directChild else focused

            val fLocation = IntArray(2)
            focusedField.getLocationInWindow(fLocation)
            val fBottomOriginal = fLocation[1] + focusedField.height - translationY
            val fTopOriginal = fLocation[1] - translationY

            val shiftToClearKeyboard = fBottomOriginal - (keyboardTop - extraMarginPx)
            val maxShiftToKeepOnScreen = maxOf(0f, fTopOriginal - topMarginPx)

            val desiredShift = maxOf(neededShiftForAnchor.toFloat(), shiftToClearKeyboard.toFloat())
            minOf(desiredShift, maxShiftToKeepOnScreen)
        } else {
            neededShiftForAnchor.toFloat()
        }

        if (targetShift <= 0f) return 0f

        val maxAllowedShift = maxOf(0f, containerTopOriginal - statusBarTop)
        return minOf(targetShift, maxAllowedShift)
    }

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

        if (!imeVisible || imeInsets.bottom == 0) {
            animate().translationY(0f).setDuration(200).start()
            allFadeViews.forEach {
                it.animate().alpha(1f).setDuration(200).withEndAction {
                    it.visibility = View.VISIBLE
                }.start()
            }
        } else {
            val shift = calculateTargetTranslation(imeInsets.bottom)
            animate().translationY(-shift).setDuration(200).start()
            allFadeViews.forEach {
                it.animate().alpha(0f).setDuration(200).withEndAction {
                    if (it.alpha == 0f) it.visibility = View.INVISIBLE
                }.start()
            }
        }

        insets
    }

    ViewCompat.setWindowInsetsAnimationCallback(
        this,
        object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat
            ): WindowInsetsAnimationCompat.BoundsCompat {
                allFadeViews.forEach { it.visibility = View.VISIBLE }
                return bounds
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat {
                val imeAnim = runningAnimations.find {
                    it.typeMask and WindowInsetsCompat.Type.ime() != 0
                }
                if (imeAnim != null) {
                    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val shift = calculateTargetTranslation(imeInsets.bottom)
                    translationY = -shift

                    val fraction = imeAnim.interpolatedFraction
                    val isOpening = imeInsets.bottom > 0
                    val targetAlpha = if (isOpening) 1f - fraction else fraction
                    allFadeViews.forEach { it.alpha = targetAlpha.coerceIn(0f, 1f) }
                }
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    val insets = ViewCompat.getRootWindowInsets(this@applyImeFixedTranslation)
                    val imeBottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                    if (imeBottom == 0) {
                        translationY = 0f
                        allFadeViews.forEach {
                            it.visibility = View.VISIBLE
                            it.alpha = 1f
                        }
                    } else {
                        allFadeViews.forEach {
                            it.alpha = 0f
                            it.visibility = View.INVISIBLE
                        }
                    }
                }
            }
        }
    )

    viewTreeObserver.addOnGlobalFocusChangeListener { _, _ ->
        if (isAttachedToWindow) {
            val rootInsets = ViewCompat.getRootWindowInsets(this)
            val imeBottom = rootInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            val imeVisible = rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (imeVisible && imeBottom > 0) {
                post {
                    val shift = calculateTargetTranslation(imeBottom)
                    animate().translationY(-shift).setDuration(200).start()
                }
            }
        }
    }

    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(v)
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}

/**
 * Ajusta o padding e a rolagem de formulários em sincronia com o teclado virtual (IME)
 * e a barra de navegação do sistema.
 *
 * - Quando o teclado está fechado, mantém o layout exatamente como foi desenhado (espaçoso).
 * - Quando o teclado sobe, anima a rolagem/elevação em tempo real (WindowInsetsAnimationCompat),
 *   fazendo com que os campos de entrada e botões de ação se levantem suavemente junto com o teclado.
 * - Quando o teclado fecha, retorna a tela suavemente para a posição inicial de descanso.
 */
fun View.applyImeAndNavigationPadding(extraPaddingPx: Int = 0) {
    val initialPaddingBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val bottomInset = maxOf(imeInsets.bottom, navInsets.bottom)

        view.updatePadding(bottom = initialPaddingBottom + bottomInset + extraPaddingPx)

        if (imeInsets.bottom > 0) {
            view.post {
                val focused = view.findFocus()
                if (focused != null && view is ViewGroup) {
                    val rect = Rect()
                    focused.getDrawingRect(rect)
                    view.offsetDescendantRectToMyCoords(focused, rect)
                    rect.bottom += extraPaddingPx + 16.dp
                    view.requestRectangleOnScreen(rect, false)
                }
            }
        } else {
            // Teclado fechado: retorna suavemente para a posição inicial
            if (view is NestedScrollView) {
                view.smoothScrollTo(0, 0)
            } else if (view is ScrollView) {
                view.smoothScrollTo(0, 0)
            }
        }

        insets
    }

    // Animação sincronizada com o teclado em tempo real
    ViewCompat.setWindowInsetsAnimationCallback(
        this,
        object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
            private var startScrollY = 0
            private var targetScrollY = 0

            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    startScrollY = scrollY
                }
            }

            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat
            ): WindowInsetsAnimationCompat.BoundsCompat {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    val focused = findFocus()
                    val isScrollContainer = this@applyImeAndNavigationPadding is NestedScrollView ||
                            this@applyImeAndNavigationPadding is ScrollView
                    val viewGroup = this@applyImeAndNavigationPadding as? ViewGroup

                    if (focused != null && isScrollContainer && viewGroup != null) {
                        val focusedRect = Rect()
                        val container = (focused.parent as? View) ?: focused
                        container.getDrawingRect(focusedRect)
                        viewGroup.offsetDescendantRectToMyCoords(container, focusedRect)

                        val continueBtn = findViewById<View>(com.ouros.app.R.id.btnContinue)
                            ?: findViewById<View>(com.ouros.app.R.id.btnFinish)
                            ?: findViewById<View>(com.ouros.app.R.id.recordPrimary)

                        val bottomToClear = if (continueBtn != null && continueBtn.isShown) {
                            val btnRect = Rect()
                            continueBtn.getDrawingRect(btnRect)
                            viewGroup.offsetDescendantRectToMyCoords(continueBtn, btnRect)
                            maxOf(focusedRect.bottom, btnRect.bottom)
                        } else {
                            focusedRect.bottom
                        }

                        val availableHeight = height - bounds.upperBound.bottom
                        targetScrollY = if (availableHeight > 0) {
                            maxOf(0, bottomToClear - availableHeight + extraPaddingPx + 16.dp)
                        } else {
                            startScrollY
                        }
                    } else {
                        targetScrollY = 0
                    }
                }
                return bounds
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat {
                val imeAnim = runningAnimations.find {
                    it.typeMask and WindowInsetsCompat.Type.ime() != 0
                }
                if (imeAnim != null) {
                    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    val bottomInset = maxOf(imeInsets.bottom, navInsets.bottom)
                    updatePadding(bottom = initialPaddingBottom + bottomInset + extraPaddingPx)

                    val isScrollContainer = this@applyImeAndNavigationPadding is NestedScrollView ||
                            this@applyImeAndNavigationPadding is ScrollView

                    if (isScrollContainer && targetScrollY > 0) {
                        val fraction = imeAnim.interpolatedFraction
                        val currentScroll = (startScrollY + (targetScrollY - startScrollY) * fraction).toInt()
                        scrollTo(0, currentScroll)
                    }
                }
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    val insets = ViewCompat.getRootWindowInsets(this@applyImeAndNavigationPadding)
                    val imeBottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                    if (imeBottom == 0) {
                        if (this@applyImeAndNavigationPadding is NestedScrollView) {
                            smoothScrollTo(0, 0)
                        } else if (this@applyImeAndNavigationPadding is ScrollView) {
                            smoothScrollTo(0, 0)
                        }
                    }
                }
            }
        }
    )

    // Monitora troca de foco entre campos enquanto o teclado já estiver aberto
    viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
        if (newFocus != null && isAttachedToWindow) {
            val rootInsets = ViewCompat.getRootWindowInsets(this)
            val imeBottom = rootInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            if (imeBottom > 0) {
                post {
                    val viewGroup = this as? ViewGroup
                    if (viewGroup != null) {
                        val rect = Rect()
                        newFocus.getDrawingRect(rect)
                        viewGroup.offsetDescendantRectToMyCoords(newFocus, rect)
                        rect.bottom += extraPaddingPx + 16.dp
                        requestRectangleOnScreen(rect, true)
                    }
                }
            }
        }
    }

    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(v)
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}
