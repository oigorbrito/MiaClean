# PR_CHECKLIST.md

- [ ] Does this change follow the mandatory reading order in `AGENTS.md`?
- [ ] Is the Task Type correctly declared and respected?
- [ ] Has `harness/check_scope.py` been executed and passed?
- [ ] Have I checked for existing modules in `data/` before adding new logic?
- [ ] Does it respect Android Scoped Storage / SAF rules?
- [ ] Are all new dependencies added to `gradle/libs.versions.toml`? (If applicable)
- [ ] Do all tests pass locally?
- [ ] Is the UI accessible and localized (PT-BR, ES)?
- [ ] Are ML failures handled gracefully?
- [ ] Is there any sensitive data being logged?
- [ ] Has the documentation been updated to reflect these changes?
