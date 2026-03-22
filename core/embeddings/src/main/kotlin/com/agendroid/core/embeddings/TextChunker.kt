package com.agendroid.core.embeddings

/**
 * Splits text into overlapping word-based chunks for RAG ingestion.
 *
 * Word-based (not token-based) because we have no tokenizer in the ingestion path.
 * At ~1.3 subword tokens/word, [chunkSize]=450 words ≈ 585 subword tokens —
 * safely within all-MiniLM-L6-v2's 512-token training window after [CLS]/[SEP].
 *
 * Thread-safe: stateless.
 */
class TextChunker(
    val chunkSize: Int = 450,
    val overlap: Int   = 50,
) {
    init {
        require(chunkSize > overlap) { "chunkSize ($chunkSize) must be > overlap ($overlap)" }
        require(overlap >= 0) { "overlap must be non-negative" }
    }

    /**
     * Splits [text] into chunks of at most [chunkSize] words with [overlap] words
     * of overlap between consecutive chunks.
     *
     * Returns an empty list for blank input.
     */
    fun chunk(text: String): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        if (words.size <= chunkSize) return listOf(words.joinToString(" "))

        val chunks  = mutableListOf<String>()
        val stride  = chunkSize - overlap
        var start   = 0

        while (start < words.size) {
            val end = minOf(start + chunkSize, words.size)
            chunks.add(words.subList(start, end).joinToString(" "))
            if (end == words.size) break
            start += stride
        }
        return chunks
    }
}
