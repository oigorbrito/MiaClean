## 2024-06-19 - [Accessibility] Enhanced Media Selection Feedback
**Learning:** Adding `stateDescription` to interactive elements like `MediaThumbnail` ensures that screen reader users receive immediate and clear feedback about the selection state, which is otherwise only conveyed visually by a border and an icon. Combining this with haptic feedback on long-press and a scale-down animation provides a multi-sensory confirmation of the action.
**Action:** Always include `Modifier.semantics { this.stateDescription = ... }` for toggleable UI components and pair with haptics for tactile confirmation.

## 2024-06-19 - Pre-existing Blockers
**Observation:** The test suite `:app:testDebugUnitTest` fails to compile due to missing string resources (`scan_error_permission_revoked`, `scan_error_media_unavailable`, `scan_error_unexpected`) and missing helper functions (`scanFailureResult`) in `ScanRepositoryHardeningTest.kt` and `ScanWorkerTest.kt`.
**Action:** These are documented as pre-existing blockers as per AGENTS.md and memory, and are not fixed in this UX-focused PR to maintain scope.
