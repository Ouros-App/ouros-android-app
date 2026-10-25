package com.ouros.app.feature.secondary.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.notification.NotificationItemResponse
import com.ouros.app.data.notification.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val notifications: List<NotificationItemResponse> = emptyList(),
    val unreadCount: Int = 0,
    val errorMessage: String? = null
)

class NotificationViewModel(
    private val notificationRepository: NotificationRepository = NotificationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    fun loadNotifications() {
        if (_uiState.value.notifications.isNotEmpty()) {
            refreshNotifications()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = notificationRepository.getNotifications(page = 0, size = 50)
            result.fold(
                onSuccess = { page ->
                    val unread = page.content.count { !it.isRead }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            notifications = page.content,
                            unreadCount = unread,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Erro ao carregar notificações"
                        )
                    }
                }
            )
        }
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val result = notificationRepository.getNotifications(page = 0, size = 50)
            result.fold(
                onSuccess = { page ->
                    val unread = page.content.count { !it.isRead }
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            notifications = page.content,
                            unreadCount = unread,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = error.message ?: "Erro ao atualizar notificações"
                        )
                    }
                }
            )
        }
    }

    fun markAsRead(item: NotificationItemResponse) {
        if (item.isRead) return

        // Optimistic UI update
        _uiState.update { state ->
            val updatedList = state.notifications.map {
                if (it.id == item.id) it.copy(isRead = true) else it
            }
            state.copy(
                notifications = updatedList,
                unreadCount = maxOf(0, state.unreadCount - 1)
            )
        }

        viewModelScope.launch {
            notificationRepository.markAsRead(item.id)
        }
    }

    fun markAllAsRead() {
        // Optimistic UI update
        _uiState.update { state ->
            val updatedList = state.notifications.map { it.copy(isRead = true) }
            state.copy(notifications = updatedList, unreadCount = 0)
        }

        viewModelScope.launch {
            notificationRepository.markAllAsRead()
        }
    }

    class Factory(
        private val notificationRepository: NotificationRepository = NotificationRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotificationViewModel(notificationRepository) as T
        }
    }
}
