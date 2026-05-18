/*
 * PermissionsViewModel.kt — UI state holder for the permissions screen.
 *
 * Holds a Map<PermissionCardId, PermissionCardStatus> as `StateFlow` and
 * exposes a derived `allGranted` flow for the "Continuer" button enable state.
 *
 * The Fragment recomputes the status of each card from the live system
 * state on every `onResume()` (so e.g. if the user toggled a permission off
 * in Settings and came back, the UI reflects it).
 *
 * No persistence — this state is purely derived from system permissions, no
 * Room or DataStore needed.
 */
package be.heyman.android.jemmapassdemo.ui.permissions

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

@HiltViewModel
class PermissionsViewModel @Inject constructor() : ViewModel() {

    /**
     * Status of every card. Initially all PENDING. Updated by the Fragment
     * via [setStatuses] whenever a system check happens.
     */
    private val _statuses = MutableStateFlow<Map<PermissionCardId, PermissionCardStatus>>(
        PermissionCardId.entries.associateWith { PermissionCardStatus.PENDING }
    )
    val statuses: StateFlow<Map<PermissionCardId, PermissionCardStatus>> =
        _statuses.asStateFlow()

    /**
     * True iff every card is in [PermissionCardStatus.GRANTED]. Drives the
     * "Continuer" button enabled state in the Fragment.
     */
    val allGranted: kotlinx.coroutines.flow.Flow<Boolean> =
        _statuses.map { map -> map.values.all { it == PermissionCardStatus.GRANTED } }

    /**
     * Number of currently granted cards / total cards. Drives the human
     * summary line "X / 5 accordées".
     */
    val grantedCount: kotlinx.coroutines.flow.Flow<Pair<Int, Int>> =
        _statuses.map { map ->
            val granted = map.values.count { it == PermissionCardStatus.GRANTED }
            granted to map.size
        }

    /**
     * Replace all card statuses at once. Called by the Fragment after each
     * system check (onResume, after a permission request result, etc.).
     */
    fun setStatuses(new: Map<PermissionCardId, PermissionCardStatus>) {
        _statuses.value = new
    }

    /**
     * Convenience for one-off update (e.g. after we open Settings and the
     * user comes back, but we already have data on the others).
     */
    fun updateStatus(id: PermissionCardId, status: PermissionCardStatus) {
        _statuses.value = _statuses.value.toMutableMap().also { it[id] = status }
    }
}
