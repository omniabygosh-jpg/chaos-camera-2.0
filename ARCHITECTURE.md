# CHAOS CAMERA — architecture notes

## Runtime flow

1. Activity requests camera permission.
2. Camera2 characteristics are queried for ISO range, exposure-time range, lens minimum focus distance, and manual sensor capability.
3. A preview stream is opened and displayed continuously in `TextureView`.
4. On Start, a delay is sampled uniformly between the configured interval bounds.
5. At the end of the delay, one still-capture request is assembled. Enabled random controls are sampled independently; values are clamped to the hardware capability range.
6. JPEG bytes arrive through `ImageReader` and are written to MediaStore.
7. After capture completion, the next random delay is sampled. Stop cancels the pending callback.

## Parameter semantics

- Interval: seconds between the end of one capture and scheduling the next interval.
- ISO: camera sensitivity integer; manual sensor capability required.
- Exposure: seconds in the UI, converted to nanoseconds for Camera2.
- Focus: lens diopters (`0` means infinity; larger values focus closer), if manual lens focus is supported.
- Brightness: app-window brightness from 1% to 100%; does not control sensor exposure or guarantee equivalent battery savings.

## Important product decision

Version 0.1 operates only while the app is foregrounded with the live preview visible. Background capture is intentionally excluded from this first build because modern Android versions impose camera background restrictions and because the agreed user experience is the live-preview mode.
