## 2026-05-18 - [Accessible Selection States in Compose]
**Learning:** For interactive grid items like media thumbnails, using `Modifier.semantics { stateDescription = ... }` provides superior accessibility feedback compared to modifying `contentDescription`. Screen readers announce the state change immediately when the property updates, providing a much smoother experience for visually impaired users.
**Action:** Always use `stateDescription` for togglable or selectable UI elements in Jetpack Compose to maintain clear state communication.
