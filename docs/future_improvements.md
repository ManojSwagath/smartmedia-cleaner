# SmartMedia Cleaner — Future Improvements / Roadmap (Plan Only)

This document is a **planning reference** for the future evolution of SmartMedia Cleaner.
It describes **what we want to build**, **in what order**, and the **Android/Play Store constraints** we must respect.

> Status: Plan-only. No implementation details/code in this doc.

---

## 0) Vision (Product Goal)

Build a photo cleanup + organization app that:
- Shows a clear overview of storage used by media.
- Detects “junk” / low-value media (WhatsApp spam, duplicates, blurred shots, screenshots, bursts).
- Helps users reclaim space with **minimal effort**, while staying **Play Store compliant**.
- Over time, organizes photos into:
  - **People** (Manoj / Mother / Brother / Sister…)
  - **Events** (time/location-based; eventually semantic labels like birthday/marriage)

---

## 1) Non‑negotiable Constraints (Android + Play Store)

### 1.1 Deletion cannot be silent
- On modern Android (scoped storage), deleting user photos typically requires a **system confirmation UI**.
- “Single button delete” must mean:
  - Tap once → system dialog appears → user confirms → deletion happens.
- Avoid `MANAGE_EXTERNAL_STORAGE` (“All files access”) for Play Store viability.

### 1.2 Background work is limited
- “Always running” background scanning is not guaranteed.
- Use periodic, battery-friendly background jobs (WorkManager) plus manual “Scan now”.
- UX must communicate:
  - “Last scanned time”
  - “Background scans may run when device allows”

### 1.3 Privacy and trust
- Default to **on-device processing**.
- Face-based organization must be **explicit opt-in** and include a “Delete my face data” option.

---

## 2) UX / Screens (Target Experience)

### 2.1 Home / Scan
- Top section:
  - Scan button
  - Total images
  - Total size occupied
  - Last scan time
- Below (scrollable sections):
  - Folders overview
  - WhatsApp cleanup
  - Screenshots cleanup
  - Large files
  - Duplicates / Similar
  - Blurry / Low-quality
  - People (future)
  - Events (future)

### 2.2 Folder detail screen
- Shows media in that folder with filters:
  - Blurry candidates
  - Duplicates/similar
  - Old/large
- Actions:
  - Select all suggested
  - Delete selected (system confirmation)

### 2.3 People screen (future)
- Shows people “albums” (Manoj, Mother, Brother…)
- Each person album shows photos of that person.
- Management:
  - Rename person
  - Merge/split suggestions
  - Delete all person data (embeddings/clusters)

### 2.4 Events screen (future)
- Shows event groups (date ranges + location if available)
- Event details: photos grouped inside
- Optional: user can rename events (“Birthday”, “Marriage”, “Trip to …”)

---

## 3) Feature Roadmap (Phased)

### Phase A — MVP (Monetizable, Play‑safe, fast to build)
Goal: deliver immediate value and build trust.

A1) Media indexing + totals
- Count images and compute total size.
- Show folder (bucket) breakdown.

A2) WhatsApp / “junk folder” cleanup
- Detect common WhatsApp media folders.
- Provide:
  - Size, count
  - Suggested cleanup list
  - Delete selected (system confirmation)

A3) Screenshots + Downloads + memes
- Identify screenshot folder + downloads.
- Show “likely removable” suggestions.

A4) Large files + Oldest files
- Large files list + total reclaimable size.
- Oldest list grouped by month/year.

A5) Safe delete flow
- Batch delete with system confirmation.
- Clear UX that deletion is permanent.

A6) Upgrade UI foundations
- Multi-section UI; consistent navigation.
- Quick preview of items before deletion.

Deliverable: “Cleaner that actually saves space” without AI.

---

### Phase B — v1 (Intelligence without heavy biometrics)
Goal: smarter suggestions (still mostly heuristics / lightweight processing).

B1) Similar duplicates
- Similarity detection using perceptual hashing on thumbnails.
- Cluster UI: choose “keep best one” suggestions.

B2) Blur / low-quality scoring (review-first)
- Compute blur score on small thumbnails.
- User adjusts sensitivity.
- Never auto-delete without user review + system confirmation.

B3) Burst detection
- Group burst photos (by timestamp proximity) and suggest keeping best.

B4) Background indexing (battery-friendly)
- Periodic indexing via WorkManager.
- Notification optional (only if needed, ideally avoid).

Deliverable: “Smart cleanup suggestions” that feel magical but are safe.

---

### Phase C — v2 (People + Events: R&D tier)
Goal: organization features users expect from modern galleries.

C1) Events (time/location clustering)
- Group into events by time gaps; use GPS when available.
- Let user rename events.

C2) People (face detection + clustering)
- Opt-in feature.
- Start with:
  - Face detection + face-crop grid
  - User manually assigns names to clusters
- Later:
  - Add embeddings + clustering improvements
  - Suggested merges/splits

C3) Semantic labels (Birthday/Marriage)
- High-risk for accuracy.
- Start as user-defined labels.
- If adding ML classification, keep it optional and transparent.

Deliverable: “Organized gallery experience” without overpromising.

---

## 4) “Automation” Strategy (What’s possible vs not)

### What we CAN automate
- Finding candidates (blurry/similar/WhatsApp junk) and surfacing them.
- Periodic indexing and sending a notification like:
  - “SmartMedia Cleaner found 420MB you can reclaim.”

### What we CANNOT (Play‑friendly) fully automate
- Silent background deletion of user photos.
- Automatic deletion without system confirmation.

Design principle: **automate discovery + minimize taps**, but keep deletion user-confirmed.

---

## 5) Monetization Plan (Play Billing)

### Recommended: Freemium + Subscription
Free:
- Scan totals
- Folder view
- Basic large files cleanup

Pro (subscription):
- Blur detection
- Similar duplicates clustering
- People tab (face features)
- Scheduled cleanup reminders
- Advanced filters (“keep best shot”)

### Notes
- Use Google Play Billing for digital features.
- Avoid paywalls on basic safety features (e.g., preview before delete).

---

## 6) SaaS / Cloud (Optional, Later)

### Strong recommendation: start on-device
- Faster iteration, fewer compliance burdens, simpler privacy story.

### If SaaS is needed (multi-device sync)
- Sync **metadata only** by default:
  - folder summaries
  - analysis scores
  - clusters/labels
- Do NOT upload actual photos unless user explicitly opts in (major privacy/security effort).

---

## 7) Privacy, Security, and Compliance Checklist

- Transparent permission explanations (why we need photo access).
- Clear deletion warnings.
- Data Safety form aligned with actual behavior.
- Face processing:
  - opt-in
  - on-device by default
  - “Reset face data” button
  - no sharing/uploading without explicit consent

---

## 8) Quality & Reliability Goals

- Handles libraries with 10k+ images without crashing.
- No full-res decoding for analysis (thumbnail only).
- Fast incremental scans.
- Clear errors when permissions are limited (“selected photos only”).

---

## 9) Development Milestones (Suggested)

1) MVP cleaner sections + delete flow
2) Similar duplicates (pHash) + clustering UI
3) Blur scoring + review UI
4) Background indexing + “space reclaim” notifications
5) Events (time/location)
6) People (opt-in) + clustering + naming

---

## 10) Open Questions (to decide early)

- Minimum Android version target (Android 11+ recommended for best delete UX).
- Scope: images-only or include videos?
- Play Store positioning:
  - “Cleaner” vs “Organizer” vs both?
- Whether to support “selected photos only” permission mode gracefully.

---

## 11) Current App vs Target App

Current app (today):
- Scan button
- Total images
- Total size

Target app (future):
- Sections for cleanup + organization
- Safe bulk delete with system confirmation
- Optional People and Events organization
