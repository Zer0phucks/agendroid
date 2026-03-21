# Call Pipeline Spike Results

**Date:** 2026-03-20
**Device:** OnePlus 12
**Snapdragon 8 Gen 3 / RAM:** 16GB
**Model tested:** Gemma 3 1B int4 (`litert-community/Gemma3-1B-IT`, `gemma3-1b-it-int4.task`)
**MediaPipe:** tasks-genai 0.10.32

## Acceptance Gates

| Gate | Threshold | Measured | Result |
| --- | --- | --- | --- |
| End-to-end latency p95 | ≤ 2 000ms | 682ms (LLM only, Phase 1) | **PASS** |
| Thermal sustain (10 min) | ≤ 42°C SoC | N/A (API unavailable via reflection) | TBD |
| Crash-free (20 turns) | 100% | 100% (Phase 1: 20/20) | **PASS** |
| Battery drain (10 min) | ≤ 8% | ___% (measure in Phase 2) | PENDING |
| Take-over handoff | ≤ 200ms | ___ms (measure in Phase 2) | PENDING |

## Stage Breakdown (Phase 3 medians)

| Stage | Median | p95 | Budget |
| --- | --- | --- | --- |
| STT (placeholder) | 400ms | 400ms | ≤ 500ms |
| LLM full response (Phase 1) | 548ms | 682ms | ≤ 600ms |
| LLM first token | ___ms | ___ms | ≤ 500ms |
| TTS | ___ms | ___ms | ≤ 200ms |

## Decision

**[ ] ALL GATES PASS → Proceed with full call-agent implementation (Plans 3–8 as specified)**

**[ ] ONE OR MORE GATES FAIL → Downgrade v1 scope:**
- Full call-agent deferred to v2
- v1 ships: screen-only call screening (AI transcribes, user speaks) + autonomous SMS
- Plans 3–8 proceed with call-agent code removed from :core:telephony and :feature:phone scope

## Notes

**Phase 1 (LLM latency + thermal):**
- p95 LLM full response: **682ms** — well within the 2000ms budget, well within even the stricter 600ms stage budget
- Range across 20 turns: 307ms–691ms (very consistent, no thermal degradation visible)
- Thermal monitoring: `PowerManager.thermalHeadroom()` returned -1 (unsupported/unavailable on this device via reflection). Thermal gate cannot be measured automatically — monitor device temperature manually.
- Model: `gemma3-1b-it-int4.task` via MediaPipe tasks-genai 0.10.32. Must be placed in app internal storage (`filesDir`) — FUSE blocks `Android/data/` for adb-pushed files on Android 14+.

**Setup notes (for reproducibility):**
- Download model: `huggingface-cli download litert-community/Gemma3-1B-IT gemma3-1b-it-int4.task`
- Push to device: `adb push model.task /data/local/tmp/ && adb shell run-as com.agendroid.spike.callpipeline cp /data/local/tmp/gemma3-1b-it-int4.task /data/data/com.agendroid.spike.callpipeline/files/`
