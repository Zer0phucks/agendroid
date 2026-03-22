package com.agendroid.core.embeddings

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for :core:embeddings.
 *
 * [EmbeddingModel] and [TextChunker] are both @Singleton @Inject-constructor classes;
 * Hilt generates their bindings automatically. This module exists to anchor the
 * component installation and to serve as an extension point for future swappable
 * embedding backends (e.g., a larger model in a future plan).
 */
@Module
@InstallIn(SingletonComponent::class)
object EmbeddingsModule
