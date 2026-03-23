package com.agendroid.core.ai

import android.content.ComponentName
import android.content.Context
import android.os.Binder
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AiServiceClient].
 *
 * The injectable [AiServiceClient.doBind] / [AiServiceClient.doUnbind] lambdas are replaced
 * with fakes that deliver a stub [AiServiceInterface] synchronously without requiring a real
 * Android service or [AiCoreService.LocalBinder] (an inner class that can only be constructed
 * with a live [AiCoreService] instance).
 *
 * The secondary binder resolution path in [AiServiceClient] — `binder as? AiServiceInterface`
 * — is used here via [FakeBinder], which extends [android.os.Binder] and also implements
 * [AiServiceInterface].
 */
class AiServiceClientTest {

    /**
     * A fake [android.os.Binder] that also implements [AiServiceInterface] so that the
     * secondary resolution path in [AiServiceClient.getService] picks it up without needing
     * a real [AiCoreService.LocalBinder].
     */
    private inner class FakeBinder : Binder(), AiServiceInterface {
        override fun isModelAvailable(): Boolean = true
        override val resourceState: Flow<ResourceState> = emptyFlow()
        override suspend fun generateResponse(
            userQuery: String,
            contactFilter: String?,
            conversationHistory: List<String>,
            onToken: (String, Boolean) -> Unit,
        ): String = ""
    }

    private val fakeBinder = FakeBinder()

    /**
     * Installs fake [doBind]/[doUnbind] lambdas on [client].
     * Returns a lambda that reports how many times [doBind] was called.
     */
    private fun installFakeBind(client: AiServiceClient): () -> Int {
        var bindCount = 0
        client.doBind = { _, conn, _ ->
            bindCount++
            conn.onServiceConnected(mockk<ComponentName>(), fakeBinder)
            true
        }
        client.doUnbind = { _ -> /* no-op */ }
        return { bindCount }
    }

    private fun makeClient(): AiServiceClient =
        AiServiceClient(mockk<Context>(relaxed = true))

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `getService returns the interface from the binder`() = runTest {
        val client = makeClient()
        installFakeBind(client)

        val result = client.getService()

        // FakeBinder implements AiServiceInterface — same instance must be returned.
        assertSame(fakeBinder, result)
    }

    @Test
    fun `getService returns the same cached instance on a second call without re-binding`() = runTest {
        val client = makeClient()
        val bindCount = installFakeBind(client)

        val first  = client.getService()
        val second = client.getService()

        assertSame(first, second, "Expected same instance on second call")
        assert(bindCount() == 1) { "Expected exactly 1 bind call, got ${bindCount()}" }
    }

    @Test
    fun `release clears the cache so subsequent getService re-binds`() = runTest {
        val client = makeClient()
        val bindCount = installFakeBind(client)

        client.getService()
        client.release()

        // After release, a fresh getService must trigger another bind.
        client.getService()
        assert(bindCount() == 2) { "Expected 2 bind calls after release, got ${bindCount()}" }
    }
}
