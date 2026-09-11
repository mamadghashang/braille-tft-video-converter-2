# FishBox Video Export — MODE 1

A clean Android app for **MODE 1 — EXPORT**.

It takes a normal video and converts every frame through the FishBox display pipeline:

1. Decode video frame
2. Convert to logical **240×240** pixels
3. Exact luminance: `0.22R + 0.72G + 0.06B`
4. Optional invert
5. Optional Floyd–Steinberg dithering
6. Exact Unicode Braille 2×4 dot mapping used by the FishBox converter
7. Reconstruct the **actual 240×240 TFT dots**
8. Upscale to **1080×1080** with nearest-neighbor scaling
9. Encode as H.264 MP4

The export keeps the source duration and uses the detected source FPS (falling back to 30 FPS when unavailable).

## Build without programming

### GitHub
1. Create a new GitHub repository.
2. Upload **all contents of this ZIP** to the repository (not the ZIP inside another folder).
3. Commit to `main`.
4. Open **Actions → Build APK**.
5. Open the successful workflow run.
6. Under **Artifacts**, download `FishBoxVideoExport-debug`.
7. Extract it and install `app-debug.apk` on Android.

The repository already contains a GitHub Actions workflow, so no Android Studio is required on your computer.

## App controls

- **SELECT VIDEO** — choose a local video.
- **Invert** — bright source areas become TFT dots.
- **Floyd–Steinberg dithering** — preserves more visual detail in monochrome output.
- **Threshold** — black/white threshold used by the dot conversion.
- **Fit** — letterbox/pillarbox the source inside 240×240. Turn off to crop/fill.
- **EXPORT 1080×1080 MP4** — creates the final video.
- **CANCEL** — stops a long export.

## Important

The exported video is intentionally **square 1080×1080** and visually represents the 240×240 FishBox TFT as hard black/white pixels. It does not add audio in v1.

## Compatibility

- Android 8.0+ (API 26+)
- H.264 MP4 export through Android MediaCodec/MediaMuxer
- No camera permission
- No internet permission
- No external service required
