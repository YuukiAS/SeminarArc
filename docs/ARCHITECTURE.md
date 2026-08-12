# SeminarArc Architecture

SeminarArc follows a single-module layered Android architecture:

- `ui`: Compose screens, navigation, ViewModels, UI state
- `domain`: plain Kotlin models and repository contracts
- `data`: Room, storage, and repository implementations
- `recording`, `media`, `worker`: contracts and future implementation surfaces for later batches
- `di`: Hilt modules

## Batch 01 focus

Batch 01 establishes:

- design token driven Material 3 theme
- Room database with seminar, recording, timeline event, and clip schema
- seminar-owned file storage layout
- seminar CRUD and PDF attachment lifecycle

## Recording foundation

`0.1.2` recording foundation adds:

- `SeminarRepository.startSeminarSession(seminarId)` as the single entry for one-active-seminar session semantics.
- `RecordingRepository` as the durable Room boundary for `RecordingEntity` lifecycle.
- `SeminarRecordingService` as the Android foreground service owner for microphone recording.
- `RecorderController` / `AndroidMediaRecorderController` as the hardware-facing recording backend.
- `SeminarRecordingNotificationFactory` for recording notification channel and ongoing foreground notification.
- `StartSeminarRecordingUseCase` as the minimal application entry used by UI before starting the service.

The foreground service owns the live recorder instance. Room owns durable facts. UI and ViewModels must not directly manipulate `MediaRecorder`, Room DAOs, or foreground service internals.

## Planned ownership model

All future assets must remain owned by a seminar:

```text
files/
  seminars/
    <seminar-id>/
      abstract/
      recordings/
      clips/
      photos/
```

Deletion must go through repository orchestration so database rows and owned files are removed together.
