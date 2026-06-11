# Guia de Uso do Harness e Segurança de Escopo

Este documento descreve como utilizar o harness do projeto MiaClean para garantir que as mudanças permaneçam dentro do escopo declarado, evitando regressões críticas ou alterações acidentais em áreas sensíveis (como Firebase ou Entitlement logic).

## Tipos de Tarefa

Ao iniciar uma tarefa, você deve declarar seu tipo no arquivo `harness/TASK_TEMPLATE.md`. Os tipos suportados são:

- `somente-documentacao`: Permite alterações apenas em arquivos `.md`, `README.md` e pastas de documentação.
- `somente-testes`: Permite alterações apenas em pastas de teste (`app/src/test/*`, `app/src/androidTest/*`).
- `refatoracao`: Permite alterações em arquivos de código existente, mas bloqueia arquivos críticos.
- `feature`: Permite novos arquivos e alterações em lógica de negócio.
- `bugfix`: Permite correções em lógica existente.
- `infra`: Permite alterações em scripts de build, CI/CD, Firebase e configurações globais.

## Validação de Escopo

Antes de submeter qualquer mudança, o agente deve executar o script de validação:

```bash
python3 harness/check_scope.py
```

### Exemplo de Bloqueio

Se você declarar uma tarefa como `somente-documentacao` mas tentar alterar um arquivo Kotlin:

```bash
python3 harness/check_scope.py --type somente-documentacao --files "app/src/main/java/com/miaclean/app/MainActivity.kt"
```

**Saída:**
```
--- Relatório de Validação de Escopo ---
Tipo de Tarefa: somente-documentacao
Arquivos Alterados: 1
Resultado: BLOQUEADO
Os seguintes arquivos estão fora do escopo permitido para este tipo de tarefa:
 [!] app/src/main/java/com/miaclean/app/MainActivity.kt
```

## Overrides (Exceções)

Se uma tarefa de um tipo específico precisar alterar um arquivo que normalmente seria bloqueado, você deve declarar este arquivo explicitamente na seção `Allowed Overrides` do `harness/TASK_TEMPLATE.md`.

Exemplo:
```markdown
## Scope Declaration
- **Allowed Overrides**: [app/src/main/java/com/miaclean/app/data/entitlement/EntitlementRepository.kt]
```

## Relatório Final

Toda tarefa deve ser concluída com a geração de um relatório baseado no `harness/FINAL_REPORT_TEMPLATE.md`. Este relatório deve ser incluído na resposta final do agente.
