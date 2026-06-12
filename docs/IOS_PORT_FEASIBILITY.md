# Estudo de Viabilidade: MiaClean para iOS

Este documento avalia o esforço e a estratégia para portar o MiaClean para a plataforma iOS, mantendo a paridade de funcionalidades com a versão Android.

## 1. Potencial de Código Compartilhado (KMP)

A arquitetura atual do MiaClean (Android) separa bem a extração de sinais (IO/ML) da lógica de avaliação (Lógica Pura). Isso favorece o uso de **Kotlin Multiplatform (KMP)**.

| Componente | Potencial | Observações |
| :--- | :--- | :--- |
| **Modelos de Domínio** | **100%** | `MediaItem`, `DuplicateGroup` e enums podem ser compartilhados integralmente. |
| **Evaluators** | **100%** | `MemeEvaluator`, `SelfieEvaluator` e `EntitlementEvaluator` são objetos puros em Kotlin. |
| **Agrupamento** | **95%** | Lógica de BK-Tree e DisjointSet é puramente algorítmica. |
| **Heurísticas** | **70%** | `MediaClassifier` requer ajustes nos caminhos de arquivo (iOS sandbox vs Android paths). |
| **Hashing (MD5)** | **100%** | Algoritmo padrão; requer abstração de `InputStream` no KMP. |
| **Observabilidade** | **100%** | `ClassifierEventLogger` e `ClassifierErrorMapper` podem ser movidos para um módulo `shared`. |

## 2. Componentes Específicos da Plataforma (Reescrita)

O esforço principal de desenvolvimento no iOS será focado no acesso a dados e interface com o sistema:

*   **Photos Framework**: Substituir o `MediaStoreScanner`. O iOS possui gerenciamento de permissões granular (Acesso Total vs Limitado).
*   **Deleção Consentida**: No iOS, a deleção exige confirmação do sistema para cada lote (`PHAssetChangeRequest`). Não há equivalente direto ao "Trash" programático do Android 11+.
*   **Background Tasks**: Migrar de `WorkManager` para `BGTaskScheduler`. O iOS é muito mais restritivo com janelas de execução em segundo plano.
*   **ML Integration**: Reutilizar modelos `.tflite` via MediaPipe iOS SDK ou converter para **CoreML** para máxima performance.
*   **Billing**: Substituir `Play Billing` por `StoreKit 2`.
*   **UI**: Migração de Jetpack Compose para **SwiftUI**. O padrão UDF (Unidirectional Data Flow) atual facilita essa ponte.

## 3. Riscos e Desafios

1.  **Acesso ao WhatsApp**: No iOS, o sandbox impede o acesso direto às pastas do WhatsApp. A detecção só funciona para mídias salvas no Rolo da Câmera (ajuste padrão do WhatsApp).
2.  **Performance de Background**: Processar pHash em milhares de fotos em background pode levar à suspensão do app pelo iOS por consumo excessivo de recursos.
3.  **Deleção em Massa**: O prompt do sistema para deletar itens pode ser uma fricção maior no iOS se houver muitas duplicadas.

## 4. Conclusão e Recomendações

**Estimativa de Esforço: M (Médio)**
O reaproveitamento da lógica algorítmica e de domínio economiza cerca de 30-40% do tempo de desenvolvimento de uma versão do zero.

**Arquitetura Recomendada: Kotlin Multiplatform (KMP)**
- **Shared Module**: Domínio, Evaluators, BK-Tree, DisjointSet, Business Rules.
- **Native iOS**: SwiftUI, Photos.framework, StoreKit, MediaPipe Bridge.

**Próximo Passo Sugerido (Protótipo):**
Implementar um **Scanner de Metadados** em Swift que extrai atributos básicos (`id`, `size`, `creationDate`) e os envia para um `ScanRepository` compartilhado (KMP) para validar o agrupamento por similaridade básica no iOS.
