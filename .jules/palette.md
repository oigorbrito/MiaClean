# Palette Journal - UX & Accessibility Learnings

## 2025-05-14 - Enhanced Media Selection Feedback
**Learning:** In Jetpack Compose, `combinedClickable` does not provide native haptic feedback for long-press selection by default. To make the interaction feel responsive and standard on Android, haptic feedback must be manually triggered using `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` within the `onLongClick` callback.
**Action:** Always pair long-press selection actions with manual haptic feedback for better tactile confirmation.

**Learning:** For toggleable items like thumbnails, using `Modifier.semantics { stateDescription = ... }` is superior to updating `contentDescription`. `stateDescription` allows TalkBack to announce the selection state change (e.g., "Selected" or "Not selected") while keeping the `contentDescription` focused on the item's identity (e.g., the filename).
**Action:** Use `stateDescription` for announcing state changes in accessible components.
