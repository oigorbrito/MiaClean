# ROADMAP.md

## Already Implemented
- **Core Scan Pipeline**: MediaStore + SAF scanning with MD5 and pHash.
- **Widgets**: Home screen status widget using Glance.
- **Localization**: English, Portuguese (BR), and Spanish support.
- **Billing Infrastructure**: Integration with Google Play Billing.
- **Consented Deletion**: Media deletion using the modern MediaStore API.

## Partial / Ongoing
- **Hashing**: MD5 and pHash generation is functional and independent of any persistence layer.
- **Contextual Classifier**: Basic heuristic classification for Memes/Selfies/Documents exists. Needs refinement; does not rely on persistence.
- **UI Polish**: Basic Results screen exists; UI deduplication improvements are scoped to future work.

## Pending / Future
- **Cloud Backup Integration**: Detect if media is backed up to Google Photos (requires network, no persistence impact).
- **Advanced Semantic Grouping**: Utilize MediaPipe embeddings for similarity detection (independent of DB).
- **Automated Media Cleanup**: Scheduled background cleanup for specific categories (does not affect current storage schema).
