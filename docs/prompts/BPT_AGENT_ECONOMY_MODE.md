# BPT Agent Economy Mode

Todos os agentes devem operar em modo econômico por padrão.

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

## BPT Bugfix Cirúrgico

Uso: erros claros de TypeScript, imports quebrados, falhas pequenas de build, correções pontuais.

Pode ler:
- arquivo diretamente afetado;
- imports diretos;
- tipos usados pelo arquivo;
- teste específico relacionado, se existir;
- schema Prisma apenas se o erro envolver banco/modelo.

Pode alterar:
- no máximo 3 arquivos, salvo justificativa explícita.

Validação:
- preferir `yarn typecheck`;
- rodar teste específico somente se existir e estiver ligado ao arquivo alterado.

Proibido:
- criar feature nova;
- refatorar UI sem pedido explícito;
- alterar schema Prisma sem autorização explícita;
- criar documentação;
- fazer roadmap.

## BPT Auditoria Leve

Uso: descobrir o que existe, onde está implementado e qual lacuna há.

Pode ler:
- no máximo 5 arquivos;
- até 200 linhas por arquivo;
- somente arquivos diretamente relevantes.

Proibido:
- alterar código;
- criar arquivos;
- rodar testes;
- fazer auditoria ampla;
- abrir repo inteiro.

Saída:
| Capacidade | Existe? | Evidência | Lacuna |
|---|---:|---|---|

## BPT PR Gate

Uso: validação final antes de Pull Request.

Comandos permitidos:
- `git status -sb`
- `git diff --name-only origin/main...HEAD`
- `git diff --stat origin/main...HEAD`
- `yarn typecheck`
- testes específicos conhecidos

Proibido:
- alterar código;
- criar arquivos;
- refatorar;
- abrir PR sem pedido explícito;
- sugerir merge direto;
- colar logs completos.

Saída:
- Veredito
- Diff final
- Validações executadas
- Arquivos fora de escopo, se houver
- Risco principal
- Próximo passo

## BPT Docs/Product Map

Uso: documentação estratégica, Product Map, auditorias documentais e registros de decisão.

Pode alterar apenas:
- `docs/`
- `docs/audits/`
- `docs/product/`
- arquivos markdown explicitamente solicitados.

Proibido:
- alterar código;
- alterar schema Prisma;
- criar migrations;
- rodar testes;
- refatorar UI;
- tocar em `src/` salvo auditoria de leitura explicitamente solicitada.

Saída:
- Veredito
- Arquivo criado/alterado
- O que foi documentado
- Risco principal
- Próximo passo
