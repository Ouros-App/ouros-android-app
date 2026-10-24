package com.ouros.app.feature.tip

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import java.util.Locale

class TipDetailActivity : AppCompatActivity() {

    private val viewModel: TipDetailViewModel by viewModels {
        TipDetailViewModel.Factory()
    }

    private val markwon: Markwon by lazy {
        Markwon.create(this)
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

        // Renderiza o corpo da dica zootécnica com Markdown
        val normalizedTip = tip.tip.replace("\\n", "\n").trim()
        markwon.setMarkdown(txtTipBody, normalizedTip)

        val rating = if (tip.effectiveAverageRating > 0.0) tip.effectiveAverageRating else 3.5
        txtRatingScore.text = String.format(Locale.US, "%.1f", rating)
        txtTotalReviews.text = "(${tip.effectiveTotalReviews} avaliações)"

        renderStarIcons(starViews, rating)

        // Carregamento resiliente da imagem Cloudinary (.webp)
        val imageUrl = tip.effectiveImageUrl
        if (!imageUrl.isNullOrBlank()) {
            imgTipHero.load(imageUrl) {
                crossfade(true)
                placeholder(R.drawable.tip_water_hero)
                error(R.drawable.tip_water_hero)
            }
        } else {
            imgTipHero.setImageResource(R.drawable.tip_water_hero)
        }
    }

    private fun renderStarIcons(views: List<ImageView>, rating: Double) {
        views.forEachIndexed { index, starView ->
            val starNumber = index + 1
            val iconRes = when {
                rating >= starNumber - 0.25 -> R.drawable.ic_star_filled
                rating >= starNumber - 0.75 -> R.drawable.ic_star_half
                else -> R.drawable.ic_star_border
            }
            starView.setImageResource(iconRes)
        }
    }

    private fun showReviewDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_review_tip)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        var currentSelectedScore = 4
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

        // Start with the 3.5-star visual from the design mockup until user taps a star
        renderStarIcons(stars, 3.5)

        fun updateDialogStars(score: Int) {
            currentSelectedScore = score
            renderStarIcons(stars, score.toDouble())
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
                comment = if (comment.isNullOrBlank()) "Avaliação de eficácia da dica técnica" else comment
            )
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
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
