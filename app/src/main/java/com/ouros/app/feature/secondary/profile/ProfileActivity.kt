package com.ouros.app.feature.secondary.profile

import android.app.Dialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ouros.app.R
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.SecureRefreshTokenStore
import com.ouros.app.data.farmowner.FarmOwnerNetwork
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.farmowner.FarmOwnerResponse
import com.ouros.app.data.session.FarmSessionStore
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import android.view.WindowManager
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import com.ouros.app.feature.onboarding.welcome.WelcomeActivity
import com.yalantis.ucrop.UCrop
import java.io.File

class ProfileActivity : AppCompatActivity(), ProfileViewModel.Listener {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var photoStore: ProfilePhotoStore

    private lateinit var inputName: TextInputLayout
    private lateinit var inputEmail: TextInputLayout
    private lateinit var inputPassword: TextInputLayout
    private lateinit var inputCpf: TextInputLayout
    private lateinit var inputTelephone: TextInputLayout

    private lateinit var editName: TextInputEditText
    private lateinit var editEmail: TextInputEditText
    private lateinit var editPassword: TextInputEditText
    private lateinit var editCpf: TextInputEditText
    private lateinit var editTelephone: TextInputEditText

    private lateinit var imgProfilePhoto: ImageView
    private lateinit var btnSave: Button

    private val photoExecutor = Executors.newSingleThreadExecutor()
    private val photoRequestSequence = AtomicInteger(0)
    private var pendingCameraUri: Uri? = null
    private var pendingCropOutputFile: File? = null

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) {
        wasSaved ->
            val sourceUri = pendingCameraUri
            pendingCameraUri = null

            if (wasSaved && sourceUri != null) {
                startPhotoCrop(sourceUri)
            } else {
                photoStore.cancelPendingCameraPhoto()
            }
        }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) {
        sourceUri ->
            if (sourceUri != null) {
                startPhotoCrop(sourceUri)
            }
        }

    private val cropPhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    val croppedUri = result.data?.let {
                        UCrop.getOutput(it)
                    }

                    if (croppedUri != null) {
                        saveCroppedPhoto(croppedUri)

                    } else {
                        clearPendingCrop()

                        Toast.makeText(
                            this, "Não foi possível obter a foto cortada.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                UCrop.RESULT_ERROR -> {
                    val error = result.data?.let {
                        UCrop.getError(it)
                    }

                    clearPendingCrop()

                    Toast.makeText(
                        this, error?.localizedMessage
                            ?: "Não foi possível cortar a foto.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                else -> {
                    clearPendingCrop()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        photoStore = ProfilePhotoStore(applicationContext)

        bindViews()
        configureViewModel()
        configureMasks()
        configureActions()

        viewModel.loadProfile()
    }

    private fun bindViews() {
        inputName = findViewById(R.id.inputName)
        inputEmail = findViewById(R.id.inputEmail)
        inputPassword = findViewById(R.id.inputPassword)
        inputCpf = findViewById(R.id.inputCPF)
        inputTelephone = findViewById(R.id.inputTelephone)

        editName = findViewById(R.id.editName)
        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        editCpf = findViewById(R.id.editCPF)
        editTelephone = findViewById(R.id.editTelephone)

        imgProfilePhoto = findViewById(R.id.imgProfilePhoto)
        btnSave = findViewById(R.id.btnSave)
    }

    private fun configureViewModel() {
        val farmOwnerRepository = FarmOwnerRepository(
            api = FarmOwnerNetwork.api,
            sessionManager = AppSession.sessionManager
        )

        val factory = ProfileViewModelFactory(
            farmOwnerRepository = farmOwnerRepository
        )

        viewModel = ViewModelProvider(this,factory)[ProfileViewModel::class.java]
        viewModel.setListener(this)
    }

    private fun configureActions() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnEditPhoto).setOnClickListener { showPhotoPopup() }
        findViewById<View>(R.id.txtLogout).setOnClickListener { logout() }

        btnSave.setOnClickListener {
            clearErrors()

            viewModel.saveProfile(
                name = editName.text ?.toString().orEmpty(),
                email = editEmail.text ?.toString().orEmpty(),
                password = editPassword.text ?.toString().orEmpty(),
                cpf = editCpf.text ?.toString().orEmpty(),
                telephone = editTelephone.text ?.toString().orEmpty()
            )
        }
    }

    private fun showPhotoPopup() {
        val dialog = Dialog(this)

        dialog.setContentView(R.layout.popup_photo)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        dialog.findViewById<View>(R.id.btnTakePhoto).setOnClickListener {
            dialog.dismiss()
            openCamera()
        }

        dialog.findViewById<View>(R.id.btnChooseGallery).setOnClickListener {
            dialog.dismiss()
            openGallery()
        }

        dialog.findViewById<View>(R.id.btnDeletePhoto).setOnClickListener {
            dialog.dismiss()
            deleteProfilePhoto()
        }

        dialog.show()

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            val windowAttributes = attributes
            windowAttributes.blurBehindRadius = 35
            windowAttributes.dimAmount = 0.18f
            attributes = windowAttributes
        }
    }

    private fun openCamera() {
        val cameraFile = photoStore.prepareCameraFile()
        val cameraUri = FileProvider.getUriForFile(this,"$packageName.fileprovider", cameraFile)

        pendingCameraUri = cameraUri
        takePhotoLauncher.launch(cameraUri)
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun startPhotoCrop(sourceUri: Uri) {
        val cropDirectory = File(cacheDir, "profile_crop").apply {
            if (!exists()) {
                mkdirs()
            }
        }

        val outputFile = File(cropDirectory, "cropped_profile_${System.currentTimeMillis()}.jpg")
        pendingCropOutputFile = outputFile

        val destinationUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", outputFile)

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)

            setCompressionQuality(90)
            setFreeStyleCropEnabled(false)
            setHideBottomControls(false)
            setToolbarTitle("Ajustar foto")
        }

        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1600, 1600)
            .withOptions(options)
            .start(this, cropPhotoLauncher)
    }

    private fun saveCroppedPhoto(croppedUri: Uri) {
        val saved = photoStore.saveGalleryPhoto(croppedUri)
        clearPendingCrop()

        if (saved) {
            showLocalPhoto()
            Toast.makeText(this, "Foto salva.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Não foi possível salvar a foto.", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearPendingCrop() {
        photoStore.cancelPendingCameraPhoto()

        pendingCropOutputFile
            ?.takeIf(File::exists)
            ?.delete()

        pendingCropOutputFile = null
        pendingCameraUri = null
    }

    private fun deleteProfilePhoto() {
        val avatarResource =photoStore.deletePhotoAndSelectRandomAvatar()

        if (avatarResource == null) {
            Toast.makeText(this, "Nenhum avatar_ foi encontrado.", Toast.LENGTH_LONG).show()
            return
        }

        showAvatarColor(avatarResource)

        Toast.makeText(this, "Foto removida.", Toast.LENGTH_SHORT).show()
    }

    private fun configureMasks() {
        installMask(
            editText = editCpf,
            maxDigits = 11,
            formatter = ::formatCpf
        )

        installMask(
            editText = editTelephone,
            maxDigits = 13,
            formatter = ::formatTelephone
        )
    }

    private fun installMask(editText: TextInputEditText, maxDigits: Int, formatter: (String) -> String) {
        editText.doAfterTextChanged { editable ->
            val currentText = editable?.toString().orEmpty()

            val digits = currentText
                .filter(Char::isDigit)
                .take(maxDigits)

            val formatted = formatter(digits)

            if (currentText != formatted) {
                editText.setText(formatted)
                editText.setSelection(formatted.length)
            }
        }
    }

    override fun onProfileLoadingChanged(isLoading: Boolean) {
        btnSave.isEnabled = !isLoading
        editName.isEnabled = !isLoading
        editEmail.isEnabled = !isLoading
        editPassword.isEnabled = !isLoading
        editCpf.isEnabled = !isLoading
        editTelephone.isEnabled = !isLoading
    }

    override fun onProfileLoaded(profile: FarmOwnerResponse) {
        renderProfile(profile)
    }

    override fun onProfileSaved(profile: FarmOwnerResponse) {
        renderProfile(profile)
        editPassword.text?.clear()

        Toast.makeText(this, "Perfil atualizado com sucesso.", Toast.LENGTH_SHORT).show()
    }

    override fun onProfileErrors(
        nameMessage: String?,
        emailMessage: String?,
        passwordMessage: String?,
        cpfMessage: String?,
        telephoneMessage: String?
    ) {
        inputName.error = nameMessage
        inputEmail.error = emailMessage
        inputPassword.error = passwordMessage
        inputCpf.error = cpfMessage
        inputTelephone.error = telephoneMessage
    }

    override fun onProfileError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onProfileSessionExpired() {
        Toast.makeText(this, "Sua sessão expirou.", Toast.LENGTH_LONG).show()
        logout()
    }

    private fun renderProfile(profile: FarmOwnerResponse) {
        editName.setText(profile.name.orEmpty())
        editEmail.setText(profile.email.orEmpty())

        editCpf.setText(
            formatCpf(profile.documentNumber
                .orEmpty()
                .filter(Char::isDigit)
            )
        )

        editTelephone.setText(
            formatTelephone(profile.telephone
                .orEmpty()
                .filter(Char::isDigit)
            )
        )

        editPassword.text?.clear()
        renderProfilePhoto(profile.photoUrl)
    }

    private fun renderProfilePhoto(remotePhotoUrl: String?) {
        if (showLocalPhoto()) {
            return
        }

        if (photoStore.shouldIgnoreRemotePhoto()) {
            showDefaultAvatar()
            return
        }

        if (!remotePhotoUrl.isNullOrBlank()) {
            loadRemotePhoto(remotePhotoUrl)
            return
        }

        showDefaultAvatar()
    }

    private fun showLocalPhoto(): Boolean {
        val localPhoto = photoStore.localPhotoFile()
            ?: return false

        val bitmap = BitmapFactory.decodeFile(localPhoto.absolutePath)
            ?: return false

        photoRequestSequence.incrementAndGet()

        imgProfilePhoto.setBackgroundColor(Color.TRANSPARENT)
        imgProfilePhoto.scaleType = ImageView.ScaleType.CENTER_CROP
        imgProfilePhoto.setImageBitmap(bitmap)

        return true
    }

    private fun showDefaultAvatar() {
        val avatarResource = photoStore.selectedAvatarResource()
            ?: return

        showAvatarColor(avatarResource)
    }

    private fun showAvatarColor(avatarResource: Int) {
        photoRequestSequence.incrementAndGet()

        imgProfilePhoto.setImageDrawable(null)
        imgProfilePhoto.setBackgroundColor(colorForAvatar(avatarResource))
    }

    private fun colorForAvatar(avatarResource: Int): Int {
        return when (avatarResource) {
            R.drawable.avatar_yellow -> "#D8A23A".toColorInt()
            R.drawable.avatar_purple -> "#993AD8".toColorInt()
            R.drawable.avatar_orange -> "#D85F3A".toColorInt()
            R.drawable.avatar_green -> "#3AD83F".toColorInt()
            R.drawable.avatar_blue -> "#3A47D8".toColorInt()
            else -> "#D8A23A".toColorInt()
        }
    }

    private fun loadRemotePhoto(photoUrl: String) {
        val requestId = photoRequestSequence.incrementAndGet()

        photoExecutor.execute {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(photoUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = PHOTO_TIMEOUT_MILLIS
                connection.readTimeout = PHOTO_TIMEOUT_MILLIS
                connection.instanceFollowRedirects = true
                connection.doInput = true
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    return@execute
                }

                val bitmap = connection.inputStream.use {
                    BitmapFactory.decodeStream(it)}
                        ?: return@execute

                runOnUiThread {
                    if (requestId == photoRequestSequence.get() && !isFinishing && !isDestroyed) {
                        imgProfilePhoto.setBackgroundColor(Color.TRANSPARENT)
                        imgProfilePhoto.scaleType = ImageView.ScaleType.CENTER_CROP
                        imgProfilePhoto.setImageBitmap(bitmap)
                    }
                }

            } catch (_: Exception) {
                runOnUiThread {
                    if (requestId == photoRequestSequence.get() && !isFinishing && !isDestroyed) {
                        showDefaultAvatar()
                    }
                }

            } finally { connection?.disconnect() }
        }
    }

    private fun clearErrors() {
        inputName.error = null
        inputEmail.error = null
        inputPassword.error = null
        inputCpf.error = null
        inputTelephone.error = null
    }

    private fun logout() {
        AppSession.sessionManager.clear()
        SecureRefreshTokenStore(applicationContext).clear()
        FarmSessionStore(applicationContext).clearFarmId()

        startActivity(Intent( this, WelcomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun formatCpf(digits: String): String {
        val value = digits
            .filter(Char::isDigit)
            .take(11)

        return buildString {
            value.forEachIndexed { index, char ->
                append(char)

                when (index) {
                    2, 5 -> {if (index < value.lastIndex) { append('.') }}
                    8 -> { if (index < value.lastIndex) { append('-') }}
                }
            }
        }
    }

    private fun formatTelephone(digits: String): String {
        val value = digits
            .filter(Char::isDigit)
            .take(13)

        if (value.isEmpty()) {
            return ""
        }

        if (value.length <= 2) {
            return "($value"
        }

        val areaCode = value.take(2)
        val number = value.drop(2)

        val prefixLength = if (number.length <= 8) {
            minOf(4, number.length)
        } else {
            minOf(5, number.length)
        }

        val prefix = number.take(prefixLength)
        val suffix = number.drop(prefixLength)

        return buildString {
            append('(')
            append(areaCode)
            append(") ")
            append(prefix)

            if (suffix.isNotEmpty()) {
                append('-')
                append(suffix)
            }
        }
    }

    override fun onDestroy() {
        viewModel.clearListener(this)
        photoRequestSequence.incrementAndGet()
        photoExecutor.shutdownNow()

        super.onDestroy()
    }

    private companion object {
        const val PHOTO_TIMEOUT_MILLIS = 10_000
    }
}
