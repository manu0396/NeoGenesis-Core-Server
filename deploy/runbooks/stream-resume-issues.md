# Runbook: Stream Resume Issues

## Symptoms
- Client unable to resume telemetry/stream
- Replayed events from old sequence offsets

## Immediate Actions
1. Check gateway logs for resume tokens / seq usage.
2. Verify server storage of seq ordering.

## Recovery
- Restart gateway to rebuild channel and resume.
- If sequences are corrupted, reset from last known good seq.

## Validation
- Telemetry resumes from expected seq
- No duplicate bursts after resume
