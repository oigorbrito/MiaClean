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

## Final Response Format
When completing a task, your final response must follow this structure:
1. **Files Created/Modified**: List all files.
2. **Commands Executed**: List all commands run and their results.
3. **Validation Results**: Summary of lint/tests.
4. **Pending Items**: Anything that couldn't be completed or requires a decision.
5. **Next Recommended Prompt**: A specific prompt for the next logical step.
