## 2025-06-15 - [Accessible Selection State]
**Learning:** For toggleable items like media thumbnails, relying only on visual cues (borders/icons) is insufficient for screen readers. Using `Modifier.semantics { stateDescription = ... }` ensures that TalkBack announces the state ("Selected" / "Not selected") immediately upon focus or state change.
**Action:** Always include a localized `stateDescription` in the root semantic container of interactive items that hold a binary state.
