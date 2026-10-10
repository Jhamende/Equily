package be.equily.app

import android.app.Application
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import be.equily.app.data.*
import be.equily.app.domain.InvoiceCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val android.content.Context.preferences by preferencesDataStore("settings")
data class Preferences(val language: String = "fr", val theme: String = "system", val lock: Boolean = false)

class EquilyModel(application: Application) : AndroidViewModel(application) {
    val repository = LocalRepository(application)
    val attachments = AttachmentStore(application, repository)
    val backups = BackupStore(application, repository, attachments)
    val reports = Reports(application)
    val snapshot = MutableStateFlow(Snapshot(emptyList()))
    val ready = MutableStateFlow(false)
    val preferencesReady = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<Int?>(null)
    val locked = MutableStateFlow(true)
    val preferences = application.preferences.data.map {
        Preferences(it[stringPreferencesKey("language")] ?: "fr", it[stringPreferencesKey("theme")] ?: "system", it[booleanPreferencesKey("lock")] ?: false)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())
    var scanDocument: Document? = null
    var scanCandidate: InvoiceCandidate? = null
    var scanFailed = false
    var scanDuplicate = false
    init {
        viewModelScope.launch {
            application.preferences.data.collect {
                val enabled = it[booleanPreferencesKey("lock")] ?: false
                if (!preferencesReady.value) locked.value = enabled
                else if (!enabled) locked.value = false
                preferencesReady.value = true
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.refreshRecurring()
            repository.snapshots.collect { snapshot.value = it; ready.value = true }
        }
    }
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            try { repository.refreshRecurring() } catch (_: Exception) { message.value = R.string.error }
        }
    }
    fun perform(after: () -> Unit = {}, notify: Boolean = true, action: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) { action() }
                if (notify) message.value = R.string.success
                after()
            } catch (_: Exception) { message.value = R.string.error }
            finally { busy.value = false }
        }
    }
    fun preference(key: String, value: String) = perform {
        getApplication<Application>().preferences.edit { it[stringPreferencesKey(key)] = value }
    }
    fun lockEnabled(enabled: Boolean) = perform {
        getApplication<Application>().preferences.edit { it[booleanPreferencesKey("lock")] = enabled }
    }
    fun scan(uri: Uri, after: () -> Unit) = perform(after, notify = false) {
        val document = attachments.import(uri, null)
        scanDocument = document
        scanDuplicate = repository.snapshot().documents.any { it.hash == document.hash && it.chargeId != null }
        scanCandidate = null
        scanFailed = false
        try { scanCandidate = attachments.extract(document) } catch (_: Exception) { scanFailed = true }
    }
}
