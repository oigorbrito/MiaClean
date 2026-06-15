# Scan Quality Report

## Configuration
- **pHash Hamming Distance Threshold**: 5
- **Semantic Cosine Similarity Threshold**: 0.92

## Metrics Summary
- **Precision**: 87.50%
- **Recall**: 100.00%
- **False Positives (Pairs)**: 1
- **False Negatives (Pairs)**: 0

## Grouping Results
### Strategy: EXACT_MD5
- Group 1: exact_1a.jpg, exact_1b.jpg

### Strategy: PERCEPTUAL_PHASH
- Group 1: resized_2a.jpg, resized_2b.jpg
- Group 2: compressed_3a.jpg, compressed_3b.jpg
- Group 3: screenshot_4a.jpg, screenshot_4b.jpg
- Group 4: selfie_5a.jpg, selfie_5b.jpg
- Group 5: meme_6a.jpg, meme_6b.jpg
- Group 6: distinct_8a.jpg, distinct_8b.jpg

### Strategy: SEMANTIC_EMBED
- Group 1: semantic_7a.jpg, semantic_7b.jpg

## Error Analysis
### False Positives (Incorrectly Grouped)
- distinct_8a.jpg <-> distinct_8b.jpg

## Suggestions
- If Precision is low (High FP): Increase SEMANTIC_THRESHOLD (e.g. 0.95) or decrease PHASH_THRESHOLD (e.g. 3).
- If Recall is low (High FN): Decrease SEMANTIC_THRESHOLD (e.g. 0.88) or increase PHASH_THRESHOLD (e.g. 8).
- Semantic embedding currently uses a greedy grouping; a more robust clustering (e.g. DBSCAN) might improve stability.
