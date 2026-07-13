# SeminarArc

SeminarArc is a local-first Android app for capturing and revisiting academic seminars. Each seminar is the single ownership container for its abstract PDF, recording, marked audio clips, slide photos, quick notes, questions, and later summary.

## Current Batch

This repository is in the first `0.1.x` implementation batch.

Implemented in this batch:

- Android project baseline
- Material 3 theme and design token mapping
- Room-backed seminar container model
- Seminar list, editor, and detail flows
- Abstract PDF import / replace / remove

Planned for later batches:

- foreground recording service
- active session flow
- CameraX slide capture
- timeline event creation during recording
- clip generation and playback fallback

## Build

Expected local commands once Java, Android SDK, and Gradle wrapper prerequisites are available:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Permissions

- Microphone: required in later batches for seminar recording
- Camera: required in later batches for slide capture
- Notifications: required in later batches for foreground recording service

## Known Limitations

- Batch 01 does not implement real recording, photo capture, or clip generation yet
- Start/Resume seminar is intentionally shown as a future contract rather than a fake completed flow
