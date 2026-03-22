package com.agendroid.core.ai

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PromptBuilderTest {

    private val builder = PromptBuilder()

    @Test
    fun `build includes system prompt`() {
        val prompt = builder.build(
            ragChunks      = emptyList(),
            conversationHistory = emptyList(),
            userQuery      = "Hello",
        )
        assertTrue(prompt.contains(PromptBuilder.SYSTEM_PROMPT), "Expected system prompt in output")
    }

    @Test
    fun `build wraps RAG chunks in delimiters`() {
        val prompt = builder.build(
            ragChunks      = listOf("chunk one", "chunk two"),
            conversationHistory = emptyList(),
            userQuery      = "test",
        )
        assertTrue(prompt.contains("[USER DATA START]"))
        assertTrue(prompt.contains("[USER DATA END]"))
        assertTrue(prompt.contains("chunk one"))
        assertTrue(prompt.contains("chunk two"))
    }

    @Test
    fun `build includes user query`() {
        val prompt = builder.build(
            ragChunks      = emptyList(),
            conversationHistory = emptyList(),
            userQuery      = "What is the weather?",
        )
        assertTrue(prompt.contains("What is the weather?"))
    }

    @Test
    fun `build includes conversation history in order`() {
        val history = listOf("User: hi", "Assistant: hello")
        val prompt  = builder.build(
            ragChunks      = emptyList(),
            conversationHistory = history,
            userQuery      = "continue",
        )
        val userIdx = prompt.indexOf("User: hi")
        val aiIdx   = prompt.indexOf("Assistant: hello")
        assertTrue(userIdx < aiIdx, "History must appear in order")
    }

    @Test
    fun `build truncates RAG chunks when they would exceed context limit`() {
        // Each chunk is ~1000 chars; many chunks should be truncated to stay within limit
        val largeChunk = "x".repeat(1000)
        val manyChunks = List(200) { largeChunk }
        val prompt = builder.build(
            ragChunks      = manyChunks,
            conversationHistory = emptyList(),
            userQuery      = "test",
        )
        // Approximate: 4 chars ≈ 1 token; Gemma 3 1B context = 8192 tokens ≈ 32768 chars
        assertTrue(prompt.length <= 35_000,
            "Prompt length ${prompt.length} exceeds reasonable limit for 8192-token context")
    }

    @Test
    fun `build omits RAG section when no chunks provided`() {
        val prompt = builder.build(
            ragChunks      = emptyList(),
            conversationHistory = emptyList(),
            userQuery      = "hi",
        )
        assertFalse(prompt.contains("[USER DATA START]"),
            "Expected no RAG delimiters when chunks list is empty")
    }

    @Test
    fun `built prompt ends with model turn opening for streaming`() {
        val prompt = builder.build(
            ragChunks      = emptyList(),
            conversationHistory = emptyList(),
            userQuery      = "hello",
        )
        assertTrue(prompt.trimEnd().endsWith("<start_of_turn>model"),
            "Prompt should end with model turn so LLM completes it")
    }
}
