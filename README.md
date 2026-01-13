# Hecker Image Analyzer

Android app that scans device images, summarizes total count/space, highlights near-duplicates via perceptual hashing, and lists largest/oldest files.

## Quick start
1. Open the project folder in Android Studio (Hedgehog/2023.1.1 or newer).
2. When prompted, use Gradle JDK 17.
3. If Gradle wrapper is missing its JAR, regenerate it (this requires a JDK):
	- In Android Studio: **Terminal** tool window -> run `gradlew.bat wrapper`
	- Or set `JAVA_HOME` to Android Studio's JDK (JBR) and run `gradlew.bat wrapper` from PowerShell.
4. Build & run on a device (recommended) with photos. Emulator must have media files to show meaningful results.

## Run on a real Android phone (step-by-step)
1. On your phone: enable **Developer options** and **USB debugging**.
2. Connect phone via USB, accept the “Allow USB debugging” prompt.
3. In Android Studio: select your device in the device dropdown.
4. Click **Run** (green triangle) to install and start the app.
5. On first use, tap **Analyze Images** and grant the permission when prompted.

### What to test
- Run once, then run again to verify the **Since Last Scan** delta changes.
- Take a few similar/duplicate photos (or copy the same image) and rerun to see **Near Duplicates**.
- Check **Largest Files** and **Oldest Files** lists look reasonable.

## Permissions
- Android 13+ (`READ_MEDIA_IMAGES`)
- Android 12 and below (`READ_EXTERNAL_STORAGE`)

## What it does
- Queries `MediaStore.Images` to collect image URIs, names, sizes, and timestamps.
- Computes aggregate stats (count, total bytes → GB string).
- Samples images (up to 400) and uses 8x8 average hash to find near-duplicate groups.
- Lists top 5 largest and oldest images.
- Persists a snapshot (count/bytes/timestamp) in `SharedPreferences` to show deltas on the next scan.

## Build settings
- compileSdk / targetSdk: 34
- minSdk: 24
- Language: Kotlin, JVM target 17
- Key deps: `androidx.core-ktx`, `appcompat`, `material`, `activity-ktx`, `lifecycle-runtime-ktx`

## Running a clean sync
If sync fails because `gradle-wrapper.jar` is absent:
- Run `gradlew.bat wrapper` to regenerate it (best done from Android Studio Terminal, which already has a JDK configured).

## Testing tips
- Run on a real device with a sizable photo library for meaningful duplicate detection.
- Compare “Since Last Scan” after adding/removing photos to see delta behavior.
