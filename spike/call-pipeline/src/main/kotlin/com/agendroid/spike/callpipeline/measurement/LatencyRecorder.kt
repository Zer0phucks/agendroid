package com.agendroid.spike.callpipeline.measurement

class LatencyRecorder {

    private val _turns = mutableListOf<TurnRecord>()

    fun startTurn(): TurnBuilder = TurnBuilder()

    fun turns(): List<TurnRecord> = _turns.toList()

    fun p95TotalMs(): Long {
        if (_turns.isEmpty()) return 0L
        val sorted = _turns.map { it.totalMs }.sorted()
        val idx = ((sorted.size - 1) * 0.95).toInt()
        return sorted[idx]
    }

    inner class TurnBuilder {
        val stages = mutableMapOf<String, Long>()

        fun markStage(name: String, durationMs: Long) {
            stages[name] = durationMs
        }

        fun end() {
            _turns.add(TurnRecord(stages.toMap(), stages.values.sum()))
        }
    }
}

data class TurnRecord(
    val stages: Map<String, Long>,
    val totalMs: Long,
)
