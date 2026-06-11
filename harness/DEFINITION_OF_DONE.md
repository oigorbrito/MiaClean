# DEFINITION_OF_DONE.md

A task is considered "Done" when:

0. **Scope and Security**:
   - Task type is correctly declared in `TASK_TEMPLATE.md`.
   - File changes are validated against the declared scope using `harness/check_scope.py`.
   - No out-of-scope changes to Firebase, Backend, or Core Entitlement logic.
   - Final Report is generated and attached to the PR/Task.

1. **Code Quality**:
   - Follows `RULES.md`.
   - No new lint warnings/errors (`./gradlew :app:lintDebug`).
   - No anti-patterns used (`docs/ANTI_PATTERNS.md`).

2. **Testing**:
   - New logic is covered by unit tests.
   - All tests pass (`./gradlew :app:testDebugUnitTest`).
   - Manual smoke test performed if UI was touched.

3. **Documentation**:
   - `README.md` updated if necessary.
   - `docs/DECISION_LOG.md` updated for significant changes.
   - New files added to the layout description in `README.md`.

4. **Productivity**:
   - Used the appropriate prompts from `PROMPTS.md`.
   - Followed the structure in `harness/TASK_TEMPLATE.md`.

5. **Submission**:
   - PR checklist completed.
   - Commit message follows conventions.
