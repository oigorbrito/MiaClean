# MiaClean Governance

## Purpose
This document is the canonical source for repository rules, acceptance criteria, and PR review expectations. Other docs and templates should reference this file instead of duplicating policy.

## Repository Rules
- Follow `RULES.md` for all Kotlin, Compose, storage, permissions, billing, and testing changes.
- Preserve the current Android architecture unless a migration document explicitly changes it.
- Check for an existing implementation before adding new logic in `data/classify`, `data/billing`, `data/delete`, `widget`, or `work`.
- Keep changes scoped; do not introduce unrelated refactors or dependency churn.
- Prefer small, verifiable diffs over broad rewrites.
- Do not log sensitive media information or paths in production.

## Acceptance Criteria
- Code follows `RULES.md`.
- Lint passes with no new warnings or errors.
- Relevant unit tests pass locally.
- UI changes are smoke-tested manually.
- Documentation is updated when the change affects architecture, workflow, or behavior.
- Scope guardrails pass for the declared task scope.

## PR Checklist
- Confirm the mandatory reading order in `AGENTS.md`.
- Validate the declared scope with the harness before merge.
- Check for existing modules or helpers before adding new logic.
- Respect Android Scoped Storage / SAF rules.
- Add dependencies to `gradle/libs.versions.toml` only when needed.
- Run the relevant tests locally.
- Confirm no sensitive data is logged.
- Update docs if the change affects behavior or architecture.

## Canonical References
- `AGENTS.md`: agent entrypoint and repo instructions
- `RULES.md`: coding standards and constraints
- `harness/TASK_TEMPLATE.md`: task structure
- `harness/check_scope.py`: canonical scope validator
- `harness/PR_CHECKLIST.md`: legacy checklist, now mirrors this document
- `harness/DEFINITION_OF_DONE.md`: legacy DoD, now mirrors this document
