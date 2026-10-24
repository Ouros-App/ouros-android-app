package com.ouros.app.feature.secondary.profile

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.ouros.app.R
import java.io.File
import java.io.InputStream
import kotlin.random.Random

class ProfilePhotoStore internal constructor(
    private val filesDir: File,
    private val preferences: SharedPreferences,
    ownerId: Long,
    private val streamOpener: (Uri) -> InputStream? = { null },
    private val legacyClearAction: () -> Unit = {}
) {

    constructor(
        context: Context,
        ownerId: Long
    ) : this(
        filesDir = context.applicationContext.filesDir,
        preferences = context.applicationContext.getSharedPreferences(
            ProfilePhotoScope.forOwner(context.applicationContext.filesDir, ownerId).preferencesName,
            Context.MODE_PRIVATE
        ),
        ownerId = ownerId,
        streamOpener = { uri -> context.applicationContext.contentResolver.openInputStream(uri) },
        legacyClearAction = {
            File(
                context.applicationContext.filesDir,
                LEGACY_PROFILE_DIRECTORY
            ).deleteRecursively()

            context.applicationContext.getSharedPreferences(
                ProfilePhotoScope.PREFERENCES_NAME,
                Context.MODE_PRIVATE
            ).edit().clear().apply()
        }
    )

    private val scope =
        ProfilePhotoScope.forOwner(filesDir, ownerId)

    private val profileDirectory = scope.directory

    private val profilePhoto =
        File(
            profileDirectory,
            PROFILE_PHOTO_NAME
        )

    private val pendingCameraPhoto =
        File(
            profileDirectory,
            PENDING_CAMERA_PHOTO_NAME
        )

    init {
        legacyClearAction()
    }

    private fun safelyDeleteFile(file: File) {
        if (file.exists()) {
            val wasDeleted = file.delete()
            if (!wasDeleted && file.exists()) {
                // file could not be deleted
            }
        }
    }

    fun localPhotoFile(): File? {
        return profilePhoto.takeIf {
            it.exists() && it.length() > 0L
        }
    }

    fun prepareCameraFile(): File {
        createProfileDirectory()

        safelyDeleteFile(pendingCameraPhoto)

        pendingCameraPhoto.createNewFile()
        return pendingCameraPhoto
    }

    fun cancelPendingCameraPhoto() {
        safelyDeleteFile(pendingCameraPhoto)
    }

    fun pendingCameraFile(): File? {
        return pendingCameraPhoto.takeIf {
            it.exists() && it.length() > 0L
        }
    }

    fun commitPhoto(sourceFile: File, remoteUrl: String? = null): Boolean {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return false
        }

        return try {
            createProfileDirectory()

            sourceFile.copyTo(
                target = profilePhoto,
                overwrite = true
            )

            cancelPendingCameraPhoto()

            preferences.edit()
                .putBoolean(KEY_REMOTE_PHOTO_REMOVED, false)
                .putBoolean(KEY_PHOTO_PENDING_UPLOAD, false)
                .putString(KEY_LAST_SYNCED_PHOTO_URL, remoteUrl?.trim()?.takeIf(String::isNotBlank))
                .apply()

            true
        } catch (_: Exception) {
            false
        }
    }

    fun commitPhotoRemoval(): Int? {
        safelyDeleteFile(profilePhoto)
        cancelPendingCameraPhoto()

        val avatarResource = selectedAvatarResource()

        preferences.edit()
            .putBoolean(KEY_REMOTE_PHOTO_REMOVED, true)
            .putBoolean(KEY_PHOTO_PENDING_UPLOAD, false)
            .remove(KEY_LAST_SYNCED_PHOTO_URL)
            .apply()

        return avatarResource
    }

    fun syncWithRemote(remotePhotoUrl: String?) {
        val normalizedUrl = remotePhotoUrl?.trim()?.takeIf(String::isNotBlank)

        if (normalizedUrl == null) {
            safelyDeleteFile(profilePhoto)
            preferences.edit()
                .remove(KEY_LAST_SYNCED_PHOTO_URL)
                .putBoolean(KEY_REMOTE_PHOTO_REMOVED, false)
                .putBoolean(KEY_PHOTO_PENDING_UPLOAD, false)
                .apply()
        } else {
            val lastSyncedUrl = preferences.getString(KEY_LAST_SYNCED_PHOTO_URL, null)
            if (lastSyncedUrl != null && lastSyncedUrl != normalizedUrl) {
                safelyDeleteFile(profilePhoto)
                preferences.edit()
                    .remove(KEY_LAST_SYNCED_PHOTO_URL)
                    .apply()
            }

            if (preferences.getBoolean(KEY_REMOTE_PHOTO_REMOVED, false)) {
                preferences.edit()
                    .putBoolean(KEY_REMOTE_PHOTO_REMOVED, false)
                    .apply()
            }
        }
    }

    fun peekRandomAvatar(): Int? {
        val storedName = preferences.getString(KEY_SELECTED_AVATAR, null)
        if (storedName != null) {
            val storedResource = findAvailableAvatars().firstOrNull { it.name == storedName }
            if (storedResource != null) {
                return storedResource.resourceId
            }
        }

        val avatars = findAvailableAvatars()
        if (avatars.isEmpty()) {
            return null
        }

        return avatars[Random.nextInt(avatars.size)].resourceId
    }

    fun commitCameraPhoto(): Boolean {
        if (
            !pendingCameraPhoto.exists() ||
            pendingCameraPhoto.length() == 0L
        ) {
            cancelPendingCameraPhoto()
            return false
        }

        val committed = commitPhoto(pendingCameraPhoto)
        if (committed) {
            preferences.edit().putBoolean(KEY_PHOTO_PENDING_UPLOAD, true).apply()
        }
        return committed
    }

    fun saveGalleryPhoto(uri: Uri): Boolean {
        return saveGalleryStream { streamOpener(uri) }
    }

    internal fun saveGalleryStream(streamSupplier: () -> InputStream?): Boolean {
        createProfileDirectory()

        val temporaryFile =
            File(
                profileDirectory,
                TEMPORARY_GALLERY_PHOTO_NAME
            )

        return try {
            streamSupplier()
                ?.use { inputStream ->
                    temporaryFile.outputStream()
                        .use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                }
                ?: return false

            if (temporaryFile.length() == 0L) {
                safelyDeleteFile(temporaryFile)
                return false
            }

            val committed = commitPhoto(temporaryFile)
            if (committed) {
                preferences.edit().putBoolean(KEY_PHOTO_PENDING_UPLOAD, true).apply()
            }
            safelyDeleteFile(temporaryFile)
            committed
        } catch (_: Exception) {
            safelyDeleteFile(temporaryFile)
            false
        }
    }

    fun deletePhotoAndSelectRandomAvatar(): Int? {
        return commitPhotoRemoval()
    }

    fun selectedAvatarResource(): Int? {
        val storedName =
            preferences.getString(
                KEY_SELECTED_AVATAR,
                null
            )

        if (storedName != null) {
            val storedResource =
                findAvailableAvatars()
                    .firstOrNull {
                        it.name == storedName
                    }

            if (storedResource != null) {
                return storedResource.resourceId
            }
        }

        val avatars = findAvailableAvatars()

        if (avatars.isEmpty()) {
            return null
        }

        val selectedAvatar =
            avatars[Random.nextInt(avatars.size)]

        preferences.edit()
            .putString(
                KEY_SELECTED_AVATAR,
                selectedAvatar.name
            )
            .apply()

        return selectedAvatar.resourceId
    }

    fun shouldIgnoreRemotePhoto(): Boolean {
        return preferences.getBoolean(
            KEY_REMOTE_PHOTO_REMOVED,
            false
        )
    }

    fun isPhotoPendingUpload(): Boolean {
        return preferences.getBoolean(KEY_PHOTO_PENDING_UPLOAD, false) && localPhotoFile() != null
    }

    fun markPhotoUploadCompleted() {
        preferences.edit()
            .putBoolean(KEY_PHOTO_PENDING_UPLOAD, false)
            .apply()
    }

    fun lastSyncedPhotoUrl(): String? {
        return preferences.getString(KEY_LAST_SYNCED_PHOTO_URL, null)
    }

    private fun findAvailableAvatars(): List<AvatarResource> {
        return R.drawable::class.java.fields
            .asSequence()
            .filter {
                it.name.startsWith(AVATAR_PREFIX)
            }
            .mapNotNull { field ->
                runCatching {
                    AvatarResource(
                        name = field.name,
                        resourceId = field.getInt(null)
                    )
                }.getOrNull()
            }
            .filter {
                it.resourceId != 0
            }
            .sortedBy {
                it.name
            }
            .toList()
    }

    private fun createProfileDirectory() {
        if (!profileDirectory.exists()) {
            profileDirectory.mkdirs()
        }
    }

    private data class AvatarResource(
        val name: String,
        val resourceId: Int
    )

    private companion object {
        const val LEGACY_PROFILE_DIRECTORY = "profile"
        const val PROFILE_PHOTO_NAME = "profile_photo.jpg"
        const val PENDING_CAMERA_PHOTO_NAME = "pending_camera_photo.jpg"
        const val TEMPORARY_GALLERY_PHOTO_NAME = "temporary_gallery_photo.jpg"
        const val KEY_SELECTED_AVATAR = "selected_avatar"
        const val KEY_REMOTE_PHOTO_REMOVED = "remote_photo_removed"
        const val KEY_PHOTO_PENDING_UPLOAD = "photo_pending_upload"
        const val KEY_LAST_SYNCED_PHOTO_URL = "last_synced_photo_url"
        const val AVATAR_PREFIX = "avatar_"
    }
}

internal data class ProfilePhotoScope(
    val directory: File,
    val preferencesName: String
) {
    companion object {
        const val PREFERENCES_NAME = "ouros_profile_photo"
        private const val PROFILE_DIRECTORY = "profile_accounts"

        fun forOwner(filesDir: File, ownerId: Long) =
            ProfilePhotoScope(
                directory = File(filesDir, "$PROFILE_DIRECTORY/$ownerId"),
                preferencesName = "$PREFERENCES_NAME-$ownerId"
            )
    }
}
