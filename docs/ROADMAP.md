# ROADMAP.md

## Already Implemented
- **Core Scan Pipeline**: MediaStore + SAF scanning with MD5 and pHash.
- **Widgets**: Home screen status widget using Glance.
- **Localization**: English, Portuguese (BR), and Spanish support.
- **Billing Infrastructure**: Integration with Google Play Billing.
- **Consented Deletion**: Media deletion using the modern MediaStore API.

## Partial / Ongoing
- **Contextual Classifier**: Basic heuristic classification for Memes/Selfies/Documents exists. Needs refinement with ML Kit OCR and more robust face detection.
- **UI Polish**: Basic Results screen exists, but needs better selection UX and full-screen previews.

## Pending / Future
- **Cloud Backup Integration**: Detect if media is already backed up to Google Photos.
- **Advanced Semantic Grouping**: Fully utilize MediaPipe embeddings for "Similar but not duplicate" detection.
- **Automated Media Cleanup**: Scheduled background cleanup for specific categories (e.g., old memes).

## Recommended Next PRs
1. **Refine ML Classification**: Integrate ML Kit OCR to improve Document detection.
2. **Improve Selection UX**: Add "Select All", "Deselect All", and smart selection (keep highest quality).
3. **Add Full-screen Preview**: Implement a pager-based full-screen media viewer in the Results screen.
