package alibarznji.qasa.screens.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import alibarznji.qasa.vault.VaultPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Holds in-flight editor draft across vault NavHost destinations / sheets. */
@HiltViewModel
class VaultEditorHolder @Inject constructor() : ViewModel() {
    var draft by mutableStateOf<VaultPayload?>(null)
        private set
    var existingId by mutableStateOf<String?>(null)
        private set

    fun beginCreate(payload: VaultPayload) {
        existingId = null
        draft = payload
    }

    fun beginEdit(itemId: String, payload: VaultPayload) {
        existingId = itemId
        draft = payload
    }

    fun clear() {
        draft = null
        existingId = null
    }
}
