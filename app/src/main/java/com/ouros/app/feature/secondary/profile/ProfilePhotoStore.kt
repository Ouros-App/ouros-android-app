package com.ouros.app.feature.secondary.profile

import android.content.Context
import android.net.Uri
import com.ouros.app.R
import java.io.File
import kotlin.random.Random

class ProfilePhotoStore(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val preferences =
        appContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    private val profileDirectory =
        File(
            appContext.filesDir,
            PROFILE_DIRECTORY
        )

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

    fun localPhotoFile(): File? {
        return profilePhoto.takeIf {
            it.exists() && it.length() > 0L
        }
    }

    fun prepareCameraFile(): File {
        createProfileDirectory()

        if (pendingCameraPhoto.exists()) {
            pendingCameraPhoto.delete()
        }

        pendingCameraPhoto.createNewFile()
        return pendingCameraPhoto
    }

    fun commitCameraPhoto(): Boolean {
        if (
            !pendingCameraPhoto.exists() ||
            pendingCameraPhoto.length() == 0L
        ) {
            cancelPendingCameraPhoto()
            return false
        }

        return try {
            createProfileDirectory()

            pendingCameraPhoto.copyTo(
                target = profilePhoto,
                overwrite = true
            )

            pendingCameraPhoto.delete()
            markPhotoAsSelected()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun cancelPendingCameraPhoto() {
        if (pendingCameraPhoto.exists()) {
            pendingCameraPhoto.delete()
        }
    }

    fun saveGalleryPhoto(uri: Uri): Boolean {
        createProfileDirectory()

        val temporaryFile =
            File(
                profileDirectory,
                TEMPORARY_GALLERY_PHOTO_NAME
            )

        return try {
            appContext.contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->
                    temporaryFile.outputStream()
                        .use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                }
                ?: return false

            if (temporaryFile.length() == 0L) {
                temporaryFile.delete()
                return false
            }

            temporaryFile.copyTo(
                target = profilePhoto,
                overwrite = true
            )

            temporaryFile.delete()
            markPhotoAsSelected()
            true
        } catch (_: Exception) {
            temporaryFile.delete()
            false
        }
    }

    fun deletePhotoAndSelectRandomAvatar(): Int? {
        profilePhoto.delete()
        pendingCameraPhoto.delete()

        val avatars = findAvailableAvatars()

        if (avatars.isEmpty()) {
            return null
        }

        val selectedAvatar =
            avatars[Random.nextInt(avatars.size)]

        preferences.edit()
            .putBoolean(
                KEY_REMOTE_PHOTO_REMOVED,
                true
            )
            .putString(
                KEY_SELECTED_AVATAR,
                selectedAvatar.name
            )
            .apply()

        return selectedAvatar.resourceId
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

    private fun markPhotoAsSelected() {
        preferences.edit()
            .putBoolean(
                KEY_REMOTE_PHOTO_REMOVED,
                false
            )
            .apply()
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
        const val PREFERENCES_NAME =
            "ouros_profile_photo"

        const val PROFILE_DIRECTORY =
            "profile"

        const val PROFILE_PHOTO_NAME =
            "profile_photo.jpg"

        const val PENDING_CAMERA_PHOTO_NAME =
            "pending_camera_photo.jpg"

        const val TEMPORARY_GALLERY_PHOTO_NAME =
            "temporary_gallery_photo.jpg"

        const val KEY_SELECTED_AVATAR =
            "selected_avatar"

        const val KEY_REMOTE_PHOTO_REMOVED =
            "remote_photo_removed"

        const val AVATAR_PREFIX =
            "avatar_"
    }
}