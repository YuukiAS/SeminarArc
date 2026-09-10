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
- `SeminarExportRepositoryImpl` owns Android I/O: Room flow reads, `MediaStorageManager` file resolution, `ACTION_CREATE_DOCUMENT` writes, text share payloads, and cache-backed FileProvider share URIs.
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
- `FormulaOcrProvider`, `TranscriptionProvider`, and `SummaryProvider` remain deferred boundaries; no formula OCR, transcription, AI summary, Notion, or cloud upload provider behavior ships in `0.3.x`.

Local OCR now uses `MlKitTextOcrProvider` behind `TextOcrProvider`. The first implementation uses bundled Latin and Chinese ML Kit recognizers, stores recognized text and lightweight block JSON in `ocr_results`, and writes durable `TEXT_OCR` processing job state through `RunTextOcrForAssetUseCase`. OCR operates on app-owned photo assets only and does not upload seminar media.

`ProcessingWorkScheduler` and `ProcessingWorker` now provide the durable WorkManager queue for `0.2.x` OCR and image enhancement. UI actions enqueue app-owned `ProcessingJob` rows first, then WorkManager receives only job IDs plus bounded operation options. Workers re-read Room and app-private storage before running providers, write `RUNNING/SUCCEEDED/FAILED/CANCELLED`, and clean partial enhancement output on failure. Duplicate active requests reuse the existing queued/running job, retry requeues retryable failed/cancelled jobs, cancel cancels the unique WorkManager work and records `CANCELLED`, and process-start recovery moves orphan `RUNNING` rows back to `QUEUED` before rescheduling.

`ReconstructionWorkspaceViewModel` is the first UI-facing reconstruction boundary. It combines seminar detail, photo assets, OCR results, processing jobs, key-slide tag membership, search text, and OCR status filters into immutable UI state. It exposes actions for key-slide toggles, OCR editing, image enhancement, local OCR, retry, and cancel through repository/use-case/queue APIs.

`ReconstructionWorkspaceScreen` is now reachable from Seminar Detail for completed-seminar cleanup work. It renders searchable/filterable photo assets, ViewModel-resolved local image previews, key-slide toggles, local enhance/OCR commands, queued/running/failed/cancelled processing controls, retry/cancel, and editable OCR text. The screen is intentionally a thin Compose layer over the ViewModel; it does not call Room, WorkManager, ML Kit, CameraX, or storage repositories directly.

Compose screens continue to call ViewModels for app actions. ViewModels call repository/use-case boundaries. Workers/use cases call providers and persist results. No composable reads Room, ML Kit, or bitmap processing providers directly.

## Transcription and Summary Foundation

`0.4.x` starts with provider-independent local persistence and contracts:

- Room version `5` adds `transcripts`, `transcript_segments`, and `summary_drafts`.
- `TranscriptDao` and `TranscriptRepositoryImpl` own transcript rows, timestamped segments, and summary draft persistence.
- `TranscriptionProvider` and `SummaryProvider` are domain contracts; first tests use fake providers only.
- `RunTranscriptionForRecordingUseCase` reads completed recordings through `RecordingRepository`, resolves app-private recording files through `MediaStorageManager`, finds the recording asset through `ReconstructionRepository`, and writes transcript lifecycle/segments through `TranscriptRepository`.
- `DraftSummaryForSeminarUseCase` builds provider-independent summary requests from seminar metadata, user-selected transcript windows, confirmed references, key-slide captions, and notes; provider success/failure is persisted to `summary_drafts` without overwriting user-authored `SeminarBrief` rows.
- `BuildTranscriptTimelineWindowsUseCase` derives local transcript windows around timeline/photo offsets, matches segments by interval overlap, links photo assets, and returns bounded previews for future Reconstruction UI, summary selection, Markdown-friendly export, and Notion-prep boundaries.
- `TranscriptReviewViewModel` and `TranscriptReviewScreen` expose the first local transcript review surface from Reconstruction workspace: transcript list, selected metadata, timestamped segments, timeline/photo windows, local segment editing, manual transcript import, summary draft status, and local summary draft field editing. Live transcription and live summary generation are still explicit provider-selection follow-ups.
- Local Markdown/ZIP export includes transcript review metadata, timestamped segments, ready-transcript timeline windows, and generated summary drafts labeled as editable drafts. ZIP packaging remains compatible by writing the enriched Markdown to `<seminar-slug>/seminar.md` plus readable media assets.
- `ProcessingWorkScheduler` can enqueue completed recordings as durable `TRANSCRIPTION` WorkManager jobs. `ProcessingWorker` delegates those jobs to `RunTranscriptionForRecordingUseCase`; retry/recovery rebuilds work from the input recording asset. The default `UnavailableTranscriptionProvider` is a safe boundary that records provider-unavailable failure without uploading audio or faking success.
- The Transcript Review action now creates local durable transcription work for the latest completed recording and reports the queued/unavailable-provider state through snackbar feedback.
- Room version `6` adds `processing_jobs.inputPayloadJson` so durable work can persist bounded provider-independent inputs beyond a single `inputAssetId`.
- `ProcessingWorkScheduler` can enqueue the selected transcript segments as durable `SUMMARY_DRAFT` WorkManager jobs. `ProcessingWorker` decodes the persisted payload and delegates to `DraftSummaryForSeminarUseCase`; retry/recovery rebuilds work from the payload stored in Room. The default `UnavailableSummaryProvider` is a safe boundary that records provider-unavailable failure without uploading transcript text, references, notes, or faking success.
- `SeminarNotionReadyRenderer` maps the local `SeminarExportDocument` into a provider-neutral, block-like `NotionReadyExportDocument` plus Markdown preview. `SeminarExportRepository.writeNotionReadyMarkdown` saves that preview through Android document picker, and `prepareNotionReadyMarkdownShare` exposes it through the Android share sheet as local `text/markdown`. It carries text and local relative media paths only; it has no Notion OAuth, API client, token storage, upload, or retry behavior.
- Transcript Review observes local processing jobs for `TRANSCRIPTION` and `SUMMARY_DRAFT`, renders their persisted queue state, and routes retry/cancel actions through `ProcessingWorkScheduler`.
- Transcript segment editing stays local: `TranscriptReviewViewModel` keeps unsaved segment drafts in UI state, then calls `TranscriptRepository.editSegmentText`. The repository trims nonblank text, marks the segment edited, refreshes `updatedAt`, and updates the parent transcript activity timestamp.
- Manual transcript import stays local: `TranscriptReviewViewModel` creates `TranscriptSourceType.MANUAL` transcripts with provider id `manual-transcript`, converts each nonblank pasted line into a coarse editable segment, saves the transcript as ready, and reuses the same segment edit/export/summary boundaries as provider-generated transcripts.
- Summary draft editing stays local: `TranscriptReviewViewModel` keeps per-field draft edits in UI state, then calls `TranscriptRepository.editSummaryDraft`. The repository trims field text, clears provider error text, marks the row as `DRAFT`, refreshes `updatedAt`, and preserves provider id, input fingerprint, provenance JSON, and created time.
- Summary draft application is explicit and local: Transcript Review can call `ReferenceRepository.saveBrief` only after the user taps `Apply to Seminar Brief`, copying the selected draft fields into the app-owned editable `SeminarBrief` without live provider calls or background overwrite.
- `NOTION_EXPORT_PREP` remains a durable processing job type, but the existing generic WorkManager worker does not execute that future job type yet.

This foundation does not ship a real ASR engine, cloud transcription, live Notion OAuth/upload, or AI summary runtime yet.

## Formula and research export readiness

`0.5.x` starts with local formula and research-export contracts:

- Formula OCR must be region-scoped. Users select a seminar-owned photo region before any provider runs; the app must not process every photo automatically.
- Formula results are editable local records. Provider output, user-corrected LaTeX, confidence, provenance, and retryable error state remain separate.
- `FormulaOcrProvider` is the future boundary for Mathpix, PaddleOCR, pix2tex/self-hosted, fake, and manual providers. Live Mathpix or other cloud providers require later credential/backend/user-key approval.
- Research export should be deterministic and based only on confirmed references. BibTeX/RIS renderers must exclude pending/rejected candidates and avoid fabricating missing metadata.

Room version `7` adds `formula_regions` and `formula_results`. `FormulaDao` exposes local region/result persistence for UI/provider tasks. `ProcessingJobType.FORMULA_OCR` is wired for the local manual LaTeX provider path; live cloud/model providers remain separate future implementations.

`FormulaOcrProvider` defines the provider boundary for normalized crop input, request fingerprinting, LaTeX output, confidence, provenance, failure, and retryability. The production default is `UnavailableFormulaOcrProvider`; `ManualFormulaProvider` supports explicit user-supplied LaTeX without network or credential access.

`FormulaOcrProviderStatus` and `FormulaOcrProviderCapabilities` expose UI-facing provider readiness without enabling provider execution. The default live OCR provider reports unavailable/credential-required/no-network-runtime, while `ManualFormulaProvider` reports available/manual/no-credential/no-network. Reconstruction workspace renders this status summary so the user can distinguish local manual correction from future Mathpix/PaddleOCR/pix2tex provider paths.

`FormulaRepository` validates source photo ownership before persisting regions. Reconstruction workspace observes formula regions through its ViewModel and exposes local create/delete controls; no composable accesses `FormulaDao` directly.

`FORMULA_OCR` jobs now support the local manual LaTeX path through WorkManager. The scheduler persists `FormulaOcrWorkPayload` in `processing_jobs.inputPayloadJson`; Worker re-reads the formula region and source photo from Room/storage before invoking `ManualFormulaProvider`, then writes READY/FAILED formula results through `FormulaRepository`. Live cloud/model providers remain deferred.

`SeminarBibliographyRenderer` generates deterministic BibTeX and RIS text from `SeminarExportDocument.brief.references`. The export assembler only populates those references from the confirmed Seminar Brief bundle, and ZIP export writes `references.bib` / `references.ris` only when at least one confirmed reference exists. Pending or rejected candidates are never promoted into bibliography artifacts.

Seminar Detail exposes local save/share entry points for BibTeX and RIS through `SeminarExportRepository.writeBibTeX`, `writeRis`, `prepareBibTeXShare`, and `prepareRisShare`. These paths write or share text only when confirmed references exist; otherwise the repository returns a local failure message instead of creating an empty artifact.

Export assembly also loads formula regions and formula results through `FormulaRepository`. Only READY formula results with non-empty LaTeX enter `SeminarExportDocument.formulas`; Markdown and Notion-ready renderers include the LaTeX, normalized crop, source photo, provider/version, confidence, edited flag and provenance. Failed, queued, cancelled or blank formula results remain local state and are not exported as ready formula knowledge.

Reconstruction photo preview draws saved formula regions as token-colored overlays using normalized crop coordinates. The overlay also supports drag-to-draft selection: pointer input updates local `X/Y/Width/Height` draft fields and draws the draft rectangle, but persistence still requires the explicit `Save formula region` action. The drag path does not call a provider, enqueue OCR, upload media, or write Room until the user saves.

Saved formula regions can be loaded back into the same draft controls with `Edit crop` and persisted through `FormulaRepository.updateRegion`. Updates change label/crop/rotation timestamps only; they keep the existing region id, seminar ownership, source asset id and source photo path. Existing formula results remain attached to the region so a corrected crop does not silently erase manual LaTeX history.

The detailed readiness plan is `docs/plans/0.5.x-formula-research-export-plan.md`.

Current `0.5.x` closeout state is COMPLETE for the approved local-safe alpha scope. Schema/domain/provider contracts, manual formula queue, region selection/edit UI, provider status display, formula export, and BibTeX/RIS export passed local JVM/build/lint gates. On 2026-09-10, Windows Emulator closeout passed under the mixed-inventory explicit-emulator lane: Windows ADB still showed protected physical serial `8cc54656 unauthorized`, so unscoped connected Gradle tasks stayed forbidden; app/test APK install and `am instrument` were run only with `adb -s emulator-5554`, and the suite passed `OK (24 tests)`.

## Reference Candidate and Seminar Brief

`0.3.x` adds the opt-in research metadata layer on top of local reconstruction while keeping user media local by default:

- Room migrates explicitly from version `3` to version `4`; schema `4.json` is checked in.
- `reference_evidence`, `reference_lookup_attempts`, `reference_candidates`, `reference_candidate_sources`, `seminar_briefs`, `brief_references`, and `brief_key_slides` are seminar-owned rows. `MIGRATION_3_4` only creates new tables/indexes and preserves all v3 assets, OCR results, processing jobs, tags, recordings, events, and clips.
- `ReferenceEvidenceExtractor` derives safe DOI/title/author/year/venue clues from selected OCR, tags, seminar metadata, and manual user input. Speculative DOI repair is shown as a clue, but exact DOI lookup only uses a safely normalized DOI.
- `ReferenceLookupProvider` implementations for Crossref, OpenAlex, and DataCite use public keyless metadata endpoints through `HttpReferenceClient`. Requests are user-triggered from the review flow and receive only the selected minimum query text/metadata.
- `ReferenceMatcher` writes deterministic `reference-match-v1` scores, confidence bands, and match reasons. Candidates are app-owned canonical records; provider observations remain separate rows for provenance and dedup.
- `ReferenceReviewViewModel` and `ReferenceReviewScreen` expose evidence selection, query preview, provider plan, lookup status, confirm/reject/reopen, editable brief fields, and selected key-slide linking.
- Export assembly loads the `SeminarBriefBundle` and includes only confirmed references plus linked key slides in Markdown/ZIP output. Provider candidates that remain pending or rejected are not exported as confirmed knowledge.

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
