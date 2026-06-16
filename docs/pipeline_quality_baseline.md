# Relatório de Qualidade do Pipeline de Detecção de Duplicados

## Configuração Atual
- **pHash Distance Threshold:** 5
- **Semantic Similarity Threshold:** 0.92

## Métricas de Performance
- **Precision:** 0.4583
- **Recall:** 0.9167
- **F1 Score:** 0.6111
- **False Positives (FP):** 13
- **False Negatives (FN):** 1

## Matriz de Confusão (Pares)
| | Positivo (GT) | Negativo (GT) |
|---|---|---|
| **Positivo (Det)** | TP: 11 | FP: 13 |
| **Negativo (Det)** | FN: 1 | TN: 300 |

## Análise de Erros
### Falsos Positivos (FP)
- **burst_10_a <-> multi_resize_12_a**: Detectado via `PERCEPTUAL_PHASH`. Evidência: pHash distance: 4
- **burst_10_a <-> multi_resize_12_b**: Detectado via `PERCEPTUAL_PHASH`. Evidência: pHash distance: 4
- **burst_10_b <-> multi_resize_12_a**: Detectado via `PERCEPTUAL_PHASH`. Evidência: pHash distance: 5
- **burst_10_b <-> multi_resize_12_b**: Detectado via `PERCEPTUAL_PHASH`. Evidência: pHash distance: 5
- **cropped_11_a <-> heic_8_a**: Detectado via `PERCEPTUAL_PHASH`. Evidência: pHash distance: 4
- **cropped_11_a <-> heic_8_b**: Detectado via `PERCEPTUAL_PHASH`. Evidência: pHash distance: 4
- **cropped_11_a <-> live_photo_9_a**: Detectado via `PERCEPTUAL_PHASH`. Evidência: pHash distance: 4
- **cropped_11_a <-> live_photo_9_b**: Detectado via `PERCEPTUAL_PHASH`. Evidência: pHash distance: 5
- **heic_8_a <-> live_photo_9_a**: Detectado via `PERCEPTUAL_PHASH`. Evidência: pHash distance: 4
- **heic_8_a <-> live_photo_9_b**: Detectado via `PERCEPTUAL_PHASH`. Evidência: pHash distance: 5
- **heic_8_b <-> live_photo_9_a**: Detectado via `PERCEPTUAL_PHASH`. Evidência: pHash distance: 4
- **heic_8_b <-> live_photo_9_b**: Detectado via `PERCEPTUAL_PHASH`. Evidência: pHash distance: 5
- **similar_7_a <-> similar_7_b**: Detectado via `SEMANTIC_EMBED`. Evidência: Semantic similarity: 0.9762

### Falsos Negativos (FN)
- **cropped_11_a <-> cropped_11_b**: Falha na detecção. (pHash dist: 999, Semantic sim: 0.9999)

## Recomendações de Ajuste e Baseline
### Comparativo
- **Baseline Anterior:** Precision: 0.8571, Recall: 1.0000
- **Atual:** Precision: 0.4583, Recall: 0.9167

### Análise
1. A queda na Precision se deve à inclusão de casos complexos (Bursts, Live Photos) que possuem pHashs muito próximos (distância <= 5), causando agrupamento indevido.
2. O Recall diminuiu ligeiramente devido ao item `cropped_11_b`, cujo pHash diverge significativamente do original, e embora a similaridade semântica seja alta (0.9999), ele pode estar sendo "roubado" por outro grupo ou o fluxo de remoção de itens já agrupados está afetando a detecção.
3. **Recomendação:** Aumentar o rigor do pHash para distância <= 3 para reduzir FPs em Bursts, ou integrar metadados (como timestamp) para distinguir fotos de bursts.

## Análise de Regressões e Testes
### ScanRepositoryHardeningTest
Foram detectadas 3 falhas funcionais no `ScanRepositoryHardeningTest`:
1. `permission revoked during scan emits retryable failure`: O pipeline atual não captura `SecurityException` durante a enumeração de arquivos, resultando em crash do worker em vez de um estado `ScanProgress.Failed` amigável.
2. `inaccessible media emits retryable failure`: O pipeline não captura `FileNotFoundException` durante o cálculo de MD5, o que interrompe o scan prematuramente.
3. `unexpected pipeline exception emits unexpected failure`: Exceções genéricas durante a persistência no banco de dados (upsert) não são tratadas, causando falha catastrófica do fluxo.

**Recomendação:** Implementar blocos `try-catch` específicos no `ScanRepository.kt` para mapear essas exceções para `ScanProgress.Failed` com os recursos de string apropriados, conforme estabelecido no contrato de erro do projeto.

### Relatório de Correções de Compilação
Para permitir a execução da suíte de testes sem alterar a lógica de produção, foram realizadas as seguintes intervenções:
- **Recursos de String:** Adicionados `scan_error_permission_revoked`, `scan_error_media_unavailable` e `scan_error_unexpected` em `strings.xml` (en, pt-rBR, es).
- **Domain Model:** O contrato de `ScanProgress.Failed` foi atualizado de `String` para `reasonResId: Int` para alinhar com a implementação esperada pelos testes e o padrão de internacionalização do projeto.
- **UI:** `ScanScreen.kt` foi ajustado para consumir o `reasonResId` usando `stringResource()`.
- **Testes:** `ScanWorkerTest.kt` foi corrigido para usar referências de classe modernas (`::class.java`) e incluiu o helper `scanFailureResult` que estava ausente. `ScanRepositoryHardeningTest.kt` recebeu o mock estático de `Uri` necessário para execução fora de ambiente Robolectric.
