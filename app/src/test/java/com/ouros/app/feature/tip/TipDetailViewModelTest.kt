package com.ouros.app.feature.tip

import com.ouros.app.data.tip.ReviewRequest
import com.ouros.app.data.tip.ReviewResponse
import com.ouros.app.data.tip.TipApi
import com.ouros.app.data.tip.TipDetailResponse
import com.ouros.app.data.tip.TipRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class TipDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadTip_success_updatesUiState() = runTest(testDispatcher) {
        val tip = TipDetailResponse(
            id = 1L,
            title = "Manejo de Água",
            tip = "Instale hidrômetros",
            totalReviews = 5,
            averageRating = 4.5
        )

        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> = Response.success(tip)
            override suspend fun createReview(tipId: Long, request: ReviewRequest): Response<ReviewResponse> = error("unused")
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = false, ioDispatcher = testDispatcher)
        val viewModel = TipDetailViewModel(repository)

        viewModel.loadTip(1L)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(1L, state.tip?.id)
        assertEquals("Manejo de Água", state.tip?.title)
    }

    @Test
    fun loadTip_withRichContent_emitsCompleteTipInUiState() = runTest(testDispatcher) {
        val richTip = TipDetailResponse(
            id = 2L,
            title = "Regulagem de Pressão Nipple",
            tip = "* **Diagnóstico:** Vazamento detectado.\n1. **Ação:** Regular manômetro.",
            imageUrl = "https://res.cloudinary.com/ouros/image/upload/v1/tips/nipple.webp",
            totalReviews = 10,
            averageRating = 4.8
        )

        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> = Response.success(richTip)
            override suspend fun createReview(tipId: Long, request: ReviewRequest): Response<ReviewResponse> = error("unused")
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = false, ioDispatcher = testDispatcher)
        val viewModel = TipDetailViewModel(repository)

        viewModel.loadTip(2L)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(2L, state.tip?.id)
        assertEquals("Regulagem de Pressão Nipple", state.tip?.effectiveTitle)
        assertEquals("https://res.cloudinary.com/ouros/image/upload/v1/tips/nipple.webp", state.tip?.effectiveImageUrl)
        assertEquals(10, state.tip?.effectiveTotalReviews)
        assertEquals(4.8, state.tip?.effectiveAverageRating ?: 0.0, 0.001)
        assertTrue(state.tip?.tip?.contains("**Diagnóstico:**") == true)
    }

    @Test
    fun loadTip_failure_setsErrorMessage() = runTest(testDispatcher) {
        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> {
                return Response.error(404, "Not Found".toResponseBody(null))
            }
            override suspend fun createReview(tipId: Long, request: ReviewRequest): Response<ReviewResponse> = error("unused")
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = false, ioDispatcher = testDispatcher)
        val viewModel = TipDetailViewModel(repository)

        viewModel.loadTip(999L)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertNull(state.tip)
    }

    @Test
    fun submitReview_success_updatesTipReviewCountAndUserRating() = runTest(testDispatcher) {
        val initialTip = TipDetailResponse(
            id = 10L,
            title = "Economia de Ração",
            tip = "Regular calhas",
            totalReviews = 3,
            averageRating = 4.0
        )

        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> = Response.success(initialTip)
            override suspend fun createReview(tipId: Long, request: ReviewRequest): Response<ReviewResponse> {
                return Response.success(
                    ReviewResponse(
                        id = 1L,
                        idTip = tipId,
                        idFarmOwner = 5L,
                        score = request.score,
                        comment = request.comment,
                        createdAt = "2026-10-18"
                    )
                )
            }
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = false, ioDispatcher = testDispatcher)
        val viewModel = TipDetailViewModel(repository)

        viewModel.loadTip(10L)

        viewModel.submitReview(tipId = 10L, score = 5, comment = "Muito esclarecedora!")

        val state = viewModel.uiState.value
        assertFalse(state.isSubmittingReview)
        assertEquals(5, state.userRating)
        assertEquals(4, state.tip?.totalReviews)
        assertNotNull(state.reviewSuccessMessage)

        viewModel.clearReviewMessage()
        assertNull(viewModel.uiState.value.reviewSuccessMessage)
    }

    @Test
    fun submitReview_failure_setsErrorMessage() = runTest(testDispatcher) {
        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> = error("unused")
            override suspend fun createReview(tipId: Long, request: ReviewRequest): Response<ReviewResponse> {
                return Response.error(400, "Bad Request".toResponseBody(null))
            }
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = false, ioDispatcher = testDispatcher)
        val viewModel = TipDetailViewModel(repository)

        viewModel.submitReview(tipId = 10L, score = 3)

        val state = viewModel.uiState.value
        assertFalse(state.isSubmittingReview)
        assertNotNull(state.errorMessage)
        assertNull(state.reviewSuccessMessage)
    }

    @Test
    fun factory_createsTipDetailViewModel() {
        val factory = TipDetailViewModel.Factory()
        val created = factory.create(TipDetailViewModel::class.java)
        assertNotNull(created)
    }
}
