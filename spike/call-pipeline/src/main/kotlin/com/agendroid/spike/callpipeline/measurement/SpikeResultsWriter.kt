package com.agendroid.spike.callpipeline.measurement

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpikeResultsWriter(private val context: Context) {

    /** Writes a JSON results file to Downloads/agendroid-spike/ on external storage.
     *  Falls back to internal files dir if external storage is unavailable.
     *  Returns the path written, or null on failure. */
    fun write(phase: String, recorder: LatencyRecorder, extras: Map<String, Any> = emptyMap()): String? {
        return try {
            val dir = resolveOutputDir()
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "spike-$phase-$ts.json")

            val root = JSONObject()
            root.put("phase", phase)
            root.put("timestamp", ts)
            root.put("p95_total_ms", recorder.p95TotalMs())

            val turnsArr = JSONArray()
            recorder.turns().forEach { turn ->
                val t = JSONObject()
                t.put("total_ms", turn.totalMs)
                val stages = JSONObject()
                turn.stages.forEach { (k, v) -> stages.put(k, v) }
                t.put("stages", stages)
                turnsArr.put(t)
            }
            root.put("turns", turnsArr)
            extras.forEach { (k, v) -> root.put(k, v) }

            file.writeText(root.toString(2))
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveOutputDir(): File {
        val external = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(external, "agendroid-spike")
        if (!dir.exists()) dir.mkdirs()
        return if (dir.canWrite()) dir else File(context.filesDir, "spike-results").also { it.mkdirs() }
    }
}
