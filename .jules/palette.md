## 2025-05-15 - [Selection Feedback Patterns]
**Learning:** In Jetpack Compose, the `combinedClickable` modifier doesn't provide automatic haptic feedback for long-press events. Manual invocation via `LocalHapticFeedback.current` is necessary for a "native" feel. Additionally, a subtle scale-down animation (e.g., `0.92f`) provides a powerful visual cue for selection state changes that is more immediate than just a border color change.
**Action:** Always pair `onLongClick` with manual haptics and consider `graphicsLayer` scale animations for item selection in lists or grids.
