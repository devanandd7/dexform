package com.dexform.app.ui.browser

import android.content.Context
import android.net.Uri
import android.webkit.ValueCallback
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexform.app.AppContainer
import com.dexform.app.core.imaging.Requirement
import com.dexform.app.core.imaging.TargetNotReachable
import com.dexform.app.core.requirement.RequirementParser
import com.dexform.app.data.db.SiteRuleEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface UploadState {
    object Idle : UploadState
    data class Choosing(val req: Requirement, val multiple: Boolean, val needsManual: Boolean) : UploadState
    data class Processing(val message: String) : UploadState
    data class Preview(val items: List<Processed>, val req: Requirement) : UploadState
    data class Error(val message: String, val req: Requirement) : UploadState
}

class BrowserViewModel(private val appContainer: AppContainer) : ViewModel() {
    private var pending: ValueCallback<Array<Uri>>? = null
    private var accept = ""
    private var multiple = false
    private var host = ""
    private var key = ""

    private val _state = MutableStateFlow<UploadState>(UploadState.Idle)
    val state: StateFlow<UploadState> = _state

    /** Called from onShowFileChooser. Always resolves an older callback so choosers never get stuck. */
    fun attachChooser(callback: ValueCallback<Array<Uri>>, accept: String, multiple: Boolean, host: String) {
        pending?.onReceiveValue(null)
        pending = callback
        this.accept = accept
        this.multiple = multiple
        this.host = host
    }

    fun onHints(raw: String?) {
        if (pending == null) return
        val hint = parsePageHint(raw)
        key = ruleKey(host, hint)
        viewModelScope.launch {
            val parsed = RequirementParser.parse(hint?.accept?.ifBlank { accept } ?: accept, hint?.hints.orEmpty())
            val cachedEntity = appContainer.siteRuleDao.get(key)
            val cached = cachedEntity?.toRequirement()
            val req = when {
                parsed.confidence >= 0.7f -> parsed
                cached != null -> cached.copy(formats = cached.formats.ifEmpty { parsed.formats })
                else -> parsed
            }
            _state.value = UploadState.Choosing(req, multiple, needsManual = req.confidence < 0.5f && cached == null)
        }
    }

    fun updateRequirement(req: Requirement) {
        val s = _state.value
        if (s is UploadState.Choosing) {
            _state.value = s.copy(req = req, needsManual = false)
        }
    }

    fun onFilesPicked(ctx: Context, uris: List<Uri>, req: Requirement, mergeAll: Boolean = false) {
        if (uris.isEmpty()) return
        _state.value = UploadState.Processing("Preparing your file...")
        viewModelScope.launch {
            try {
                val items = UploadProcessor.process(ctx, uris, req, multiple, mergeAll)
                _state.value = UploadState.Preview(items, req)
                confirm()
            } catch (e: TargetNotReachable) {
                _state.value = UploadState.Error(e.message ?: "Could not make this file fit", req)
            } catch (e: Exception) {
                _state.value = UploadState.Error("Could not read this file", req)
            }
        }
    }

    fun confirm() {
        val s = _state.value as? UploadState.Preview ?: return
        viewModelScope.launch {
            appContainer.siteRuleDao.upsert(SiteRuleEntity.fromRequirement(key, s.req))
        }
        finish(s.items.map { it.uri }.toTypedArray())
    }

    fun cancel() = finish(null)

    private fun finish(result: Array<Uri>?) {
        pending?.onReceiveValue(result)
        pending = null
        _state.value = UploadState.Idle
    }

    override fun onCleared() {
        pending?.onReceiveValue(null)
        pending = null
        super.onCleared()
    }
}
