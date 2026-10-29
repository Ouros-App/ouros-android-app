package com.ouros.app.feature.secondary.chatbot

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import com.ouros.app.R
import com.ouros.app.data.auth.AppSession
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatbotStateTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val session = AppSession.sessionManager

    private class FakeChatbotApi : ChatbotApi {
        var chatCalls = 0
        var authorization: String? = null

        override suspend fun chat(
            authorization: String,
            message: RequestBody,
            threadId: RequestBody,
            attachment: MultipartBody.Part?
        ): Response<ChatResponse> {
            chatCalls++
            this.authorization = authorization
            return Response.success(ChatResponse(message = "answer"))
        }

        override suspend fun importMeasurements(
            authorization: String,
            request: MeasurementImportRequest
        ): Response<MeasurementImportResponse> = error("Unused in this test")
    }

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        Dispatchers.setMain(testDispatcher)
        session.clear()
    }

    @After
    fun tearDown() {
        session.clear()
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun successfulSend_resetsSendingAndAddsAssistantResponse() {
        session.setAccessToken("test-token", TimeUnit.HOURS.toSeconds(1))
        val api = FakeChatbotApi()
        val viewModel = ChatbotViewModel(
            RuntimeEnvironment.getApplication(),
            SavedStateHandle(),
            api,
            api
        )

        viewModel.send("hello", attachment = null)
        viewModel.send("follow-up", attachment = null)

        assertEquals(2, api.chatCalls)
        assertEquals("Bearer test-token", api.authorization)
        assertFalse(viewModel.state.value!!.sending)
        assertEquals(
            listOf(
                ChatLine("hello", true),
                ChatLine("answer", false),
                ChatLine("follow-up", true),
                ChatLine("answer", false)
            ),
            viewModel.state.value!!.messages
        )
        assertTrue(viewModel.state.value!!.error == null)
    }

    @Test
    fun composerBottomInset_usesLargerOfImeAndNavigationBar() {
        assertEquals(300, composerBottomInset(systemBarBottom = 24, imeBottom = 300))
        assertEquals(24, composerBottomInset(systemBarBottom = 24, imeBottom = 0))
    }

    @Test
    fun activityUsesImeInsetWithoutAddingNavigationBarInsetAgain() {
        val controller = Robolectric.buildActivity(ChatbotActivity::class.java).setup()
        try {
            val activity = controller.get()
            val root = activity.findViewById<android.view.View>(R.id.chatRoot)
            val basePadding = (16 * activity.resources.displayMetrics.density).roundToInt()
            val insets = WindowInsetsCompat.Builder()
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    Insets.of(0, 24, 0, 24)
                )
                .setInsets(
                    WindowInsetsCompat.Type.ime(),
                    Insets.of(0, 0, 0, 300)
                )
                .build()

            ViewCompat.dispatchApplyWindowInsets(root, insets)

            assertEquals(basePadding + 300, root.paddingBottom)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
