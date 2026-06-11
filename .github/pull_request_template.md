## Descrição
[Descreva as mudanças aqui]

## Task-Scope
Task-Scope: somente-documentacao

### Escopos disponíveis:
- `somente-documentacao`: Mudanças exclusivas em arquivos de documentação (`.md`, `docs/**`).
- `somente-testes`: Mudanças exclusivas em testes (`app/src/test/**`, `app/src/androidTest/**`).
- `refatoracao`: Reorganização de código sem alteração de comportamento.
- `feature`: Implementação de novas funcionalidades.
- `bugfix`: Correção de erros no código existente.
- `infra`: Alterações em CI, scripts do harness, Gradle ou arquivos críticos.

*Nota: Ao declarar o Task-Scope, uma label correspondente será aplicada automaticamente à PR (ex: `scope: docs`).*

## Checklist
- [ ] O código segue as regras do `RULES.md`.
- [ ] Foram adicionados testes unitários (se aplicável).
- [ ] O harness de escopo passou localmente.

Task-Scope: infra
