package com.agendroid.core.embeddings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class EmbeddingModelTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun makeModel() = EmbeddingModel(context, Dispatchers.Default)

    @Test
    fun embed_returnsDimension384() = runTest {
        val model = makeModel()
        val result = model.embed("hello world")
        assertEquals(384, result.size)
        model.close()
    }

    @Test
    fun embed_returnsNonZeroVector() = runTest {
        val model = makeModel()
        val result = model.embed("the quick brown fox jumps over the lazy dog")
        assertTrue("Expected non-zero embedding", result.any { it != 0f })
        model.close()
    }

    @Test
    fun embed_isDeterministic() = runTest {
        val model = makeModel()
        val text   = "determinism check for sentence embeddings"
        val first  = model.embed(text)
        val second = model.embed(text)
        assertArrayEquals(first, second, 1e-6f)
        model.close()
    }

    @Test
    fun embed_outputIsApproximatelyL2Normalized() = runTest {
        val model  = makeModel()
        val result = model.embed("norm check")
        val norm   = sqrt(result.map { it * it }.sum())
        assertEquals(1.0f, norm, 0.01f)   // L2 norm should be ≈ 1.0
        model.close()
    }
}
