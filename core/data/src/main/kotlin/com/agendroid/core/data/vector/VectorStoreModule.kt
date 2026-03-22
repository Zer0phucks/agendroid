// core/data/src/main/kotlin/com/agendroid/core/data/vector/VectorStoreModule.kt
package com.agendroid.core.data.vector

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VectorStoreModule {

    @Provides
    @Singleton
    fun provideVectorStore(@ApplicationContext context: Context): VectorStore =
        VectorStore(context)
}
