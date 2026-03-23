package com.agendroid.feature.assistant.knowledge

import android.net.Uri
import com.agendroid.core.common.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

@Singleton
class UrlContentFetcher @Inject constructor(
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    suspend fun fetch(url: String): ExtractedKnowledgeContent = withContext(dispatcher) {
        val document = Jsoup.connect(url)
            .userAgent("Agendroid/0.1")
            .timeout(10_000)
            .get()

        val title = document.title().ifBlank { Uri.parse(url).host ?: url }
        val text = document.body()?.text().orEmpty().ifBlank { document.text() }

        ExtractedKnowledgeContent(
            title = title,
            text = text.trim(),
        )
    }
}
