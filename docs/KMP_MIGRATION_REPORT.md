# Relatório de Migração Kotlin Multiplatform (KMP) - MiaClean

## 1. Auditoria e Classificação da Codebase

A codebase atual foi auditada para identificar a viabilidade de compartilhamento de código entre Android e iOS.

### Resumo da Classificação:
*   **Total de arquivos Kotlin (.kt):** 70
*   **Pronto para Shared/KMP:** 18 (25.7%) - *Já consolidados no módulo `:shared`*
*   **Depende de Android SDK:** 37 (52.8%)
*   **Depende de ML Kit / MediaPipe:** 3 (4.3%)
*   **Depende de MediaStore:** 4 (5.7%)
*   **Depende de Compose Android:** 13 (18.5%)
*   **Depende de WorkManager:** 4 (5.7%)
*   **Depende de Room:** 4 (5.7%)
*   **Depende de APIs de Arquivos (java.io/Uri):** 14 (20%)

*Nota: Alguns arquivos possuem múltiplas dependências.*

### Métricas de Compartilhamento:
*   **% Atual Compartilhável (Logic/Domain):** ~30%
*   **% Android-específico (UI/Infrastructure):** ~70%

---

## 2. Principais Bloqueadores para iOS

Para atingir a paridade de funcionalidades no iOS, os seguintes componentes Android-específicos precisam de abstrações ou substituições:

| Componente | Dependência Android | Estratégia de Migração iOS |
| :--- | :--- | :--- |
| **Persistence** | Room (SQLite) | Room KMP ou SQLDelight |
| **Background Jobs** | WorkManager | BackgroundTasks Framework (iOS native) |
| **Media Access** | MediaStore | PhotoKit (PHAsset) |
| **Image Processing** | Android Bitmap / EXIF | CoreGraphics / ImageIO (iOS native) |
| **ML/Embeddings** | ML Kit / MediaPipe Android | CoreML / MediaPipe iOS |
| **Dependency Injection** | Hilt | Koin ou Native DI Wrapper |

---

## 3. Plano de Priorização da Migração

A migração deve seguir a ordem de menor dependência para maior complexidade:

### Fase 1: Estabilização do Núcleo (Shared Models & Logic) - *CONCLUÍDO*
*   **Models:** `MediaItem`, `DuplicateGroup`, `ScanProgress`.
*   **Classificadores:** `MediaClassifier`, `MemeEvaluator`, `SelfieEvaluator`.
*   **Risco:** Baixo.
*   **Esforço:** Mínimo.

### Fase 2: Abstração de Infraestrutura (Interfaces)
*   **Objetivo:** Criar interfaces `expect/actual` para Hashing e Scanning.
*   **Módulos:** `Md5Hasher`, `PerceptualHasher`, `MediaStoreScanner`.
*   **Esforço:** Médio.
*   **Riscos:** Diferenças de performance entre implementações de hashing.

### Fase 3: Pipeline de Scan (Shared Repository)
*   **Objetivo:** Mover `ScanRepository` para `commonMain`.
*   **Estratégia:** Injeção de interfaces da Fase 2.
*   **Esforço:** Alto.
*   **Riscos:** Gerenciamento de concorrência (Coroutines) em Swift/Kotlin.

### Fase 4: Inteligência Artificial (pHash & Embeddings)
*   **Objetivo:** Abstrair `ImageEmbedder` e bibliotecas nativas de pHash.
*   **Estratégia:** `expect/actual` para chamadas nativas (MediaPipe iOS vs Android).
*   **Esforço:** Muito Alto.
*   **Riscos:** Paridade de modelos TFLite entre plataformas.

### Fase 5: Persistência e Background (Shared Persistence)
*   **Objetivo:** Migrar Room para versão KMP e abstrair agendamento de scan.
*   **Esforço:** Alto.
*   **Riscos:** Migração de dados de usuários existentes no Android.

---

## 4. Arquitetura Final Proposta

A estrutura do projeto deve evoluir para:

```text
/shared
  ├── commonMain/      # Domínio, Repositórios, Lógica de Scan, Interfaces
  ├── androidMain/     # Impl. Android: MediaStore, Room, ML Kit
  └── iosMain/         # Impl. iOS: PhotoKit, CoreData/SQLDelight, CoreML
/app
  └── androidApp/      # UI Compose, Glue code Hilt
/iosApp
  └── iosApp/          # UI SwiftUI (Consumindo shared)
```

---

## 5. Próximos Passos Imediatos

1. Implementar interfaces para `Md5Hasher` e `PerceptualHasher` no módulo `:shared`.
2. Criar `MediaScanner` interface para abstrair `MediaStore`.
3. Preparar `ScanRepository` para migração removendo dependências diretas de `Uri` e `Context`.
