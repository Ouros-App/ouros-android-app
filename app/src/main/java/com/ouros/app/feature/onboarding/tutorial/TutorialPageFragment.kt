package com.ouros.app.feature.onboarding.tutorial

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.ouros.app.R

class TutorialPageFragment : Fragment(R.layout.fragment_tutorial_page) {

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val arguments = requireArguments()

        val title = arguments.getString(ARG_TITLE)
        val description = arguments.getString(ARG_DESCRIPTION)
        val imageResId = arguments.getInt(ARG_IMAGE_RES_ID)

        view.findViewById<TextView>(R.id.txtTitle).text = title
        view.findViewById<TextView>(R.id.txtDescription).text = description

        view.findViewById<ImageView>(R.id.imgIllustration)
            .setImageResource(imageResId)
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_IMAGE_RES_ID = "imageResId"

        fun newInstance(page: TutorialPage): TutorialPageFragment {
            return TutorialPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, page.title)
                    putString(ARG_DESCRIPTION, page.description)
                    putInt(ARG_IMAGE_RES_ID, page.imageResId)
                }
            }
        }
    }
}