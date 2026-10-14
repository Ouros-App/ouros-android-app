package com.ouros.app.feature.onboarding.tutorial

import androidx.lifecycle.ViewModel

class TutorialViewModel : ViewModel() {

    interface Listener {
        fun onPageChanged(position: Int)
        fun onTutorialFinished()
    }

    private var listener: Listener? = null

    val pages: List<TutorialPage> = TutorialContent.pages

    var currentPage: Int = 0
        private set

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun clearListener(listener: Listener) {
        if (this.listener === listener) {
            this.listener = null
        }
    }

    fun onPageSelected(position: Int) {
        if (position in pages.indices) {
            currentPage = position
        }
    }

    fun onNextClicked() {
        if (currentPage < pages.lastIndex) {
            currentPage++
            listener?.onPageChanged(currentPage)
        } else {
            listener?.onTutorialFinished()
        }
    }

    fun onSkipClicked() {
        listener?.onTutorialFinished()
    }
}