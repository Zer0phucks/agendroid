// core/data/src/main/kotlin/com/agendroid/core/data/vector/VectorResult.kt
package com.agendroid.core.data.vector

/** A result from a VectorStore similarity query. */
data class VectorResult(
    /** Matches ChunkEntity.id in the Room database. */
    val chunkId: Long,
    /** L2 distance from the query embedding — lower is more similar. */
    val distance: Float,
)
