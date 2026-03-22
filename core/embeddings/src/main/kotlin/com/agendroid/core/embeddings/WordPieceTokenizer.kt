package com.agendroid.core.embeddings

import android.content.Context

/**
 * Minimal BERT WordPiece tokenizer for all-MiniLM-L6-v2.
 *
 * Uses the BERT-base-uncased vocabulary (30,522 tokens). Produces a fixed-length
 * [MAX_SEQ_LEN] int array with [CLS] prefix, [SEP] suffix, and zero-padding.
 *
 * Special IDs (BERT-base-uncased):
 *   [PAD]=0, [UNK]=100, [CLS]=101, [SEP]=102
 */
internal class WordPieceTokenizer(private val vocab: Map<String, Int>) {

    companion object {
        const val MAX_SEQ_LEN = 128
        private const val CLS = 101
        private const val SEP = 102
        private const val PAD = 0
        private const val UNK = 100

        fun fromAssets(context: Context, vocabAsset: String = "all_minilm_vocab.txt"): WordPieceTokenizer {
            val vocab = HashMap<String, Int>(32768)
            context.assets.open(vocabAsset).bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line -> vocab[line.trim()] = index }
            }
            return WordPieceTokenizer(vocab)
        }
    }

    /**
     * Tokenizes [text] → int array of length [MAX_SEQ_LEN].
     * Layout: [CLS, ...tokens..., SEP, PAD, PAD, ...]
     */
    fun tokenize(text: String): IntArray {
        val cleaned = text.lowercase().trim()
        val pieceIds = mutableListOf<Int>()

        for (word in cleaned.split(Regex("\\s+"))) {
            if (word.isEmpty()) continue
            pieceIds += wordPiece(word)
            if (pieceIds.size >= MAX_SEQ_LEN - 2) break
        }

        val truncated = pieceIds.take(MAX_SEQ_LEN - 2)
        val ids = IntArray(MAX_SEQ_LEN) { PAD }
        ids[0] = CLS
        truncated.forEachIndexed { i, id -> ids[i + 1] = id }
        ids[truncated.size + 1] = SEP
        return ids
    }

    private fun wordPiece(word: String): List<Int> {
        // Fast path: whole word in vocab
        vocab[word]?.let { return listOf(it) }

        val pieces = mutableListOf<Int>()
        var remaining = word
        var isFirst = true

        while (remaining.isNotEmpty()) {
            var found = false
            for (end in remaining.length downTo 1) {
                val candidate = if (isFirst) remaining.substring(0, end)
                                else "##${remaining.substring(0, end)}"
                vocab[candidate]?.let { id ->
                    pieces += id
                    remaining = remaining.substring(end)
                    isFirst = false
                    found = true
                }
                if (found) break
            }
            if (!found) return listOf(UNK)  // word cannot be segmented
        }
        return pieces
    }
}
