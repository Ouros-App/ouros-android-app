package com.ouros.app.feature.onboarding.tutorial

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.ouros.app.R
import com.ouros.app.feature.auth.choose.ChooseActivity
import com.ouros.app.feature.onboarding.tutorial.view.TutorialPagerAdapter

class TutorialActivity : AppCompatActivity(), TutorialViewModel.Listener {

    private lateinit var viewModel: TutorialViewModel
    private lateinit var pagerTutorial: ViewPager2

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {

        override fun onPageSelected(position: Int) {
            viewModel.onPageSelected(position)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tutorial)

        viewModel = ViewModelProvider(this)[TutorialViewModel::class.java]
        viewModel.setListener(this)

        pagerTutorial = findViewById(R.id.pagerTutorial)
        pagerTutorial.adapter = TutorialPagerAdapter(
            activity = this,
            pages = viewModel.pages
        )

        pagerTutorial.setCurrentItem(
            viewModel.currentPage,
            false
        )

        pagerTutorial.registerOnPageChangeCallback(
            pageChangeCallback
        )

        findViewById<View>(R.id.btnSkip).setOnClickListener {
            viewModel.onSkipClicked()
        }

        findViewById<View>(R.id.btnNext).setOnClickListener {
            viewModel.onNextClicked()
        }
    }

    override fun onPageChanged(position: Int) {
        pagerTutorial.setCurrentItem(position, true)
    }

    override fun onTutorialFinished() {
        startActivity(Intent(this, ChooseActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        pagerTutorial.unregisterOnPageChangeCallback(
            pageChangeCallback
        )

        viewModel.clearListener(this)
        super.onDestroy()
    }
}
