# TASK_TEMPLATE.md

## Task Type
- **Type**: infra

## Context
Evolução do harness para segurança contra mudanças fora de escopo.

## Analysis
- **Affected Packages**: `harness/`, `docs/`, `AGENTS.md`
- **Existing Logic**: Harness básico existente com templates de task e checklist.
- **Constraints**: Não alterar lógica runtime, não alterar Firebase, não alterar Entitlement.

## Scope Declaration
- **Allowed Overrides**: [harness/check_scope.py, harness/FINAL_REPORT_TEMPLATE.md, docs/HARNESS_USAGE.md]

## Proposed Changes
1. Implementar `harness/check_scope.py`.
2. Atualizar templates do harness.
3. Atualizar `AGENTS.md`.
4. Criar documentação de uso.

## Verification Plan
- [x] Scope validation (`python3 harness/check_scope.py`)
- [x] Unit tests for project
- [x] Manual tests of the script

## Results
[PASSOU]
