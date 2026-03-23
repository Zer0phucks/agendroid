package com.agendroid.feature.assistant.knowledge

import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import javax.inject.Inject
import javax.inject.Singleton

fun interface WorkRequestEnqueuer {
    fun enqueue(request: OneTimeWorkRequest)
}

@Singleton
class KnowledgeIngestionScheduler @Inject constructor(
    private val workRequestEnqueuer: WorkRequestEnqueuer,
) {

    fun enqueueIngest(
        sourceType: String,
        uri: String,
        title: String? = null,
    ) {
        workRequestEnqueuer.enqueue(
            buildRequest(
                action = KnowledgeIngestionWorker.ACTION_INGEST,
                sourceType = sourceType,
                uri = uri,
                title = title,
            )
        )
    }

    fun enqueueDelete(
        sourceType: String,
        uri: String,
    ) {
        workRequestEnqueuer.enqueue(
            buildRequest(
                action = KnowledgeIngestionWorker.ACTION_DELETE,
                sourceType = sourceType,
                uri = uri,
                title = null,
            )
        )
    }

    private fun buildRequest(
        action: String,
        sourceType: String,
        uri: String,
        title: String?,
    ): OneTimeWorkRequest {
        val inputData = workDataOf(
            KnowledgeIngestionWorker.KEY_ACTION to action,
            KnowledgeIngestionWorker.KEY_SOURCE_TYPE to sourceType,
            KnowledgeIngestionWorker.KEY_URI to uri,
            KnowledgeIngestionWorker.KEY_TITLE to title,
        )

        return OneTimeWorkRequestBuilder<KnowledgeIngestionWorker>()
            .setInputData(inputData)
            .addTag("knowledge_ingestion")
            .build()
    }
}
