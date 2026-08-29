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
- `ActiveSessionRoute` / `ActiveSessionViewModel` as the formal live capture route for current seminar identity, recording state, recovery, permission-denied state, elapsed timer, and End Seminar.
- `EndSeminarUseCase` as the business boundary for stopping/finalizing recording before completing an active seminar.

The foreground service owns the live recorder instance. Room owns durable facts. UI and ViewModels must not directly manipulate `MediaRecorder`, Room DAOs, or foreground service internals.

`RecordingRuntimeStateProvider` exposes current-process recorder state to UI without making ViewModels depend on the concrete Android `Service`. A Room `RecordingEntity(state = RECORDING)` is not sufficient proof that a live recorder exists; if the current process has no runtime recorder, Active Session enters recovery.

Process-start recovery is gated through `RecordingRecoveryInitializer`: startup captures the stale `RECORDING` IDs first, then only fails those IDs. `StartSeminarRecordingUseCase` awaits this gate before starting a new foreground recording, so a recording created in the new process is not accidentally failed by late startup recovery.

`EndSeminarUseCase` sequencing is:

```text
live recorder stop/finalize -> RecordingEntity COMPLETED -> Seminar ACTIVE -> COMPLETED
```

If recorder stop/finalize fails, the recording is marked `FAILED` by the coordinator and the seminar is not marked completed. If no live recorder exists while ending a stale active seminar, only that seminar's open recording rows are marked failed before the seminar completion update is attempted.

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
