# Architecture and Data Flow

## Components
- **Activity**: `MainActivity` owns the UI, permission flow, and launches the scan coroutine.
- **UI layout**: `res/layout/activity_main.xml` hosts the button, progress bar, and result text views.
- **Persistence**: `SharedPreferences` snapshot stores last scan count/bytes/timestamp to show deltas.
- **Media access**: `MediaStore.Images` query enumerates images with ID, size, name, and timestamps.
- **Hashing**: 8x8 average hash (perceptual) on sampled images to group near-duplicates.

## Flow
1. **Permission**: On button tap, request `READ_MEDIA_IMAGES` (API 33+) or `READ_EXTERNAL_STORAGE` (API <=32). If denied, show toast.
2. **Scan** (`analyzeMedia`):
   - Query MediaStore for all images → `MediaItem` list + total bytes.
   - Compute aggregate stats (count, bytes → GB string).
   - Sample up to 400 items, compute average hash per item, bucket identical hashes → duplicate groups.
   - Sort largest files by size; oldest by `DATE_ADDED` when present.
   - Build delta text vs previous snapshot; persist new snapshot.
3. **UI update**: Back on main thread, fade-in updated texts (totals, deltas, duplicates, top lists) and hide loader.

## Key methods (all in `MainActivity`)
- `runImageAnalysis()`: permission gate, loader state, coroutine launch.
- `loadMediaItems()`: MediaStore query to `MediaItem` list + byte sum.
- `computeAverageHash(Uri)`: decode downsampled bitmap, 8x8 luminance grid → 64-bit string.
- `findDuplicateGroups()`: hash buckets → `DuplicateGroup` list sorted by total size.
- `formatTopList()`, `formatBytesShort()`, `buildDeltaText()`: presentation helpers.
- `loadSnapshot()` / `saveSnapshot()`: read/write `SharedPreferences` for last scan.

## UI contracts
- IDs used in code exist in `activity_main.xml` (`analyzeButton`, `loadingBar`, `totalImagesText`, etc.).
- Theme `Theme.App` in `values/themes.xml` matches `android:theme` in `AndroidManifest.xml`.

## Threading
- Media scan and hashing run on `Dispatchers.IO` inside `lifecycleScope.launch`.
- UI updates happen on the main thread after `withContext` completes.

## Data limits and constants
- `TOP_COUNT = 5`: number of largest/oldest shown.
- `MAX_HASH_ITEMS = 400`: cap for hashing to avoid heavy work on large libraries.
- Hashing uses `inSampleSize=8` to keep memory low.

## Extending
- Add deletions/actions for duplicate groups by requesting write permissions and using `ContentResolver` deletes.
- Add paging or search by augmenting the MediaStore query with selection/sort.
- Replace average hash with pHash/dHash for better robustness.
