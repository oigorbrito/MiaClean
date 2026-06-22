# Auditoria de PRs Abertas - MiaClean

Esta auditoria identifica PRs duplicadas, sobrepostas ou substituídas, classificando-as por status e prioridade (KMP > Security > Performance > UX).

## 1. Tabela PR -> Status Recomendado

| PR / Branch | Status | Categoria | Justificativa Resumida |
| :--- | :--- | :--- | :--- |
| `feature/kmp-migration-phase-2-audit-...` | **Revisar agora** | KMP | Consolida a Fase 2 da migração e auditoria final. |
| `sentinel/fix-dos-verify-purchase-...` | **Consolidar** | Security | Traz limites de DoS, mas regrediu na validação do App Check. |
| `sentinel-security-hardening-...` | **Consolidar** | Security | Contém a lógica de validação de token removida na PR de DoS. |
| `bolt-scan-optimization-6883153...` | **Revisar agora** | Performance | Consolida otimizações de N+1 e batching de inserção. |
| `palette/enhance-media-thumbnail-ux-...` | **Revisar agora** | UX | Itaração final com haptics, animação e acessibilidade. |
| `infra-automatic-pr-risk-summary-...` | **Revisar agora** | Infra | Automação de CI para resumo de riscos em PRs. |
| `kmp-migration-phase-1-...` | **Superseded** | KMP | Substituída pela Phase 2. |
| `bolt-optimize-scan-pipeline-...` | **Superseded** | Performance | Substituída pela branch de otimização consolidada. |
| `palette-selection-ux-...` | **Superseded** | UX | Substituída pela `enhance-media-thumbnail-ux`. |
| `sentinel/add-purchase-limit-...` | **Superseded** | Security | Substituída pela branch de fix-dos. |
| `devin/*` e `codex/*` | **Fechar** | Legado | PRs antigas (Abril/2026) e defasadas após a migração. |

## 2. Justificativa Detalhada

### KMP Migration
A branch **Phase 2** (`7ce3679`) é a fonte da verdade atual para a migração KMP. Ela moveu o `ScanRepository` para o módulo compartilhado. A **Phase 1** está totalmente obsoleta e deve ser fechada.

### Security (Risco de Regressão)
Há uma sobreposição crítica entre `sentinel/fix-dos-verify-purchase` e `sentinel-security-hardening`. A primeira implementou `MAX_PURCHASES_PER_REQUEST = 10` (correto), mas removeu o parâmetro `verifyAppCheckToken` do handler, tornando a verificação do App Check ineficaz (apenas checa se o header existe). É necessário consolidar ambas para manter a segurança completa.

### Performance (Bolt)
A branch `bolt-scan-optimization` (`758a460`) é a evolução final das tentativas de otimizar o pipeline. Ela implementa o pre-fetching de IDs (N+1) e o batching (tamanho 50), tornando as PRs `bolt-optimize-scan-pipeline-*` redundantes.

### UX (Palette)
A PR `enhance-media-thumbnail-ux` (`31ad208`) refina a seleção com `graphicsLayer` (escala 0.92f) e feedback tátil, além de strings localizadas. As PRs anteriores de "selection-ux" são versões incompletas deste trabalho.

## 3. Ordem de Revisão Sugerida

1.  **KMP Phase 2 Migration**: Prioridade 1. Define a base estável do código compartilhado.
2.  **Security Consolidation**: Prioridade 2. Crucial para evitar vulnerabilidades no backend de compras.
3.  **Bolt Scan Optimization**: Prioridade 3. Ganho imediato de eficiência no processamento de mídia.
4.  **Palette UX Polish**: Prioridade 4. Melhora a percepção de qualidade do app.

## 4. PRs para Fechamento Imediato

As seguintes branches podem ser fechadas sem revisão, pois são legadas ou foram substituídas:
- Todas as branches `origin/devin/*` (Criadas em meados de Abril/2026).
- Todas as branches `origin/codex/*`.
- `origin/kmp-migration-phase-1-179097768399109217`.
- `origin/bolt-optimize-scan-pipeline-11369275399088813570` e similares.
- `origin/palette-selection-ux-16419589040731126805` e similares.
- `origin/sentinel/add-purchase-limit-7861305344407768744` e similares.
