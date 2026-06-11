# Harness de Escopo (check_scope.py)

Este documento descreve como utilizar o script `check_scope.py` para garantir que as alterações no repositório estejam dentro do escopo definido.

## Objetivo
Tornar a documentação consistente com o uso local e com o uso em CI.

## Uso Local

Para validar as alterações localmente antes de enviar um PR:

```bash
# Validar usando o tipo da tarefa
python3 harness/check_scope.py --type somente-documentacao

# Validar usando o escopo da tarefa
python3 harness/check_scope.py --scope somente-documentacao
```

## Uso em CI

No ambiente de Integração Contínua (CI), o script é utilizado para comparar as alterações entre a base e o head do PR:

```bash
python3 harness/check_scope.py --scope "$TASK_SCOPE" --base "$BASE_SHA" --head "$HEAD_SHA"
```

## Regras de Escopo (Padrões Recursivos)

O script utiliza padrões recursivos para validar os diretórios:

- `docs/**`: Toda a documentação.
- `app/src/main/**`: Código fonte principal (Kotlin e recursos).
- `app/src/test/**`: Testes unitários.
- `app/src/androidTest/**`: Testes de instrumentação.
- `functions/**`: Cloud Functions do Firebase.
- `.github/**`: Workflows e configurações do GitHub.
- `harness/**`: Scripts e templates do harness de produtividade.
- `gradle/**`: Configurações do Gradle Wrapper e libs.

## Validação de Renomeações e Cópias

Para operações de renomeação (`R`) e cópia (`C`), o script valida tanto o **caminho antigo** quanto o **caminho novo**. Ambos devem estar dentro do escopo permitido para que a operação seja aprovada.

## Gerenciamento de Overrides

O sistema de proteção distingue entre arquivos comuns e arquivos críticos.

1.  **Arquivos Comuns**: Podem ser liberados via flag `--allowed` no CLI ou na tarefa.
2.  **Arquivos Críticos**:
    - Alterações em arquivos críticos (ex: logic de entitlement, faturamento, configurações Firebase) são bloqueadas por padrão.
    - **Não** são liberados apenas com `--allowed`.
    - Exigem que a tarefa seja do tipo `infra`.
    - Exigem justificativa explícita no template da tarefa.
    - Se necessário forçar a liberação, deve-se usar a flag `--allow-critical` explicitamente no comando.

## Exemplos de Saída

### PASSOU
Quando todas as alterações estão dentro do escopo:
```text
Verificando 3 arquivos alterados...
[OK] docs/HARNESS_USAGE.md (M)
[OK] docs/ROADMAP.md (M)
[OK] harness/check_scope.py (M)

Resultado: PASSOU
```

### BLOQUEADO por deleção crítica
Quando tenta-se deletar um arquivo protegido sem permissão de `infra`:
```text
Verificando 1 arquivo alterado...
[ERRO] app/src/main/java/com/miaclean/app/data/entitlement/EntitlementRepository.kt (D)
Causa: Deleção de arquivo crítico em tarefa não-infra.

Resultado: BLOQUEADO
```

## Integração com PR

Ao abrir um Pull Request, certifique-se de que o corpo do PR contém o escopo da tarefa para que o CI funcione corretamente:

```text
...
Task-Scope: somente-documentacao
...
```
