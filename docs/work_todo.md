# SmartMedia Cleaner — Work Tracker (Non-Technical)

This document is a simple checklist to verify what has been built, what is planned next, and how to confirm it works.

Last updated: 2026-01-14

---

## What’s already built (✅ done)

### ✅ Scan + totals
- ✅ App asks for photo permission (Android-supported way).
- ✅ Scan button counts total images and total space used.
- ✅ Shows “Last scan time”.

### ✅ Folder (album) breakdown
- ✅ Shows a list of folders/albums (example: Camera, Screenshots, WhatsApp Images).
- ✅ Each folder shows image count + size used.
- ✅ Tap a folder to open it.

### ✅ Safe delete (Play Store friendly)
- ✅ Inside a folder, photos show as a grid of thumbnails.
- ✅ Tap a photo to preview it full-screen.
- ✅ Long-press a photo to start selecting (then tap to toggle selection).
- ✅ Press “Delete selected” → Android shows a system confirmation UI → only after user confirms, deletion happens.

### ✅ Cleanup sections (quick entry points)
- ✅ WhatsApp cleanup screen (shows WhatsApp-related images; review + delete)
- ✅ Screenshots cleanup screen (shows screenshots; review + delete)
- ✅ Large files screen (biggest images first; review + delete)
- ✅ Oldest files screen (grouped by month/year; review + delete)

### ✅ Phase B (early “smart” features)
- ✅ Similar duplicates (beta): scans recent images, forms clusters, suggests “keep best / delete the rest”.
- ✅ Blurry / low-quality (beta): finds likely blurry images and pre-selects them for review.
- ✅ Bursts (beta): groups rapid shots and suggests keeping the best one.

### ✅ Events (early, time-based)
- ✅ Events: groups photos into events by time gaps (no ML needed).

### ✅ Performance improvement
- ✅ Analysis caching: after you run Similar/Blurry/Bursts once, repeating it should be faster.

### ✅ Safety controls
- ✅ Settings screen: toggle background indexing on/off.
- ✅ Low disturbance mode: reduces scan sizes for analysis screens (helps battery/older devices).

---

## How you can check it works (quick tests)

1) Open the app → tap “Scan”
   - Expect: totals and last scan time update.
2) Tap any folder
   - Expect: grid of thumbnails loads.
3) Tap a photo
   - Expect: full-screen preview opens.
   - Swipe left/right to browse nearby photos.
   - Tap the preview to close.

3b) In preview: tap “Select”, then “Delete selected”
   - Expect: Android system delete confirmation appears.
4) Long-press 1 photo, then tap 1–2 more photos, then tap “Delete selected”
   - Expect: Android confirmation dialog appears.
   - If you cancel: nothing is deleted.
   - If you confirm: selected items disappear from the grid.

4b) Long-press a photo, then drag your finger across the grid
   - Expect: photos you drag over get selected quickly (multi-select in one stroke).

5) Tap “Similar duplicates”
   - Expect: an “Analyzing…” progress screen.
   - Then: a list of clusters appears.
   - Tap a cluster → you’ll see the images in that cluster.
   - Tap “Select suggested” → it selects what the app recommends deleting.

6) Tap “Blurry / low-quality”
   - Expect: an “Analyzing…” progress screen.
   - Then: you’ll see a blur sensitivity slider + “Review candidates”.
   - Tap “Review candidates” → grid opens with blurry candidates, pre-selected.

7) Tap “Bursts”
   - Expect: an “Analyzing…” progress screen.
   - Then: a list of burst groups appears.
   - Tap a burst → grid opens with suggested deletes available.

8) Tap “Oldest files”
   - Expect: list of months/years appears.
   - Tap a month → grid opens to review/delete.

9) Tap “Events”
   - Expect: an “Analyzing…” progress screen.
   - Then: a list of events appears (each event is a time range).
   - Tap an event → grid opens to review/delete.

10) Tap “Settings”
   - Turn off “Background indexing” if you want zero background work.
   - Turn on “Low disturbance mode” if you want smaller/safer scans.

---

## What’s next (🚧 planned)

### Phase A (MVP cleanup sections)
- [ ] Improve WhatsApp detection (use additional folder/path heuristics)
- [x] Oldest files: group by month/year (better browsing)

### Phase B (make it feel “wow”)
- [ ] Similar duplicates: faster scan (caching) + better clustering
- [x] Blurry: add sensitivity control (slider) and show blur score threshold
- [ ] Burst detection (group by timestamp proximity)

### UX improvements
- [x] Add a preview screen (tap image → full screen preview)
- [x] Add Settings (disable background work + low disturbance mode)
- [x] Preview: swipe left/right
- [x] Preview: select + delete (with system confirmation)
- [x] Add “Select/Review all suggested” for a section (Similar duplicates, Bursts)
- [ ] Add clear empty states (“No images found”) and helpful messages

---

## Important limitations (by design)

- Deleting photos is NOT silent/automatic.
  - Android requires confirmation, so the app must show a system dialog.
- The app should avoid “All files access” permission for Play Store safety.

- Background scanning is best-effort.
   - The app schedules periodic background indexing, but Android may delay it depending on battery/idle state.

---

## Notes / Decisions

- Minimum Android: currently supports Android 7.0+ (minSdk 24).
- Scope: currently images only (no videos yet).
