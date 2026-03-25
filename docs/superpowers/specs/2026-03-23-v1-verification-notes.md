# V1 Verification Notes

**Date:** 2026-03-23
**Branch:** main

## Manual Checklist

- [ ] Onboarding roles complete — default SMS + dialer roles granted
- [ ] Incoming SMS auto mode sends after delay; cancel works within 10 s
- [ ] SMS semi mode approval notification works
- [ ] Wake word opens assistant surface (foreground only, v1)
- [ ] Incoming call shows live transcript and take-over button
- [ ] Emergency calls pass through untouched (SCREEN_ONLY mode)

## Automated Test Results

- Unit tests: TBD (run `./gradlew testDebugUnitTest`)
- Instrumented: TBD (run `./gradlew connectedDebugAndroidTest`)

## Known Gaps

- Wake word: requires physical model assets in `assets/models/kws/`
- V1 ships without CalDAV, IMAP, and custom wake-word training
