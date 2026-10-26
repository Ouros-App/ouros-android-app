package com.ouros.app.core.util

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

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
 * Ajusta o padding inferior de uma View (como NestedScrollView ou ScrollView) quando o teclado (IME)
 * ou as barras de navegação do sistema aparecem.
 */
fun View.applyImeAndNavigationPadding(extraPaddingPx: Int = 0) {
    val initialPaddingBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val bottomInset = maxOf(imeInsets.bottom, navInsets.bottom)

        view.updatePadding(bottom = initialPaddingBottom + bottomInset + extraPaddingPx)
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
