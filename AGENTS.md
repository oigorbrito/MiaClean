# AGENTS.md - IA Agent Instructions

Welcome, Agent. This file contains specific instructions for your work in the MiaClean repository.

## Mandatory Reading Order
1. `README.md`: High-level overview and stack.
2. `docs/REPOSITORY_AUDIT.md`: Current state of features and stack.
3. `docs/ARCHITECTURE.md`: Technical design and layer boundaries.
4. `RULES.md`: Coding standards and constraints.
5. `harness/TASK_TEMPLATE.md`: How to structure your tasks.

## Standard Commands
- Build: `./gradlew assembleDebug`
- Lint: `./gradlew :app:lintDebug`
- Unit Tests: `./gradlew :app:testDebugUnitTest`

## Development Rules
- **Do NOT re-implement modules**: Always check existing implementations in:
  - `data/classify`: For any categorization or ML logic.
  - `data/billing`: For monetization or subscription logic.
  - `data/delete`: For file deletion logic.
  - `widget`: For any glance widget logic.
  - `work`: For background tasks.
- **No Code Changes in this Task**: This specific task is for documentation and harness setup only. Do not alter Kotlin or Gradle files.
- **Verify before you act**: Always use `ls` and `read_file` to confirm the current state before proposing changes.

## Scope Security
- **Define Task Type**: Before starting, identify the task type in `harness/TASK_TEMPLATE.md`.
- **Run Validation**: Before submission, run `python3 harness/check_scope.py`.
- **Zero Tolerance**: Changes to `data/entitlement/*.kt` or Firebase files outside of an `infra` task will be blocked unless explicitly permitted in `TASK_TEMPLATE.md`.

## Final Response Format
When completing a task, your final response must follow this structure:
1. **Final Report**: Fill and include the content of `harness/FINAL_REPORT_TEMPLATE.md`.
2. **Files Created/Modified**: List all files.
3. **Commands Executed**: List all commands run and their results.
4. **Validation Results**: Summary of lint/tests AND scope check.
5. **Pending Items**: Anything that couldn't be completed or requires a decision.
6. **Next Recommended Prompt**: A specific prompt for the next logical step.
