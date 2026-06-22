# AGENTS.md - IA Agent Instructions

Welcome, Agent. This file contains core directives for working in this repository.

# BPT Agent Economy Mode

Todos os agentes devem operar em modo econômico por padrão. Veja também `docs/prompts/BPT_AGENT_ECONOMY_MODE.md`.

## Regras globais

- Leia pouco.
- Altere pouco.
- Valide só o necessário.
- Não audite o repositório inteiro.
- Não carregue skills fora do escopo.
- Não leia `docs/`, `audit/`, `.github/`, `.jules/`, `.vs/`, `.next/` ou `node_modules/` salvo pedido explícito.
- Não abra arquivos grandes sem busca pontual prévia.
- Não abra mais de 5 arquivos sem justificar.
- Não cole logs completos.
- Não repita contexto já conhecido.
- Se encontrar problema fora do escopo, apenas relate no final.
- Responda sempre em formato curto:
  - Veredito
  - Arquivos alterados
  - Validação
  - Risco principal
  - Próximo passo

## Perfis de Agente

### BPT Bugfix Cirúrgico
Uso: erros claros de TypeScript, imports quebrados, correções pontuais.
- Pode ler: arquivo afetado, imports, tipos, teste específico.
- Pode alterar: máx 3 arquivos.
- Validação: `yarn typecheck` ou teste específico.

### BPT Auditoria Leve
Uso: descobrir o que existe e lacunas.
- Pode ler: máx 5 arquivos, 200 linhas/arq.
- Proibido: alterar código, rodar testes, auditoria ampla.

### BPT PR Gate
Uso: validação final antes de PR.
- Comandos: `git status -sb`, `git diff`, `yarn typecheck`.
- Proibido: alterar código, refatorar, sugerir merge direto.

### BPT Docs/Product Map
Uso: documentação estratégica e registros.
- Pode alterar: `docs/`.
- Proibido: alterar código, tocar em `src/`.

## Standard Commands & Rules
- **Multi-module**: `:app` (Android) and `:shared` (KMP).
- **KMP Boundaries**: No `android.*` in `shared/src/commonMain`.
- **Localization**: `ScanErrorCode` to `R.string` mapping only in `:app`.
- **Standard Commands**: `./gradlew assembleDebug`, `./gradlew :app:lintDebug`, `./gradlew :app:testDebugUnitTest`, `./gradlew :shared:test`.

## Final Response Format
Responda sempre em formato curto (conforme Regras globais).
