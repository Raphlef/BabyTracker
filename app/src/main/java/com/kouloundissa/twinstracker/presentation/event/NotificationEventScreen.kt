package com.kouloundissa.twinstracker.presentation.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import androidx.compose.runtime.collectAsState

@Composable
fun NotificationEventScreen(
    eventId: String,
    viewModel: EventViewModel = hiltViewModel(),
    onDone: () -> Unit
) {
    LaunchedEffect(eventId) {
        viewModel.onNotificationClicked(eventId)
        viewModel.loadEventIntoForm(viewModel.notificationEvent.value!!)
    }

    EventFormDialog(
        initialBabyId = viewModel.notificationEvent.collectAsState().value!!.babyId,
        onDismiss = {
            viewModel.clearEditingEvent()
            viewModel.resetFormState()
            onDone()
        }
    )
}
