# AGENTS.md - IA Agent Instructions

Welcome, Agent. This file contains specific instructions for your work in the MiaClean repository.

## Agent Economy Mode (Mandatory)
All agents MUST operate in economy mode to reduce token consumption and limit scope creep.
See detailed profiles and rules in: [docs/prompts/BPT_AGENT_ECONOMY_MODE.md](docs/prompts/BPT_AGENT_ECONOMY_MODE.md)

### Global Economy Rules
- Read little, change little, validate only what is necessary.
- Do not audit the entire repository.
- Limit to 5 open files unless justified.
- Respond in short format: Verdict, Changed files, Validation, Main risk, Next step.

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
- **Do NOT re-implement modules**: Always check existing implementations in `data/classify`, `data/billing`, `data/delete`, `widget`, `work`.
- **Verify before you act**: Always use `ls` and `read_file` to confirm state.

## Final Response Format
When completing a task, your final response must follow the structured format defined in `BPT_AGENT_ECONOMY_MODE.md`.
