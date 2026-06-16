## 2025-05-15 - [MediaThumbnail Accessibility & Selection Feedback]
**Learning:** For selection-heavy grid interfaces, immediate tactile and visual feedback (haptics + scale) significantly reduces user uncertainty. Semantic `stateDescription` is more effective than generic content descriptions for communicating toggleable states to screen reader users.
**Action:** Always combine `Modifier.semantics { stateDescription = ... }` with visual selection indicators to ensure accessibility parity.
