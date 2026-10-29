package com.ouros.app.feature.secondary.chatbot

import android.net.Uri
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import com.ouros.app.R
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.SecureRefreshTokenStore
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
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
    private val refreshTokenStore by lazy {
        SecureRefreshTokenStore(RuntimeEnvironment.getApplication())
    }

    private class FakeChatbotApi : ChatbotApi {
        var chatCalls = 0
        var importCalls = 0
        var authorization: String? = null
        var importedRequest: MeasurementImportRequest? = null
        var chatResponse: Response<ChatResponse> = Response.success(ChatResponse(message = "answer"))
        var importResponse: Response<MeasurementImportResponse> =
            Response.success(MeasurementImportResponse())

        override suspend fun chat(
            authorization: String,
            message: RequestBody,
            threadId: RequestBody,
            attachment: MultipartBody.Part?
        ): Response<ChatResponse> {
            chatCalls++
            this.authorization = authorization
            return chatResponse
        }

        override suspend fun importMeasurements(
            authorization: String,
            request: MeasurementImportRequest
        ): Response<MeasurementImportResponse> {
            importCalls++
            this.authorization = authorization
            importedRequest = request
            return importResponse
        }
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
        refreshTokenStore.clear()
    }

    @After
    fun tearDown() {
        session.clear()
        refreshTokenStore.clear()
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

    @Test
    fun retryWithoutPreview_replaysLastUserMessageWithoutAppendingItAgain() {
        session.setAccessToken("test-token", TimeUnit.HOURS.toSeconds(1))
        val api = FakeChatbotApi()
        val viewModel = newViewModel(api)
        viewModel.state.value = viewModel.state.value!!.copy(
            messages = listOf(ChatLine("first", true), ChatLine("old answer", false))
        )

        viewModel.retry()

        assertEquals(1, api.chatCalls)
        assertEquals(
            listOf(
                ChatLine("first", true),
                ChatLine("old answer", false),
                ChatLine("answer", false)
            ),
            viewModel.state.value!!.messages
        )
        assertFalse(viewModel.state.value!!.sending)
    }

    @Test
    fun retryWithPreview_importsOnlyValidRecordsAndClearsPreview() {
        session.setAccessToken("test-token", TimeUnit.HOURS.toSeconds(1))
        val api = FakeChatbotApi()
        val viewModel = newViewModel(api)
        viewModel.state.value = viewModel.state.value!!.copy(
            preview = preview(
                MeasurementImportRecord("water", "w1", "2026-01-01", "100", "120"),
                MeasurementImportRecord("water", "w2", "2026-01-02", "130", null),
                MeasurementImportRecord("energy", "e1", "2026-01-03", energyConsumption = "7.12"),
                MeasurementImportRecord("energy", "e2", "2026-01-04", energyConsumption = ""),
                MeasurementImportRecord("unknown", "x1", "2026-01-05")
            )
        )

        viewModel.retry()

        assertEquals(1, api.importCalls)
        assertEquals(
            listOf(WaterRegistry("2026-01-01", "100", "120")),
            api.importedRequest!!.waterRegistries
        )
        assertEquals(
            listOf(EnergyRegistry("2026-01-03", "7.12")),
            api.importedRequest!!.energyRegistries
        )
        assertTrue(viewModel.state.value!!.imported)
        assertFalse(viewModel.state.value!!.importing)
        assertEquals(null, viewModel.state.value!!.preview)
        assertEquals(
            "Importação concluída: 1 registros de água e 1 de energia.",
            viewModel.state.value!!.messages.last().text
        )
    }

    @Test
    fun confirmImportWithNoValidRecords_reportsFailureWithoutCallingApi() {
        val api = FakeChatbotApi()
        val viewModel = newViewModel(api)
        viewModel.state.value = viewModel.state.value!!.copy(preview = preview())

        viewModel.confirmImport()

        assertEquals(0, api.importCalls)
        assertFalse(viewModel.state.value!!.importing)
        assertTrue(viewModel.state.value!!.importAttemptFailed)
        assertEquals("Não há registros válidos para importar.", viewModel.state.value!!.error)
    }

    @Test
    fun cancelPreview_clearsPreviewAndReportsNoRecordsSaved() {
        val viewModel = newViewModel(FakeChatbotApi())
        viewModel.state.value = viewModel.state.value!!.copy(preview = preview())

        viewModel.cancelPreview()

        assertEquals(null, viewModel.state.value!!.preview)
        assertEquals(
            "Importação cancelada. Nenhum registro foi salvo.",
            viewModel.state.value!!.messages.last().text
        )
    }

    @Test
    fun cancelPreview_doesNothingWhileImportIsInProgressOrCompleted() {
        val viewModel = newViewModel(FakeChatbotApi())
        val initial = viewModel.state.value!!.copy(preview = preview(), importing = true)
        viewModel.state.value = initial

        viewModel.cancelPreview()
        assertEquals(initial, viewModel.state.value)

        viewModel.state.value = initial.copy(importing = false, imported = true)
        val imported = viewModel.state.value
        viewModel.cancelPreview()
        assertEquals(imported, viewModel.state.value)
    }

    @Test
    fun sendWithoutSession_reportsExpiredSessionAndDoesNotCallApi() {
        val api = FakeChatbotApi()
        val viewModel = newViewModel(api)

        viewModel.send("hello", attachment = null)

        assertEquals(0, api.chatCalls)
        assertFalse(viewModel.state.value!!.sending)
        assertTrue(viewModel.state.value!!.error!!.contains("sessão expirou"))
    }

    @Test
    fun unauthorizedSendWithoutRefreshToken_isNotRetried() {
        session.setAccessToken("expired-token", TimeUnit.HOURS.toSeconds(1))
        val api = FakeChatbotApi().apply {
            chatResponse = Response.error(401, "unauthorized".toResponseBody())
        }
        val viewModel = newViewModel(api)

        viewModel.send("hello", attachment = null)

        assertEquals(1, api.chatCalls)
        assertFalse(viewModel.state.value!!.sending)
        assertTrue(viewModel.state.value!!.error!!.contains("sessão expirou"))
    }

    @Test
    fun sendDoesNothingForEmptyMessageSendingRequestOrActivePreview() {
        val api = FakeChatbotApi()
        val viewModel = newViewModel(api)

        viewModel.send("", attachment = null)
        viewModel.state.value = viewModel.state.value!!.copy(sending = true)
        viewModel.send("busy", attachment = null)
        viewModel.state.value = viewModel.state.value!!.copy(sending = false, preview = preview())
        viewModel.send("preview", attachment = null)

        assertEquals(0, api.chatCalls)
    }

    @Test
    fun attachmentCanBeSelectedAndDiscarded() {
        val viewModel = newViewModel(FakeChatbotApi())
        val uri = Uri.parse("content://history/file.csv")

        viewModel.setAttachment(uri)
        assertEquals(uri, viewModel.state.value!!.selectedAttachment)

        viewModel.discardAttachment()
        assertEquals(null, viewModel.state.value!!.selectedAttachment)
    }

    @Test
    fun completeChatSendUsesResponseFallbacks() {
        val state = ChatState(threadId = "thread")

        assertEquals(
            "response",
            state.completeChatSend(ChatResponse(response = "response")).messages.last().text
        )
        assertEquals(
            "Recebi sua mensagem.",
            state.completeChatSend(ChatResponse()).messages.last().text
        )
    }

    @Test
    fun sendWithServerError_resetsSendingAndKeepsUserMessage() {
        session.setAccessToken("test-token", TimeUnit.HOURS.toSeconds(1))
        val api = FakeChatbotApi().apply {
            chatResponse = Response.error(500, "server error".toResponseBody())
        }
        val viewModel = newViewModel(api)

        viewModel.send("hello", attachment = null)

        assertEquals(1, api.chatCalls)
        assertFalse(viewModel.state.value!!.sending)
        assertEquals("hello", viewModel.state.value!!.messages.first().text)
        assertTrue(viewModel.state.value!!.error!!.contains("HTTP 500"))
    }

    @Test
    fun confirmImport_doesNothingWhileImportingAlreadyImportedOrPreviouslyFailed() {
        val api = FakeChatbotApi()
        val viewModel = newViewModel(api)
        val preview = preview(MeasurementImportRecord("energy", "e1", "2026-01-01", energyConsumption = "7"))

        for (blockedState in listOf(
            ChatState("thread", importing = true, preview = preview),
            ChatState("thread", imported = true, preview = preview),
            ChatState("thread", importAttemptFailed = true, preview = preview)
        )) {
            viewModel.state.value = blockedState
            viewModel.confirmImport()
            assertEquals(blockedState, viewModel.state.value)
        }
        assertEquals(0, api.importCalls)
    }

    @Test
    fun failedImportCanBeDismissedWithWarningToCheckFarmRecords() {
        session.setAccessToken("test-token", TimeUnit.HOURS.toSeconds(1))
        val api = FakeChatbotApi().apply {
            importResponse = Response.error(500, "server error".toResponseBody())
        }
        val viewModel = newViewModel(api)
        viewModel.state.value = viewModel.state.value!!.copy(
            preview = preview(
                MeasurementImportRecord("energy", "e1", "2026-01-01", energyConsumption = "7")
            )
        )

        viewModel.confirmImport()

        assertEquals(1, api.importCalls)
        assertTrue(viewModel.state.value!!.importAttemptFailed)
        assertFalse(viewModel.state.value!!.importing)

        viewModel.cancelPreview()

        assertEquals(null, viewModel.state.value!!.preview)
        assertFalse(viewModel.state.value!!.importAttemptFailed)
        assertEquals(
            "Prévia encerrada. Confira os registros da fazenda antes de reenviar o arquivo.",
            viewModel.state.value!!.messages.last().text
        )
    }

    private fun newViewModel(api: FakeChatbotApi) = ChatbotViewModel(
        RuntimeEnvironment.getApplication(),
        SavedStateHandle(),
        api,
        api
    )

    private fun preview(vararg records: MeasurementImportRecord) = MeasurementImportPreview(
        filename = "historico.csv",
        requiresConfirmation = true,
        records = records.toList()
    )
}
