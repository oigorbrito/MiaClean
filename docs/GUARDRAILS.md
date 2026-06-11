# Harness Scope Guardrails

Para garantir que as tarefas permaneçam dentro do escopo e evitar mudanças acidentais ou perigosas em partes sensíveis do sistema, o harness agora inclui um sistema de validação de escopo.

## Escopos Disponíveis

- `somente-documentacao`: Restringe mudanças a arquivos de documentação (`.md`, `harness/*`, `docs/*`).
- `somente-testes`: Restringe mudanças a arquivos de teste (`*Test.kt`, `*Test.java`, `app/src/test/*`, etc).
- `refatoracao`: Mudanças em código existente sem alteração de comportamento.
- `feature`: Implementação de novas funcionalidades.
- `bugfix`: Correção de bugs.
- `infra`: Mudanças em build scripts, CI/CD, etc.

## Como Usar

Antes de submeter sua PR, execute o script de validação:

```bash
python3 harness/guardrail.py --scope <seu-escopo>
```

### Exemplo: Somente Documentação

Se você estiver fazendo uma tarefa de documentação:

```bash
python3 harness/guardrail.py --scope somente-documentacao
```

Se você acidentalmente alterou um arquivo Kotlin ou um arquivo de configuração do Firebase, o script bloqueará a submissão.

## Relatório de Saída

O script gera um relatório detalhado:

```
========================================
       HARNESS SCOPE REPORT
========================================
Scope: somente-documentacao
Status: BLOQUEADO
----------------------------------------
Files Changed: 3
 [OK] README.md
 [OK] docs/ARCH.md
 [BLOCKED] app/src/main/java/MainActivity.kt
----------------------------------------
Risks: Alteração de código runtime em tarefa de documentação.
========================================
FINAL DECISION: BLOQUEADO
========================================
```

## Regras de Restrição

- **`somente-documentacao`**: É o modo mais estrito. Bloqueia qualquer arquivo que não seja explicitamente documentação ou arquivos de configuração do harness.
- **Backend Firebase**: Mudanças no backend Firebase são desencorajadas a menos que o escopo seja explicitamente `infra` ou `feature` com justificativa.
- **Cache de Entitlement**: Alterações no cache de entitlement são sensíveis e devem ser revisadas com cuidado extra.
