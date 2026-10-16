package com.ouros.app.feature.tip

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.ouros.app.R
import com.ouros.app.data.tip.TipDetailResponse
import kotlinx.coroutines.launch
import java.util.Locale

class TipDetailActivity : AppCompatActivity() {

    private val viewModel: TipDetailViewModel by viewModels {
        TipDetailViewModel.Factory()
    }

    private var tipId: Long = 1L

    private lateinit var btnBack: ImageView
    private lateinit var txtTipTitle: TextView
    private lateinit var starViews: List<ImageView>
    private lateinit var txtRatingScore: TextView
    private lateinit var txtTotalReviews: TextView
    private lateinit var imgTipHero: ImageView
    private lateinit var txtTipBody: TextView
    private lateinit var cardReviewAction: View
    private lateinit var scrollTipContent: View
    private lateinit var progressLoading: ProgressBar
    private lateinit var layoutError: LinearLayout
    private lateinit var txtErrorMessage: TextView
    private lateinit var btnRetry: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tip_detail)

        tipId = intent.getLongExtra(EXTRA_TIP_ID, 1L)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.headerTip)) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(
                view.paddingLeft,
                statusBarInset.top + 16,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        bindViews()
        setupListeners()
        observeState()

        viewModel.loadTip(tipId)
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        txtTipTitle = findViewById(R.id.txtTipTitle)
        starViews = listOf(
            findViewById(R.id.imgStar1),
            findViewById(R.id.imgStar2),
            findViewById(R.id.imgStar3),
            findViewById(R.id.imgStar4),
            findViewById(R.id.imgStar5)
        )
        txtRatingScore = findViewById(R.id.txtRatingScore)
        txtTotalReviews = findViewById(R.id.txtTotalReviews)
        imgTipHero = findViewById(R.id.imgTipHero)
        txtTipBody = findViewById(R.id.txtTipBody)
        cardReviewAction = findViewById(R.id.cardReviewAction)
        scrollTipContent = findViewById(R.id.scrollTipContent)
        progressLoading = findViewById(R.id.progressLoading)
        layoutError = findViewById(R.id.layoutError)
        txtErrorMessage = findViewById(R.id.txtErrorMessage)
        btnRetry = findViewById(R.id.btnRetry)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnRetry.setOnClickListener {
            viewModel.loadTip(tipId)
        }

        cardReviewAction.setOnClickListener {
            showReviewDialog()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(state: TipDetailUiState) {
        progressLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        if (state.errorMessage != null && state.tip == null) {
            layoutError.visibility = View.VISIBLE
            txtErrorMessage.text = state.errorMessage
            scrollTipContent.visibility = View.GONE
        } else {
            layoutError.visibility = View.GONE
            scrollTipContent.visibility = if (state.isLoading) View.GONE else View.VISIBLE

            state.tip?.let { tip ->
                renderTip(tip)
            }
        }

        state.reviewSuccessMessage?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            viewModel.clearReviewMessage()
        }
    }

    private fun renderTip(tip: TipDetailResponse) {
        txtTipTitle.text = tip.effectiveTitle
        txtTipBody.text = formatTipBody(tip.tip)

        val rating = tip.effectiveAverageRating
        txtRatingScore.text = String.format(Locale.US, "%.1f", rating)
        txtTotalReviews.text = "(${tip.effectiveTotalReviews} avaliações)"

        val roundedRating = rating.toInt().coerceIn(0, 5)
        starViews.forEachIndexed { index, starView ->
            if (index < roundedRating) {
                starView.setImageResource(R.drawable.ic_star_filled)
            } else {
                starView.setImageResource(R.drawable.ic_star_border)
            }
        }

        val imageUrl = tip.effectiveImageUrl
        if (!imageUrl.isNullOrBlank()) {
            imgTipHero.load(imageUrl) {
                crossfade(true)
                placeholder(R.drawable.dash_kpi)
                error(R.drawable.dash_kpi)
            }
        } else {
            imgTipHero.setImageResource(R.drawable.dash_kpi)
        }
    }

    private fun formatTipBody(rawTip: String): String {
        return rawTip.replace("\\n", "\n").trim()
    }

    private fun showReviewDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_review_tip)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        var currentSelectedScore = 5
        val stars = listOf(
            dialog.findViewById<ImageView>(R.id.star1),
            dialog.findViewById<ImageView>(R.id.star2),
            dialog.findViewById<ImageView>(R.id.star3),
            dialog.findViewById<ImageView>(R.id.star4),
            dialog.findViewById<ImageView>(R.id.star5)
        )
        val editComment = dialog.findViewById<EditText>(R.id.editComment)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelReview)
        val btnSubmit = dialog.findViewById<Button>(R.id.btnSubmitReview)

        fun updateDialogStars(score: Int) {
            currentSelectedScore = score
            stars.forEachIndexed { index, star ->
                if (index < score) {
                    star.setImageResource(R.drawable.ic_star_filled)
                } else {
                    star.setImageResource(R.drawable.ic_star_border)
                }
            }
        }

        stars.forEachIndexed { index, star ->
            star.setOnClickListener {
                updateDialogStars(index + 1)
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSubmit.setOnClickListener {
            val comment = editComment.text?.toString()?.trim()
            viewModel.submitReview(
                tipId = tipId,
                score = currentSelectedScore,
                comment = if (comment.isNullOrBlank()) null else comment
            )
            dialog.dismiss()
        }

        dialog.show()
    }

    companion object {
        const val EXTRA_TIP_ID = "extra_tip_id"

        fun createIntent(context: Context, tipId: Long): Intent {
            return Intent(context, TipDetailActivity::class.java).apply {
                putExtra(EXTRA_TIP_ID, tipId)
            }
        }
    }
}
