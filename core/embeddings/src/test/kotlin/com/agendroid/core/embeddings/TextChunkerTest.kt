package com.agendroid.core.embeddings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TextChunkerTest {

    private val chunker = TextChunker()

    @Test
    fun `empty string returns empty list`() {
        assertTrue(chunker.chunk("").isEmpty())
    }

    @Test
    fun `blank string returns empty list`() {
        assertTrue(chunker.chunk("   \n\t  ").isEmpty())
    }

    @Test
    fun `text shorter than chunkSize returns single chunk`() {
        val text = (1..100).joinToString(" ") { "word$it" }
        val chunks = chunker.chunk(text)
        assertEquals(1, chunks.size)
        assertEquals(100, chunks[0].split(" ").size)
    }

    @Test
    fun `text exactly chunkSize words returns single chunk`() {
        val text = (1..450).joinToString(" ") { "word$it" }
        assertEquals(1, chunker.chunk(text).size)
    }

    @Test
    fun `text larger than chunkSize produces multiple chunks each within limit`() {
        val text = (1..1000).joinToString(" ") { "word$it" }
        val chunks = chunker.chunk(text)
        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            assertTrue(chunk.split(" ").size <= chunker.chunkSize,
                "Chunk has ${chunk.split(" ").size} words, expected ≤ ${chunker.chunkSize}")
        }
    }

    @Test
    fun `consecutive chunks share overlap words`() {
        val text = (1..500).joinToString(" ") { "word$it" }
        val chunks = chunker.chunk(text)
        assertTrue(chunks.size >= 2)
        val endOfFirst    = chunks[0].split(" ").takeLast(chunker.overlap)
        val startOfSecond = chunks[1].split(" ").take(chunker.overlap)
        assertEquals(endOfFirst, startOfSecond,
            "Expected last ${chunker.overlap} words of chunk 0 == first ${chunker.overlap} words of chunk 1")
    }

    @Test
    fun `all chunks together cover every word in the original text`() {
        val words = (1..600).map { "word$it" }
        val text  = words.joinToString(" ")
        val chunks = chunker.chunk(text)
        // The first chunk starts with word1; the last chunk ends with the last word
        assertTrue(chunks.first().startsWith("word1"))
        assertTrue(chunks.last().endsWith("word600"))
    }

    @Test
    fun `chunkSize not greater than overlap throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> { TextChunker(chunkSize = 50, overlap = 50) }
    }

    @Test
    fun `negative overlap throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> { TextChunker(chunkSize = 100, overlap = -1) }
    }
}
