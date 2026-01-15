# Architecture and Data Flow

This app is an on-device, Play-safe media cleaner that scans the Android MediaStore, computes lightweight analysis on thumbnails, and deletes only via Android system confirmation.

---

## High-level modules

### UI layer (Activities)
- `MainActivity`: permission gate, scan dashboard (totals + folder list), entry points to all cleanup features.
- `FolderDetailActivity`: grid of images for a MediaStore bucket (folder) with multi-select + delete.
- `MediaQueryActivity`: reusable grid for any MediaStore filter/sort (WhatsApp, Screenshots, Large, Oldest).
- `IdsGridActivity`: reusable “review these IDs” grid; supports “Select suggested” + delete.
- `SimilarDuplicatesActivity`: runs duplicate analysis and shows clusters.
- `BlurryCandidatesActivity`: runs blur analysis and opens a pre-selected review grid.
- `BurstDetectionActivity`: groups rapid shots (“bursts”) and shows groups.

### Data access
- `MediaStoreRepository`: all MediaStore queries live here (scan totals, bucket breakdown, fetch media by bucket/IDs, list images for analysis).

### Analysis
- `ThumbnailLoader`: loads small thumbnails only (never full-res).
- `ImageAnalysis`: dHash (difference hash) + blur score (variance of Laplacian).
- `DuplicateClustering`: candidate generation + Hamming distance filtering → clusters.
- `BurstGrouping`: groups by bucket + time gap window.

### Caching
- `AnalysisCacheDb`: SQLite cache of dHash + blur score per media ID, keyed by size/date-taken.
- `AnalysisPipeline`: runs analysis with cache hits to avoid re-processing on repeat scans.

### Background work
- `BackgroundIndexWorker` (WorkManager): periodic, battery-friendly job that:
   - refreshes totals via `MediaStoreRepository.scanDashboard()`
   - warms the analysis cache for a bounded set of recent images
- `BackgroundIndexScheduler`: schedules unique periodic work (`ExistingPeriodicWorkPolicy.KEEP`).

---

## Data flow (what talks to what)

1) UI screen requests a dataset
- Activities call `MediaStoreRepository` to fetch:
   - totals/buckets for dashboard, or
   - a list of items for a grid, or
   - a bounded list of basics for analysis.

2) Optional analysis step (Similar/Blurry/Bursts)
- Activity calls `AnalysisPipeline.analyze()` with a list of `MediaItemBasic`.
- Pipeline:
   - looks up cached analysis in `AnalysisCacheDb`
   - loads thumbnails for missing/changed items via `ThumbnailLoader`
   - computes dHash/blur via `ImageAnalysis`
   - upserts results back into SQLite.

3) Clustering/grouping
- Duplicate clusters: `DuplicateClustering.cluster(analyzed)`
- Bursts: `BurstGrouping.group(analyzed)`

4) Review + delete
- Review screens (folder/query/ids grid) show a thumbnail grid and allow multi-select.
- Delete is always user-confirmed:
   - Android 11+ uses `MediaStore.createDeleteRequest()` → system confirmation UI.
   - Android 10 uses recoverable security exceptions where possible.

---

## Performance principles
- Always analyze thumbnails (~128px), not full images.
- Use background threads (coroutines) and keep UI responsive.
- Cache analysis to avoid repeating expensive steps.
- Limit first-pass analysis size; extend to incremental/background indexing later.
