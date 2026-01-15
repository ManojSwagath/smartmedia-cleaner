# SmartMedia Cleaner — Dev Log (What Changed and Why)

Audience: your developer friends.
Goal: make progress auditable and reduce “hallucination risk”.

Last updated: 2026-01-14

---

## How to verify changes

- Build: `./gradlew :app:assembleDebug`
- Main entry: `MainActivity`

---

## Key design constraints

- Play-safe deletion: no silent deletes.
  - Android 11+ uses `MediaStore.createDeleteRequest()` to trigger the system confirmation UI.
- Storage access: uses scoped storage-friendly read permissions (`READ_MEDIA_IMAGES` or legacy read).
- Analysis is thumbnail-only (never full-res decoding).

---

## Implemented feature milestones (high level)

### MVP (Phase A)
- Dashboard totals (count + size) + “last scan time”
- Folder (bucket) breakdown + folder detail grid
- Reusable filtered grids: WhatsApp, Screenshots, Large files
- Oldest files grouped by month/year (tap month → review grid)

### Phase B (lightweight intelligence; mostly NOT neural ML)
- Similar duplicates: dHash + Hamming distance + clustering
- Blurry candidates: variance of Laplacian blur score + sensitivity threshold UI
- Bursts: time-gap grouping within same bucket
- SQLite cache for analysis results (dHash + blur)

### Background indexing (WorkManager)
- Periodic worker runs in a battery-friendly way to refresh totals and warm the analysis cache.

### UX: Preview + safer selection
- Full-screen preview screen (`PreviewActivity`) for any image in a grid.
- Grid behavior updated:
  - Tap opens preview when not selecting.
  - Long-press enters selection mode and toggles selection.
  - While in selection mode, tap toggles selection.

### UX: Swipeable preview
- Preview supports swipe left/right through the current grid order (ViewPager2).
- Grids pass the current item ID list + tapped index into preview.

### UX: Delete from preview
- Preview supports selecting the current item and deleting selected items using the same Play-safe system confirmation flow.

### UX: Review all suggested (Similar duplicates)
### UX: Review all suggested (Similar duplicates, Bursts)
- Similar duplicates and Bursts screens expose a single action to open one combined grid with all suggested deletes pre-selected.

---

## Code map (important files)

### UI (Activities)
- `app/src/main/java/com/example/app/MainActivity.kt`
- `app/src/main/java/com/example/app/FolderDetailActivity.kt`
- `app/src/main/java/com/example/app/MediaQueryActivity.kt`
- `app/src/main/java/com/example/app/IdsGridActivity.kt`
- `app/src/main/java/com/example/app/PreviewActivity.kt`
- `app/src/main/java/com/example/app/SimilarDuplicatesActivity.kt`
- `app/src/main/java/com/example/app/BlurryCandidatesActivity.kt`
- `app/src/main/java/com/example/app/BurstDetectionActivity.kt`
- `app/src/main/java/com/example/app/EventsActivity.kt`
- `app/src/main/java/com/example/app/SettingsActivity.kt`
- `app/src/main/java/com/example/app/OldestMonthsActivity.kt`

### Settings
- `app/src/main/java/com/example/app/AppSettings.kt`

### Data access
- `app/src/main/java/com/example/app/MediaStoreRepository.kt`

### Adapters
- `app/src/main/java/com/example/app/MonthGroupAdapter.kt`

### Layouts
- `app/src/main/res/layout/activity_analysis_list.xml`

### Analysis
- `app/src/main/java/com/example/app/ThumbnailLoader.kt`
- `app/src/main/java/com/example/app/ImageAnalysis.kt`
- `app/src/main/java/com/example/app/DuplicateClustering.kt`
- `app/src/main/java/com/example/app/BurstGrouping.kt`

### Caching
- `app/src/main/java/com/example/app/AnalysisCacheDb.kt`
- `app/src/main/java/com/example/app/AnalysisPipeline.kt`

### Background work
- `app/src/main/java/com/example/app/BackgroundIndexWorker.kt`
- `app/src/main/java/com/example/app/BackgroundIndexScheduler.kt`

---

## “Communication” / data flow summary

- UI → Repository
  - Activities request lists/totals from `MediaStoreRepository`.
- UI → AnalysisPipeline → CacheDb
  - For Similar/Blurry/Bursts, Activities pass a bounded list of images into `AnalysisPipeline`.
  - Pipeline checks `AnalysisCacheDb` and only processes thumbnails for uncached items.
- UI → Android system delete confirmation
  - Delete always uses Android’s confirmation UI (on supported versions).

No network calls.
No server.
No uploading of photos.

---

## External libraries / tools used

- Kotlin coroutines + `lifecycleScope` for background work.
- RecyclerView for lists/grids.
- Coil for thumbnail display.
- SQLiteOpenHelper for analysis caching.
- WorkManager for periodic background indexing.
- MediaStore for photo indexing and Play-safe deletion request.

---

## Recent implementation notes

- Preview wiring: folder/query/IDs grids pass an `onPreviewRequested` callback into `MediaAdapter`.
- Swipe preview: `PreviewActivity.intentForIds(ids, startIndex, title)` uses ViewPager2 and converts MediaStore IDs into `content://` URIs.
- Events list thumbnails: show a best-effort preview by converting the first MediaStore ID to a `content://` URI.
- Safety mode: Settings toggles gate WorkManager scheduling (cancel unique work when disabled) and cap analysis scan limits.
- Blurry sensitivity UI: `BlurryCandidatesActivity` analyzes once, then lets the user adjust a blur-score threshold (SeekBar) before opening the review grid.
- Similar duplicates: `Review all suggested` button unions cluster suggestions and opens `IdsGridActivity`.

---

## Next planned (developer-facing)

- Events tab (time/location clustering) – no neural models needed.
- Optional People tab (opt-in) using ML Kit face detection + on-device clustering.
- WorkManager background incremental indexing.
