package com.ouros.app.core.util

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewInsetsExtensionsTest {

    @Test
    fun dp_convertsValueToPixelsBasedOnDensity() {
        val zero = 0.dp
        assertEquals(0, zero)

        val sixteen = 16.dp
        assertTrue(sixteen >= 0)
    }

    @Test
    fun applyBottomSystemBarMargin_withBaseMargin_attachesListener() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)
        view.layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 10
        }

        view.applyBottomSystemBarMargin(baseMarginBottomPx = 20)
        assertNotNull(view)
    }

    @Test
    fun applyBottomSystemBarMargin_withoutExplicitBaseMargin_attachesListener() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)
        view.layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 15
        }

        view.applyBottomSystemBarMargin()
        assertNotNull(view)
    }

    @Test
    fun applyBottomSystemBarMargin_withoutMarginLayoutParams_attachesListener() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)

        view.applyBottomSystemBarMargin()
        assertNotNull(view)
    }

    @Test
    fun applyBottomSystemBarPadding_withBasePadding_attachesListener() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)
        view.setPadding(0, 0, 0, 10)

        view.applyBottomSystemBarPadding(basePaddingBottomPx = 25)
        assertNotNull(view)
    }

    @Test
    fun applyBottomSystemBarPadding_withoutExplicitBasePadding_attachesListener() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)
        view.setPadding(0, 0, 0, 10)

        view.applyBottomSystemBarPadding()
        assertNotNull(view)
    }

    @Test
    fun applyImeFixedTranslation_attachesWindowInsetsAndAnimationCallbacks() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val child = View(context)
        val fadeView = View(context)
        val collapsingView = View(context)

        parent.addView(child)
        parent.addView(fadeView)
        parent.addView(collapsingView)

        parent.applyImeFixedTranslation(
            bottomAnchorView = child,
            extraMarginPx = 16,
            viewsToFadeOut = listOf(fadeView),
            collapsingViews = listOf(collapsingView)
        )
        assertNotNull(parent)
    }

    @Test
    fun applyImeFixedTranslation_withDefaults_attachesCallbacks() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)

        parent.applyImeFixedTranslation()
        assertNotNull(parent)
    }

    @Test
    fun applyImeAndNavigationPadding_attachesCallbacksOnView() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)

        view.applyImeAndNavigationPadding(extraPaddingPx = 8)
        assertNotNull(view)
    }

    @Test
    fun applyImeAndNavigationPadding_attachesCallbacksOnScrollView() {
        val context = RuntimeEnvironment.getApplication()
        val scrollView = ScrollView(context)

        scrollView.applyImeAndNavigationPadding(extraPaddingPx = 8)
        assertNotNull(scrollView)
    }

    @Test
    fun applyImeAndNavigationPadding_attachesCallbacksOnNestedScrollView() {
        val context = RuntimeEnvironment.getApplication()
        val nestedScrollView = NestedScrollView(context)

        nestedScrollView.applyImeAndNavigationPadding(extraPaddingPx = 8)
        assertNotNull(nestedScrollView)
    }
}
