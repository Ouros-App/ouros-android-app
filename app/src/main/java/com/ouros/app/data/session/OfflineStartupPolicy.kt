package com.ouros.app.data.session

/** Decides whether a previously authenticated user may enter using local data only. */
object OfflineStartupPolicy {
    fun canOpenMain(
        isOnline: Boolean,
        hasSavedRefreshToken: Boolean,
        ownerId: Long?,
        farmId: Long?
    ): Boolean =
        !isOnline &&
            hasSavedRefreshToken &&
            ownerId != null &&
            ownerId > 0L &&
            farmId != null &&
            farmId > 0L
}
