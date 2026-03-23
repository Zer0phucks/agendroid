package com.agendroid.feature.assistant.knowledge

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class KnowledgeIngestionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: KnowledgeIngestionRunner,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val sourceType = inputData.getString(KEY_SOURCE_TYPE) ?: return Result.failure()
        val uri = inputData.getString(KEY_URI) ?: return Result.failure()
        val request = KnowledgeIngestionRequest(
            sourceType = sourceType,
            uri = uri,
            title = inputData.getString(KEY_TITLE),
        )

        return try {
            when (action) {
                ACTION_INGEST -> runner.ingest(request)
                ACTION_DELETE -> runner.delete(request)
                else -> return Result.failure()
            }
            Result.success()
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("KnowledgeIngestion", "Bad ingestion input for $uri", e)
            Result.failure()
        } catch (e: Exception) {
            android.util.Log.e("KnowledgeIngestion", "Knowledge ingestion failed for $uri", e)
            Result.retry()
        }
    }

    companion object {
        const val ACTION_INGEST = "INGEST"
        const val ACTION_DELETE = "DELETE"

        const val KEY_ACTION = "action"
        const val KEY_SOURCE_TYPE = "source_type"
        const val KEY_URI = "uri"
        const val KEY_TITLE = "title"
    }
}
