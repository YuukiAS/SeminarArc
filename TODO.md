# SeminarArc MVP Implementation TODO

## 1. Product goal

SeminarArc is a local-first Android app for capturing and revisiting academic seminars. Each seminar is an independent container. Its abstract PDF, full recording, marked audio clips, slide photos, quick notes, questions, and later summary must always remain associated with the same seminar.

The MVP must implement a complete usable path rather than only UI mockups:

1. Create a seminar and optionally attach an abstract PDF.
2. Start an active seminar session.
3. Record audio in the background while the screen is locked.
4. Mark important moments and retain the audio before and after each mark.
5. Capture slide photos and associate them with the current seminar and recording timestamp.
6. Add quick notes or questions during the seminar.
7. End the session and review all events on one chronological timeline.
8. Reopen the seminar later and play the relevant recording or marked clips.

The first version is for one local user. Do not add accounts, cloud synchronization, collaboration, subscriptions, or a backend.

---

## 2. Required technology and project baseline

Build a native Android application using:

- Kotlin
- Jetpack Compose and Material 3
- Navigation Compose
- Room for structured local data
- Hilt for dependency injection
- Coroutines and Flow
- CameraX for slide capture
- A foreground service for seminar audio recording
- Media3 for audio playback
- WorkManager for deferred clip generation and cleanup
- Android Storage Access Framework for importing abstract PDFs
- App-private storage for recordings, clips, photos, and imported PDFs

Recommended baseline:

- `minSdk = 26`
- `targetSdk` and `compileSdk` set to the latest stable SDK available in the environment
- Gradle Kotlin DSL
- Version catalog for dependencies
- Package/namespace: `com.yuukias.seminararc`

Use a clear layered structure, but do not split the MVP into unnecessary Gradle modules. A single `app` module with packages such as `data`, `domain`, `recording`, `media`, `ui`, and `worker` is sufficient.

The repository must build from a clean checkout with documented commands.

---

## 3. Core product rules

These rules are non-negotiable:

1. Every captured object must contain a valid `seminarId`.
2. The app must never create an unassociated photo, recording, clip, note, or question during the normal in-app workflow.
3. At most one seminar may be active at a time.
4. Starting a new seminar while another is active must return the user to the active seminar rather than silently starting a second recording.
5. Every event created during an active seminar must store its offset from the recording start.
6. Data must survive app process death and device restart wherever Android permits.
7. The foreground recording notification must clearly show which seminar is being recorded.
8. The app must not claim that a clip exists until it has either been generated successfully or is explicitly shown as pending/failed.

---

## 4. Data model

Implement Room entities and relationships equivalent to the following model. Exact field names may vary, but no major relationship may be omitted.

### `SeminarEntity`

Required fields:

- `id: Long`
- `title: String`
- `speaker: String?`
- `affiliation: String?`
- `scheduledAt: Instant?`
- `location: String?`
- `abstractText: String?`
- `abstractPdfPath: String?`
- `status: DRAFT | ACTIVE | COMPLETED`
- `rating: Int?` where valid values are 1–5
- `isFavorite: Boolean`
- `createdAt: Instant`
- `updatedAt: Instant`
- `sessionStartedAt: Instant?`
- `sessionEndedAt: Instant?`

### `RecordingEntity`

Required fields:

- `id: Long`
- `seminarId: Long`
- `filePath: String`
- `startedAt: Instant`
- `endedAt: Instant?`
- `durationMs: Long?`
- `state: RECORDING | COMPLETED | FAILED`
- `errorMessage: String?`

The MVP may use one full recording per seminar, but the schema must not make future multiple recordings impossible.

### `TimelineEventEntity`

Required fields:

- `id: Long`
- `seminarId: Long`
- `recordingId: Long?`
- `type: MARK | PHOTO | NOTE | QUESTION`
- `offsetMs: Long`
- `createdAt: Instant`
- `text: String?`
- `photoPath: String?`

### `AudioClipEntity`

Required fields:

- `id: Long`
- `seminarId: Long`
- `recordingId: Long`
- `sourceEventId: Long`
- `startOffsetMs: Long`
- `endOffsetMs: Long`
- `filePath: String?`
- `state: PENDING | PROCESSING | READY | FAILED`
- `errorMessage: String?`

Use foreign keys and useful indices. Deleting a seminar must delete its database children and owned media files through an explicit repository/use-case path. Do not rely only on database cascade because files also need cleanup.

---

## 5. Required screens and navigation

### 5.1 Seminar library

The app launch screen must show seminar cards ordered by date, with at least:

- title
- speaker when available
- date
- status
- number of photos
- number of marked clips

Required actions:

- create seminar
- open seminar
- search by title or speaker
- filter by `All`, `Draft`, `Completed`, and `Favorites`

A floating action button may be used for creation.

### 5.2 Create/edit seminar

Required editable fields:

- title, required
- speaker
- affiliation
- date/time
- location
- abstract text

Required actions:

- import an abstract PDF using `ACTION_OPEN_DOCUMENT`
- copy the selected PDF into app-private storage
- display the attached filename
- replace or remove the PDF
- save the seminar as a draft

Automatic metadata extraction from the PDF is not required in the MVP. Do not fabricate extracted metadata.

### 5.3 Seminar detail

Show:

- metadata and attached abstract PDF
- seminar status
- start/resume/open active session action
- chronological timeline
- recording information
- rating and favorite controls
- edit and delete actions

For a completed seminar, timeline items must be directly usable. A photo opens a viewer. A note or question shows its content. A mark plays its generated clip, or the corresponding range of the full recording if clip generation has not completed.

### 5.4 Active session

This is the most important screen. It must show:

- seminar title
- recording state
- elapsed duration
- large `Mark moment` button
- large `Capture slide` button
- `Add question` button
- `Quick note` button
- pause/resume recording if technically stable; otherwise omit pause rather than implement it unreliably
- explicit `End seminar` action with confirmation

The screen must remain simple enough to operate with minimal attention.

### 5.5 Photo capture and viewer

Use CameraX inside the app. After capture:

- save the image in the current seminar's media directory
- create a `PHOTO` timeline event with the current recording offset
- show a quick preview with `Keep` and `Retake`
- preserve image orientation correctly

Perspective correction, OCR, formula recognition, and image enhancement are deferred. Structure the code so a future image-processing pipeline can be inserted without changing the data model.

### 5.6 Post-seminar review

After ending a seminar, navigate to a review state that shows:

- full recording duration and playback
- all timeline events ordered by `offsetMs`
- photos
- marks with clip generation state
- notes and questions

Allow:

- deleting an individual event
- editing note/question text
- replaying the full recording from the event timestamp
- replaying a ready clip
- retrying failed clip generation

---

## 6. Recording implementation

Implement recording as a foreground service so recording continues when the app is backgrounded or the device is locked.

Required behavior:

1. Starting a seminar creates or updates its `RecordingEntity`, marks the seminar `ACTIVE`, and starts the foreground service.
2. Store the audio in a stable compressed format supported by Android, preferably AAC in an M4A container.
3. The persistent notification must display:
   - SeminarArc
   - the current seminar title
   - elapsed recording time or active state
   - an action to mark the current moment
   - an action to return to the active session
   - an end action only if it can be implemented safely with confirmation or an unambiguous stop flow
4. Recover UI state from Room/service state rather than assuming the Activity remains alive.
5. On normal completion, finalize the file, store duration, set recording state to `COMPLETED`, and set seminar status to `COMPLETED`.
6. On failure, preserve whatever file is usable, record the error, and present a visible failure state.

Handle runtime permissions according to the Android version. Include microphone, camera, and notification permissions where required. The UI must explain why each permission is needed and must remain usable for viewing existing seminars if a permission is denied.

---

## 7. Marked audio clips

The default marked interval is:

- 60 seconds before the mark
- 90 seconds after the mark

Make these constants configurable in one place.

When the user presses `Mark moment`:

1. Create a `MARK` timeline event immediately using the current offset.
2. Compute the requested interval:
   - `startOffsetMs = max(0, markOffsetMs - 60_000)`
   - `endOffsetMs = markOffsetMs + 90_000`
3. Create a pending `AudioClipEntity`.
4. If the seminar is still active, clip generation waits until the requested post-mark interval has elapsed and the source media is safely readable, or until the seminar ends.
5. After the source recording is finalized, WorkManager generates a real standalone clip without transcoding when technically possible.
6. Clamp the end offset to the actual recording duration.
7. If two marked intervals overlap substantially, the MVP may keep them as separate logical clips. Do not silently merge events.
8. Clip export failure must not destroy the mark. Playback must fall back to seeking within the full recording.

Do not fake clip generation by merely copying the full recording under a new filename.

---

## 8. Timeline behavior

All seminar artifacts must appear in one timeline ordered by recording offset. Each item must display:

- event type
- offset formatted as `HH:MM:SS` or `MM:SS`
- thumbnail for photos
- text preview for notes/questions
- clip state for marks

Selecting any event must offer `Play from here`, which seeks the full recording to the event offset. A photo should also expose the associated audio context through this action.

When a seminar has no recording, imported/manual notes may use an offset of zero and still remain associated with the seminar.

---

## 9. Local storage layout

Use app-private storage with a predictable layout similar to:

```text
files/
  seminars/
    <seminar-id>/
      abstract/
      recordings/
      clips/
      photos/
```

Database paths should be relative to an app-owned root where practical, so internal migrations are possible.

Provide a central media storage component. UI code and ViewModels must not construct paths directly.

Add safe cleanup for:

- replacing/removing an abstract PDF
- retaking a photo
- deleting a timeline event
- deleting a seminar
- failed partial clip files

Never delete a full recording merely because one clip generation job fails.

---

## 10. Lifecycle and recovery requirements

Test and support these cases:

- screen rotation during seminar recording
- app sent to background
- screen locked
- Activity/process recreated while the foreground service remains alive
- user reopens the app during an active seminar
- app starts while a seminar is marked `ACTIVE` but the recording service is no longer running
- insufficient storage
- denied or revoked microphone/camera permission
- camera capture cancelled
- PDF import cancelled
- clip worker interrupted and retried

For stale `ACTIVE` state after an abnormal stop, show a recovery screen. Do not silently mark the seminar completed without explaining that recording was interrupted.

---

## 11. Testing requirements

Add automated tests rather than treating testing as optional.

### Unit tests

At minimum cover:

- mark interval calculation near recording start
- mark interval calculation near recording end
- timeline ordering
- one-active-seminar invariant
- seminar deletion cleanup orchestration
- stale active-session recovery decision

### Room tests

At minimum cover:

- seminar with recording/events/clips relationships
- foreign keys and deletion behavior
- timeline query ordering

### UI tests

At minimum cover:

- create seminar and see it in the library
- start a seminar using a fake recording controller
- add a mark, note, and question
- end seminar and reach the review screen

Abstract the recorder, camera launcher, clock, and clip generator behind interfaces so tests do not require real hardware.

---

## 12. Documentation and repository deliverables

The implementation is not complete unless the repository includes:

- `README.md` with product purpose, current capabilities, screenshots or clearly marked screenshot placeholders, build instructions, permission explanation, and known limitations
- `docs/ARCHITECTURE.md` describing data flow, storage ownership, recording service, clip generation, and recovery behavior
- `docs/PRIVACY.md` explaining that recordings are local by default and that users must obtain permission before recording a seminar
- `.gitignore`
- Gradle wrapper
- a compilable Android Studio project
- tests and commands to run them

Add a basic GitHub Actions workflow that builds the debug APK and runs unit tests on pushes and pull requests.

---

## 13. Explicit non-goals for the MVP

Do not spend MVP time on:

- Notion synchronization
- cloud backup or login
- speech-to-text transcription
- AI summaries
- PDF metadata extraction
- OCR or LaTeX recognition
- slide perspective correction
- knowledge graphs
- speaker diarization
- collaboration
- iOS or web versions

These may be documented as future work, but no placeholder screen should pretend that they already function.

---

## 14. Suggested implementation order

### Phase 1: Foundation

- initialize Android project and CI
- implement Room schema, DAOs, repositories, storage manager, and tests
- implement seminar library, create/edit, detail, and delete flows
- implement abstract PDF attachment

### Phase 2: Recording session

- implement foreground recording service and notification
- enforce one active seminar
- implement active-session screen and lifecycle recovery
- implement full-recording playback

### Phase 3: Captures and timeline

- implement mark, note, and question events
- implement CameraX slide capture
- implement chronological timeline and `Play from here`

### Phase 4: Clip generation

- implement pending clip model and WorkManager jobs
- generate actual standalone clips
- implement retry, failure display, and full-recording fallback

### Phase 5: Hardening

- add remaining automated tests
- test backgrounding, locking, process recreation, permission denial, and storage errors
- complete README, architecture, privacy documentation, and known limitations
- remove dead code, demo-only shortcuts, and unimplemented controls

Codex should complete phases sequentially, keeping the repository buildable after each phase. Do not claim the MVP is complete while any required phase or acceptance criterion remains unmet.

---

## 15. Definition of done

The MVP is complete only when a clean installation can demonstrate this end-to-end scenario on an Android emulator or physical device:

1. Create a seminar titled `Domain Adaptation Seminar`.
2. Attach an abstract PDF.
3. Start the seminar and grant required permissions.
4. Lock or background the app while recording continues through the foreground service.
5. Return to the app and create at least two marks.
6. Capture at least one slide photo.
7. Add one question and one quick note.
8. End the seminar.
9. See every item under the same seminar in chronological order.
10. Open the photo and play the recording from its timestamp.
11. Play each generated marked clip; if generation is still pending or failed, visibly fall back to the correct range in the full recording.
12. Close and reopen the app and confirm that all seminar data and media remain accessible.
13. Delete the seminar and confirm that its database records and owned media files are removed.
14. Run the documented build and test commands successfully from a clean checkout.

A collection of static Compose screens, mocked data without persistence, a recorder that stops when the Activity closes, or marker buttons that do not produce playable audio context does not satisfy this TODO.
