# Bolt's Journal - Performance Learnings

## 2024-05-24 - Image Downscaling in Scan Pipeline
**Learning:** Decoding full-resolution bitmaps into memory just to re-compress them (e.g., for pHash calculation) is a major memory and CPU bottleneck. By using `BitmapFactory.Options.inSampleSize`, we can downscale the image during the decoding phase itself, significantly reducing the memory footprint and the time spent in both decoding and subsequent compression.
**Action:** Always use `inJustDecodeBounds` to determine image dimensions and calculate an appropriate `inSampleSize` before decoding images for feature extraction or thumbnail generation.
