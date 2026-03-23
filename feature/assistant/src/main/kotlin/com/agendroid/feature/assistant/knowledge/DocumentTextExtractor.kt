package com.agendroid.feature.assistant.knowledge

import android.content.Context
import android.net.Uri
import com.agendroid.core.common.di.IoDispatcher
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class DocumentTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    suspend fun extract(uri: String, sourceType: String): ExtractedKnowledgeContent = withContext(dispatcher) {
        val parsedUri = Uri.parse(uri)
        val fileName = parsedUri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: uri
        val title = fileName.substringBeforeLast('.', fileName)
        val text = openInputStream(uri, parsedUri)?.use { inputStream ->
            if (sourceType.equals("PDF", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)) {
                extractPdfText(inputStream)
            } else {
                inputStream.bufferedReader().use { reader -> reader.readText() }
            }
        }?.trim().orEmpty()

        ExtractedKnowledgeContent(
            title = title.ifBlank { uri },
            text = text,
        )
    }

    private fun openInputStream(rawUri: String, parsedUri: Uri): InputStream? = when (parsedUri.scheme?.lowercase()) {
        "content" -> context.contentResolver.openInputStream(parsedUri)
        "file" -> parsedUri.path?.let(::FileInputStream)
        null -> FileInputStream(rawUri)
        else -> context.contentResolver.openInputStream(parsedUri)
    }

    private fun extractPdfText(inputStream: InputStream): String {
        PDFBoxResourceLoader.init(context)
        return PDDocument.load(inputStream).use { document ->
            PDFTextStripper().getText(document)
        }
    }
}
