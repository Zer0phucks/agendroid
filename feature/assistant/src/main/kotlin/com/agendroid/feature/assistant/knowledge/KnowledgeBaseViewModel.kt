package com.agendroid.feature.assistant.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendroid.core.data.entity.IndexedSourceEntity
import com.agendroid.core.data.repository.IndexedSourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KnowledgeBaseViewModel @Inject constructor(
    private val indexedSourceRepository: IndexedSourceRepository,
    private val knowledgeIngestionScheduler: KnowledgeIngestionScheduler,
) : ViewModel() {

    val sourcesFlow: StateFlow<List<IndexedSourceEntity>> = indexedSourceRepository.sourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onAddDocument(uri: String, title: String) {
        knowledgeIngestionScheduler.enqueueIngest("FILE", uri, title)
    }

    fun onAddUrl(url: String) {
        knowledgeIngestionScheduler.enqueueIngest("URL", url, url)
    }

    fun onDelete(source: IndexedSourceEntity) {
        viewModelScope.launch {
            indexedSourceRepository.delete(source)
            knowledgeIngestionScheduler.enqueueDelete(source.sourceType, source.uri)
        }
    }
}
