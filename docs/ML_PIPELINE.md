# ML_PIPELINE.md - Machine Learning Integration

## Current Pipeline Components

### 1. MediaPipe Image Embedder
- **Purpose**: Generates semantic embeddings for images to detect visually similar (but not identical) items.
- **Model**: Expected at `app/src/main/assets/image_embedder.tflite`.
- **Implementation**: `com.miaclean.app.data.ml.ImageEmbedderWrapper`.

### 2. MediaPipe Face Detector
- **Purpose**: Detects faces to classify images as "Selfies".
- **Model**: Expected at `app/src/main/assets/face_detector.tflite`.
- **Implementation**: `com.miaclean.app.data.classify.SelfieDetector`.

### 3. ML Kit Text Recognition (OCR)
- **Purpose**: Extracts text from images to identify documents, receipts, or text-heavy memes.
- **Implementation**: `com.miaclean.app.data.ml.TextRecognizer` (Pendente de decisão - verify if already implemented or just planned).

## Model Management
- **Location**: Models reside in the `app/src/main/assets/` directory.
- **Validation**: `ImageEmbedderWrapper` and `SelfieDetector` use lazy initialization and try-catch blocks to handle missing `.tflite` files gracefully.
- **Asset Presence**: The build does not fail if models are missing, but the pipeline falls back to MD5/pHash only.

## Runtime Risks
- **Memory Consumption**: Decoding bitmaps for ML can lead to OOM. Use `inSampleSize` for downscaling.
- **Battery Impact**: Avoid running ML on every media item in the background without checking battery/charging state.
- **Performance**: ML tasks should always run on background threads (`Dispatchers.Default` or `IO`).

## Strategy for New Models
1. Add `.tflite` to assets.
2. Create a wrapper in `data/ml`.
3. Implement lazy initialization with a graceful fallback.
4. Integrate into `MediaClassifier` or a new stage in `ScanRepository`.
