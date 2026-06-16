# Relatório de Qualidade do Pipeline de Detecção de Duplicados

## Configuração Atual
- **pHash Distance Threshold:** 5
- **Semantic Similarity Threshold:** 0.92

## Métricas de Performance
- **Precision:** 0.8571
- **Recall:** 1.0000
- **F1 Score:** 0.9231
- **False Positives (FP):** 1
- **False Negatives (FN):** 0

## Detalhamento de Grupos
### Grupos Corretos (TP)
6 pares detectados corretamente.

### Falsos Positivos (FP)
1 pares detectados incorretamente.
- similar_7_a <-> similar_7_b (Deveriam ser distintos)

### Falsos Negativos (FN)
0 pares não detectados.

## Recomendações de Ajuste
1. O threshold de pHash (5) é conservador. Imagens comprimidas são bem capturadas.
2. O impacto do embedding é crucial para selfies e memes onde o pHash falha devido a variações sutis no fundo ou texto.
3. Se o Recall estiver baixo em selfies, considere reduzir o SEMANTIC_SIMILARITY_THRESHOLD para 0.90.
4. Se falsos positivos surgirem em imagens visualmente parecidas mas distintas (ex: fotos diferentes do mesmo objeto), considere aumentar o SEMANTIC_SIMILARITY_THRESHOLD para 0.94.

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
