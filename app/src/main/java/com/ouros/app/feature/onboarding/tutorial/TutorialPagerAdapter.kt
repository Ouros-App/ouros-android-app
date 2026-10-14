package com.ouros.app.feature.onboarding.tutorial.view

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.ouros.app.feature.onboarding.tutorial.TutorialPage
import com.ouros.app.feature.onboarding.tutorial.TutorialPageFragment

class TutorialPagerAdapter(activity: FragmentActivity, private val pages: List<TutorialPage>) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int {
        return pages.size
    }

    override fun createFragment(position: Int): Fragment {
        return TutorialPageFragment.newInstance(pages[position])
    }
}