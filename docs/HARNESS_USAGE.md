# AI Productivity Harness - MiaClean

O MiaClean utiliza um sistema de proteção de escopo (`harness/check_scope.py`) para garantir que as tarefas dos agentes de IA sejam seguras e sigam as diretrizes de arquitetura.

## Como Funciona

A validação de escopo analisa o estado real do repositório usando o Git e compara os arquivos alterados com os padrões permitidos para cada tipo de tarefa.

### Status de Arquivos Suportados

O validador reconhece os seguintes status do Git:
- **A** (Added): Arquivos novos.
- **M** (Modified): Arquivos existentes que foram alterados.
- **D** (Deleted): Arquivos que foram removidos.
- **R** (Renamed): Arquivos que foram movidos ou renomeados.
- **C** (Copied): Arquivos que foram copiados.

## Tipos de Tarefa e Escopos

| Tipo de Tarefa | Descrição | Padrões Permitidos |
| :--- | :--- | :--- |
| `somente-documentacao` | Apenas mudanças em docs e markdown. | `README.md`, `docs/*`, `*.md`, `AGENTS.md`, `PROMPTS.md`, `RULES.md` |
| `somente-testes` | Apenas testes unitários ou instrumentados. | `app/src/test/*`, `app/src/androidTest/*` |
| `refatoracao` | Mudanças no código fonte sem novos testes. | `app/src/main/*` |
| `feature` / `bugfix` | Desenvolvimento de funcionalidades ou correção de bugs. | `app/src/main/*`, `app/src/test/*` |
| `infra` | Mudanças em build, CI/CD, Firebase e Harness. | *Qualquer arquivo* (exceto regras de negócio críticas sem override) |

## Proteções Críticas e Risco de Deleção

Para prevenir danos acidentais ao sistema, certas áreas são protegidas contra modificações e deleções.

### 1. Proteção contra Modificação
Arquivos em áreas sensíveis são bloqueados em tarefas que não sejam de `infra`, mesmo que correspondam a padrões genéricos (como `app/src/main/*`):
- `app/src/main/java/com/miaclean/app/data/entitlement/*` (Lógica de Premium)
- `functions/*` (Backend Firebase)
- `firebase.json`, `.firebaserc`, `firestore.rules`, `firestore.indexes.json` (Configurações Firebase)

### 2. Validação de Deleção (Risco Crítico)
A deleção de arquivos em áreas sensíveis é tratada como um **Risco Crítico**. Qualquer deleção detectada nos seguintes caminhos será BLOQUEADA, a menos que a tarefa seja declarada como `infra`:
- Backend Firebase (`functions/*`)
- Código fonte da aplicação (`app/src/main/*`)
- Lógica de monetização (`app/src/main/java/com/miaclean/app/data/entitlement/*`)
- Arquivos de build e configuração (`*.gradle*`, `gradle/*`, `firebase.json`, etc.)

## Comandos Úteis

### Validar Escopo Manualmente
```bash
python3 harness/check_scope.py --type <tipo-da-tarefa>
```

### Validar com Overrides
Se precisar alterar um arquivo protegido, declare-o no `TASK_TEMPLATE.md` ou use o parâmetro `--allowed`:
```bash
python3 harness/check_scope.py --type feature --allowed "firebase.json"
```

## Exemplo de Bloqueio por Deleção
```text
--- Relatório de Validação de Escopo ---
Tipo de Tarefa: bugfix
Arquivos Alterados: 1
Resultado: BLOQUEADO (RISCO CRÍTICO)
Os seguintes arquivos estão fora do escopo permitido ou representam risco:
 [!] firebase.json (DELEÇÃO CRÍTICA)

AVISO: Deleções de arquivos sensíveis foram detectadas. Isso requer uma tarefa do tipo 'infra'.
```
