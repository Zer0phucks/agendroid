package com.agendroid.core.embeddings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WordPieceTokenizerTest {

    // Build a minimal vocab for testing (same token IDs as BERT-base-uncased)
    private val vocab = mapOf(
        "[PAD]" to 0, "[UNK]" to 100, "[CLS]" to 101, "[SEP]" to 102,
        "hello" to 7592, "world" to 2088, "##ing" to 2075,
        "run" to 2175, "##ning" to 6605,
    )
    private val tokenizer = WordPieceTokenizer(vocab)

    @Test
    fun `output length is always MAX_SEQ_LEN`() {
        val result = tokenizer.tokenize("hello world")
        assertEquals(WordPieceTokenizer.MAX_SEQ_LEN, result.size)
    }

    @Test
    fun `first token is CLS id 101`() {
        assertEquals(101, tokenizer.tokenize("hello").first())
    }

    @Test
    fun `known words are mapped to correct IDs`() {
        val ids = tokenizer.tokenize("hello world").toList()
        assertTrue(ids.contains(7592), "Expected hello (7592) in ids")
        assertTrue(ids.contains(2088), "Expected world (2088) in ids")
    }

    @Test
    fun `padding fills remaining positions with 0`() {
        val ids = tokenizer.tokenize("hello")
        // [CLS]=101, hello=7592, [SEP]=102, then all zeros
        assertEquals(0, ids[3])
        assertEquals(0, ids[WordPieceTokenizer.MAX_SEQ_LEN - 1])
    }

    @Test
    fun `unknown word maps to UNK id 100`() {
        val ids = tokenizer.tokenize("xyzzy").toList()
        assertTrue(ids.contains(100), "Expected UNK (100) for unknown word")
    }
}
