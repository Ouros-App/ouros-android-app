package com.ouros.app.feature.secondary.profile

import android.content.Context
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.AuthNetwork
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SecureRefreshTokenStore
import com.ouros.app.data.cloudinary.CloudinaryRepository
import com.ouros.app.data.farmowner.FarmOwnerNetwork
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.farmowner.FarmOwnerResponse
import com.ouros.app.data.farmowner.FarmOwnerUpdateRequest
import com.ouros.app.data.local.OfflineStore
import com.ouros.app.data.local.OfflineSynchronizer
import com.ouros.app.data.session.FarmSessionStore

interface OfflineProfileSaver {
    fun savePending(
        ownerId: Long,
        currentProfile: FarmOwnerResponse,
        request: FarmOwnerUpdateRequest,
        photoChange: ProfilePhotoChange
    ): FarmOwnerResponse
}

class ProfileOfflineCoordinator(context: Context) : OfflineProfileSaver, OfflineSynchronizer {
    private val applicationContext = context.applicationContext
    private val offlineStore = OfflineStore(applicationContext)
    private val farmSessionStore = FarmSessionStore(applicationContext)
    private val cloudinaryRepository = CloudinaryRepository()
    private val farmOwnerRepository: FarmOwnerRepository

    init {
        val authRepository = AuthRepository(
            api = AuthNetwork.api,
            refreshTokenStore = SecureRefreshTokenStore(applicationContext),
            sessionManager = AppSession.sessionManager
        )
        farmOwnerRepository = FarmOwnerRepository(
            api = FarmOwnerNetwork.api,
            sessionManager = AppSession.sessionManager,
            refreshSession = authRepository::refreshSession,
            clearSession = authRepository::clearSession,
            offlineStore = offlineStore,
            farmSessionStore = farmSessionStore
        )
    }

    override fun savePending(
        ownerId: Long,
        currentProfile: FarmOwnerResponse,
        request: FarmOwnerUpdateRequest,
        photoChange: ProfilePhotoChange
    ): FarmOwnerResponse {
        val photoStore = ProfilePhotoStore(applicationContext, ownerId)
        val photoOperation = when (photoChange) {
            is ProfilePhotoChange.UploadNew -> {
                check(photoStore.stagePhotoForUpload(photoChange.file, currentProfile.photoUrl)) {
                    "Não foi possível salvar a foto neste aparelho."
                }
                PendingPhotoOperation.UPLOAD
            }
            is ProfilePhotoChange.Remove -> {
                photoStore.commitPhotoRemoval()
                PendingPhotoOperation.REMOVE
            }
            is ProfilePhotoChange.KeepCurrent -> PendingPhotoOperation.KEEP
        }

        val optimisticProfile = currentProfile.copy(
            name = request.name,
            email = request.email,
            telephone = request.telephone,
            photoUrl = if (photoOperation == PendingPhotoOperation.REMOVE) "" else currentProfile.photoUrl
        )
        offlineStore.cache(profileCacheKey(ownerId), optimisticProfile)
        offlineStore.enqueueReplacing(
            area = AREA,
            operation = UPDATE,
            entityKey = ownerId.toString(),
            value = PendingProfileUpdate(ownerId, request, photoOperation)
        )
        return optimisticProfile
    }

    override suspend fun synchronize() {
        for (operation in offlineStore.pending(AREA)) {
            val pending = runCatching {
                offlineStore.decode(operation.payload, PendingProfileUpdate::class.java)
            }.getOrNull()
            if (pending == null) {
                offlineStore.complete(operation.id)
                continue
            }

            if (farmSessionStore.ownerId() != pending.ownerId) continue
            offlineStore.markAttempt(operation.id)

            val photoStore = ProfilePhotoStore(applicationContext, pending.ownerId)
            var request = pending.request
            var uploadedPhotoUrl: String? = null
            when (pending.photoOperation) {
                PendingPhotoOperation.UPLOAD -> {
                    val localPhoto = photoStore.localPhotoFile() ?: return
                    val upload = cloudinaryRepository.uploadImage(localPhoto)
                    if (upload.isFailure) return
                    uploadedPhotoUrl = upload.getOrNull() ?: return
                    request = request.copy(photoUrl = uploadedPhotoUrl)
                }
                PendingPhotoOperation.REMOVE -> request = request.copy(photoUrl = "")
                PendingPhotoOperation.KEEP -> Unit
            }

            when (farmOwnerRepository.update(pending.ownerId, request)) {
                is AuthResult.Success -> {
                    if (pending.photoOperation == PendingPhotoOperation.UPLOAD) {
                        photoStore.markPhotoUploadCompleted(uploadedPhotoUrl)
                    } else if (pending.photoOperation == PendingPhotoOperation.REMOVE) {
                        photoStore.markPhotoRemovalCompleted()
                    }
                    offlineStore.complete(operation.id)
                }
                is AuthResult.Error -> return
            }
        }
    }

    private fun profileCacheKey(ownerId: Long) = "farm-owner:$ownerId"

    private data class PendingProfileUpdate(
        val ownerId: Long,
        val request: FarmOwnerUpdateRequest,
        val photoOperation: PendingPhotoOperation
    )

    private enum class PendingPhotoOperation { KEEP, UPLOAD, REMOVE }

    private companion object {
        const val AREA = "profile"
        const val UPDATE = "update"
    }
}
