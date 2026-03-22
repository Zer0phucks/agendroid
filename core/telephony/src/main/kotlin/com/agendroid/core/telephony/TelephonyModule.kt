package com.agendroid.core.telephony

import android.content.Context
import com.agendroid.core.voice.KokoroEngine
import com.agendroid.core.voice.WhisperEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TelephonyModule {

    @Provides
    @Singleton
    fun provideSpeechRecognizer(
        @ApplicationContext context: Context,
    ): TelephonyCoordinator.SpeechRecognizer {
        val whisperEngine = WhisperEngine(context)
        return object : TelephonyCoordinator.SpeechRecognizer {
            override suspend fun load() {
                whisperEngine.load()
            }

            override suspend fun transcribe(pcm: ShortArray): String = whisperEngine.transcribe(pcm)
        }
    }

    @Provides
    @Singleton
    fun provideSpeechSynthesizer(
        @ApplicationContext context: Context,
    ): TelephonyCoordinator.SpeechSynthesizer {
        val kokoroEngine = KokoroEngine(context)
        return object : TelephonyCoordinator.SpeechSynthesizer {
            override suspend fun load() {
                kokoroEngine.load()
            }

            override suspend fun synthesize(text: String): FloatArray = kokoroEngine.synthesize(text)
        }
    }

    @Provides
    @Singleton
    fun provideAiProvider(connector: AiServiceConnector): TelephonyCoordinator.AiProvider = connector
}
