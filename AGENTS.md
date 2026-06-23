# AGENTS.md - IA Agent Instructions

Welcome, Agent. This file contains core directives for working in this repository.

# BPT Agent Economy Mode (Standard)

Todos os agentes devem operar em modo econômico por padrão.

## Regras globais

- **Leia pouco**: Foque nos arquivos diretamente relacionados à tarefa.
- **Altere pouco**: Limite as mudanças ao estritamente necessário.
- **Valide só o necessário**: Use testes específicos em vez de suítes completas quando possível.
- **Não audite o repositório inteiro**: Evite listagens recursivas profundas sem necessidade.
- **Restrição de pastas**: Não leia `docs/`, `audit/`, `.github/`, `.jules/`, `.vs/`, `.next/` ou `node_modules/` salvo pedido explícito.
- **Limite de Arquivos**: Não abra mais de 5 arquivos sem justificar.
- **Formato de Resposta**: Responda sempre em formato curto:
  - Veredito
  - Arquivos alterados
  - Validação
  - Risco principal
  - Próximo passo

## Perfis de Agente

### BPT Bugfix Cirúrgico
Uso: correções pontuais, erros de lógica, imports quebrados.
- **Leitura**: arquivo afetado, imports diretos, tipos e teste específico.
- **Escopo**: máximo 3 arquivos alterados.
- **Validação**: `./gradlew :app:testDebugUnitTest` (Android) ou `npm run build` (Functions).

### BPT Auditoria Leve
Uso: descoberta de código e análise de lacunas.
- **Leitura**: máx 5 arquivos, 200 linhas/arq.
- **Proibido**: alterar código, rodar testes pesados.

### BPT PR Gate
Uso: validação final antes de submissão.
- **Comandos**: `git status -sb`, `git diff`, validação de build local.
- **Proibido**: refatorar ou sugerir mudanças fora do escopo do PR.

### BPT Docs/Product Map
Uso: documentação e registros estratégicos.
- **Escopo**: altera apenas `docs/`. Proibido tocar em `src/` ou código Android/KMP.

# Standard Commands & Rules

## KMP & Architecture
- **Multi-module**: `:app` (Android) e `:shared` (Kotlin Multiplatform).
- **KMP Boundaries**: Proibido usar `android.*` ou dependências específicas de Android em `shared/src/commonMain`.
- **Localization**: O mapeamento de `ScanErrorCode` para `R.string` deve ocorrer exclusivamente no módulo `:app`.

## Comandos Recomendados
- **Build**: `./gradlew assembleDebug`
- **Lint**: `./gradlew :app:lintDebug`
- **Tests (Android)**: `./gradlew :app:testDebugUnitTest`
- **Tests (KMP)**: `./gradlew :shared:test`
- **Backend**: `cd functions && npm test`

