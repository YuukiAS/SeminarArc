# SeminarArc Architecture

SeminarArc follows a single-module layered Android architecture:

- `ui`: Compose screens, navigation, ViewModels, UI state
- `domain`: plain Kotlin models and repository contracts
- `data`: Room, storage, and repository implementations
- `recording`, `media`, `worker`: microphone recording, clip generation, playback, CameraX capture, and background work boundaries
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
- `RecordingPlaybackController` / `Media3RecordingPlaybackController` as the page-scoped Media3 playback boundary for completed full recordings.

The foreground service owns the live recorder instance. Room owns durable facts. UI and ViewModels must not directly manipulate `MediaRecorder`, Room DAOs, or foreground service internals.

`RecordingRuntimeStateProvider` exposes current-process recorder state to UI without making ViewModels depend on the concrete Android `Service`. A Room `RecordingEntity(state = RECORDING)` is not sufficient proof that a live recorder exists; if the current process has no runtime recorder, Active Session enters recovery.

Process-start recovery is gated through `RecordingRecoveryInitializer`: startup captures the stale `RECORDING` IDs first, then only fails those IDs. `StartSeminarRecordingUseCase` awaits this gate before starting a new foreground recording, so a recording created in the new process is not accidentally failed by late startup recovery.

`EndSeminarUseCase` sequencing is:

```text
live recorder stop/finalize -> RecordingEntity COMPLETED -> Seminar ACTIVE -> COMPLETED
```

If recorder stop/finalize fails, the recording is marked `FAILED` by the coordinator and the seminar is not marked completed. If no live recorder exists while ending a stale active seminar, only that seminar's open recording rows are marked failed before the seminar completion update is attempted.

## Full recording playback

`0.1.2` full recording playback adds page-local Media3 playback for completed seminar recordings:

- `RecordingRepository.observeRecordingsForSeminar(seminarId)` exposes all recording rows for one seminar so playback selection is not based on a single latest row.
- `MediaStorageManager.resolveReadableRelativeFile(relativePath)` is the storage boundary for converting Room's app-private relative path into a readable `File`; UI never concatenates `context.filesDir` with database strings.
- `RecordingPlaybackController` exposes a sealed controller state and idempotent `prepare`, `play`, `pause`, `seekTo`, and `release` operations.
- `Media3RecordingPlaybackController` owns ExoPlayer and its callbacks. Compose does not manage player listeners, and ViewModels do not hold Android Views.
- `SeminarDetailViewModel` chooses the newest readable `COMPLETED` recording file. A latest `FAILED` row does not hide an older legal completed recording. If completed rows exist but no readable file can be resolved, Detail shows `MissingFile`.
- `FAILED` recordings are not treated as reliable full recordings. `RECORDING` rows are left to Active Session and are not exposed as completed playback.
- Playback errors are scoped to `RecordingPlaybackUiState.PlaybackError`; the rest of Seminar Detail remains browsable.

Playback is intentionally page-scoped in this phase. There is no `MediaSessionService`, lock-screen control, notification media control, background playback service, waveform, stitching, clip generation, OCR, transcription, or AI path in `0.1.2`.

## Capture, timeline, and clips

`0.1.3` and `0.1.4` add the local capture loop:

- `ActiveSessionViewModel` is the capture coordinator for marks, questions, notes, slide photos, and last-photo undo/retake.
- CameraX stays behind the UI/session layer; domain and repository layers only persist app-private relative photo paths.
- Timeline events are durable Room rows keyed by `seminarId`, ordered by recoverable `offsetMs`, and may reference a photo path or a generated clip.
- MARK events create `PENDING` clips through `ClipRepository`; WorkManager re-reads Room state and calls the Android `.m4a` clip generator instead of trusting Activity memory.
- A clip is playable only when its Room state is `READY` and its app-private file resolves through `MediaStorageManager`; otherwise UI keeps a full-recording fallback from the event offset.

## Local export

`0.1.5` adds local-only export without introducing cloud/provider dependencies:

- `SeminarExportAssembler` maps `SeminarDetail`, timeline events, recordings, and clips into a UI-independent `SeminarExportDocument`.
- `SeminarMarkdownRenderer` renders deterministic Markdown with relative media links and visible fallback text for non-ready clips.
- `SeminarZipWriter` writes `<seminar-slug>/seminar.md` plus readable `media/abstract`, `media/photos`, and `media/clips` assets; missing media is skipped and recorded instead of failing the full export.
- `SeminarExportRepositoryImpl` owns Android I/O: Room flow reads, `MediaStorageManager` file resolution, `ACTION_CREATE_DOCUMENT` writes, and cache-backed FileProvider share URIs.
- `SeminarDetailViewModel` exposes progress/success/failure state and one-shot share events; Compose screens do not read Room or app-private files directly.

## Local visual reconstruction readiness

`0.2.x` advances from local capture into local visual reconstruction. The first data foundation implementation has landed these architecture decisions:

- Room migrates explicitly from version `2` to version `3`; schema `3.json` is checked in.
- Existing `seminars`, `recordings`, `timeline_events`, and `audio_clips` remain intact for `0.1.x` compatibility.
- `seminar_assets` is the app-owned index for original and derived assets: abstract PDFs, recordings, original photos, enhanced photos, clips, and exports.
- `processing_jobs` records durable local processing state for image enhancement and OCR. Job state is persisted as `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, or `CANCELLED`.
- `ocr_results` stores app-owned recognized text, optional lightweight block JSON, provider metadata, and human-edited state.
- `tags` and `asset_tags` support key slides and seminar-scoped photo organization.
- `MIGRATION_2_3` backfills assets from existing `abstractPdfPath`, `recordings.filePath`, `timeline_events.photoPath`, and `audio_clips.filePath` without moving or deleting files.
- Local image enhancement now uses `AndroidBitmapImageEnhancementProvider` behind `ImageEnhancementProvider`. `EnhancePhotoAssetUseCase` resolves the original app-owned file, writes a deterministic `enhanced/` JPEG, creates a `PHOTO_ENHANCED` derived `SeminarAsset`, and marks the durable `IMAGE_ENHANCEMENT` job succeeded or retryable failed.

Provider boundaries:

- `TextOcrProvider` owns local OCR calls and returns app-owned domain models.
- `ImageEnhancementProvider` owns rotate/crop/perspective/readability outputs and always writes derived assets instead of replacing original photos.
- `CloudUploadPolicy` defaults to no upload in `0.2.x`.
- `FormulaOcrProvider`, `TranscriptionProvider`, `SummaryProvider`, and `ReferenceLookupProvider` remain deferred boundaries; no real `0.3.x+` provider behavior ships in `0.2.x`.

Local OCR now uses `MlKitTextOcrProvider` behind `TextOcrProvider`. The first implementation uses bundled Latin and Chinese ML Kit recognizers, stores recognized text and lightweight block JSON in `ocr_results`, and writes durable `TEXT_OCR` processing job state through `RunTextOcrForAssetUseCase`. OCR operates on app-owned photo assets only and does not upload seminar media.

`ReconstructionWorkspaceViewModel` is the first UI-facing reconstruction boundary. It combines seminar detail, photo assets, OCR results, processing jobs, key-slide tag membership, search text, and OCR status filters into immutable UI state. It also exposes actions for key-slide toggles, OCR editing, image enhancement, and local OCR through repository/use-case APIs.

Compose screens continue to call ViewModels only. ViewModels call repository/use-case boundaries. Workers/use cases call providers and persist results. No composable reads Room, app-private files, ML Kit, or bitmap processing APIs directly.

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

External Markdown/ZIP copies created through document export or share sheets are outside app-owned storage. Seminar deletion must not imply those external copies are removed.
