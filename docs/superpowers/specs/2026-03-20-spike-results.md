# Call Pipeline Spike Results

**Date:** YYYY-MM-DD
**Device:** OnePlus 12 (or actual device used)
**Snapdragon 8 Gen 3 / RAM:** ___GB
**Model tested:** Gemma 3 1B GPU int4

## Acceptance Gates

| Gate | Threshold | Measured | Result |
| --- | --- | --- | --- |
| End-to-end latency p95 | ≤ 2 000ms | ___ms | PASS / FAIL |
| Thermal sustain (10 min) | ≤ 42°C SoC | ___°C | PASS / FAIL |
| Crash-free (20 turns) | 100% | ___% | PASS / FAIL |
| Battery drain (10 min) | ≤ 8% | ___% | PASS / FAIL |
| Take-over handoff | ≤ 200ms | ___ms | PASS / FAIL |

## Stage Breakdown (Phase 3 medians)

| Stage | Median | Budget |
| --- | --- | --- |
| STT (placeholder 400ms) | 400ms | ≤ 500ms |
| LLM first token | ___ms | ≤ 500ms |
| LLM full response | ___ms | ≤ 600ms |
| TTS | ___ms | ≤ 200ms |

## Decision

**[ ] ALL GATES PASS → Proceed with full call-agent implementation (Plans 3–8 as specified)**

**[ ] ONE OR MORE GATES FAIL → Downgrade v1 scope:**
- Full call-agent deferred to v2
- v1 ships: screen-only call screening (AI transcribes, user speaks) + autonomous SMS
- Plans 3–8 proceed with call-agent code removed from :core:telephony and :feature:phone scope

## Notes

(Add any observations about thermal behavior, audio routing quirks, model loading time, etc.)
