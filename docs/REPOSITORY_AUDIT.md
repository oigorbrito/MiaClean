# Repository Audit - MiaClean

## Stack Overview
- **Language**: Kotlin 2.0.20 (K2)
- **Build System**: Gradle 8.9, Kotlin DSL, Version Catalog (`libs.versions.toml`)
- **UI**: Jetpack Compose, Material 3, Glance Widgets
- **DI**: Dagger Hilt
- **Storage**: Room (Database), DataStore (Preferences)
- **Background**: WorkManager
- **ML/Vision**: MediaPipe (Image Embedder, Face Detector), ML Kit (OCR), Perceptual Hash (pHash)
- **Billing**: Google Play Billing
- **Permissions**: Accompanist Permissions, MediaStore, SAF (Storage Access Framework)
- **Localization**: PT-BR, ES, EN (Default)

## Module Structure
- `:app`: Single module containing all layers.

## Feature Status Audit

### 1. Contextual Classifier
- **Status**: Partially Implemented / Core logic exists.
- **Location**: `com.miaclean.app.data.classify`
- **Details**: Contains `MediaClassifier`, `MemeDetector`, `SelfieDetector`, `MemeEvaluator`, `SelfieEvaluator`.

### 2. Billing / Freemium Tier
- **Status**: Implemented / Infrastructure exists.
- **Location**: `com.miaclean.app.data.billing`, `com.miaclean.app.data.entitlement`
- **Details**: `PlayBillingRepository`, `BillingState`, `EntitlementRepository`. Paywall UI exists in `com.miaclean.app.ui.results.PaywallDialog`.

### 3. Batch Delete Flow
- **Status**: Implemented / Infrastructure exists.
- **Location**: `com.miaclean.app.data.delete`
- **Details**: `MediaDeleter` implementation using `MediaStore.createDeleteRequest`.

### 4. Widgets
- **Status**: Implemented.
- **Location**: `com.miaclean.app.widget`
- **Details**: `DuplicatesWidget` using Glance.

### 5. Internationalization (i18n)
- **Status**: Implemented.
- **Location**: `app/src/main/res/values-pt-rBR`, `app/src/main/res/values-es`.

### 6. Scan Pipeline
- **Status**: Core functional.
- **Location**: `com.miaclean.app.data.scan`, `com.miaclean.app.data.hash`, `com.miaclean.app.data.ml`.
- **Flow**: MediaStore/SAF → Hashing (MD5/pHash) → ML Classification → Room → UI.

## Observations
- README currently lists several of these features as "Roadmap" or "Out of scope", which is incorrect given the current codebase.
- `local.properties` is required for local builds (SDK path).
- `.tflite` models for MediaPipe are expected in `assets/` but not committed.
# REPOSITORY AUDIT - MiaClean

Este documento apresenta um diagnóstico inicial do repositório MiaClean para fins de alinhamento de produtividade e preparação para automação/agentes de IA.

## 1. Stack Detectada

*   **Linguagem:** Kotlin 2.0.20 (K2)
*   **Sistema de Build:** Gradle 8.9 com Kotlin DSL e Version Catalog (`libs.versions.toml`)
*   **Interface (UI):** Jetpack Compose, Material 3, Glance (Widgets)
*   **Arquitetura & DI:** Dagger Hilt
*   **Persistência:** Room (Banco de dados), DataStore (Preferências)
*   **Processamento em Background:** WorkManager
*   **IA/ML Local:** MediaPipe (Image Embedder, Face Detector), ML Kit (Text Recognition)
*   **Hashing:** MD5 (exato), pHash (ru.avicorp:phashcalc - perceptual)
*   **Faturamento:** Google Play Billing 7.1.1
*   **Permissões:** Accompanist Permissions (Scoped Storage / Media Store)

## 2. Estrutura Atual

O projeto utiliza um módulo único (`:app`) com a seguinte organização de pacotes:

```text
com.miaclean.app
├── di/                     # Módulos Dagger Hilt
├── domain/                 # Modelos de domínio (Models.kt)
├── data/                   # Camada de dados (Repositórios e fontes)
│   ├── db/                 # Room DAO, Entity e Database
│   ├── scan/               # Escaneamento via MediaStore e SAF (WhatsApp)
│   ├── hash/               # MD5 e pHash implementations
│   ├── ml/                 # MediaPipe wrappers
│   ├── classify/           # Classificadores (Memes, Selfies)
│   ├── billing/            # Integração com Play Store
│   ├── entitlement/        # Controle de acesso Pro/Free
│   └── delete/             # Lógica de deleção de mídia
├── ui/                     # Interface Compose
│   ├── onboarding/
│   ├── scan/
│   ├── results/
│   ├── settings/
│   └── theme/
├── work/                   # WorkManager (ScanWorker)
└── widget/                 # Glance AppWidgets
```

## 3. Scripts Disponíveis

*   `./gradlew assembleDebug`: Gera o APK de debug.
*   `./gradlew installDebug`: Instala no dispositivo conectado.
*   `./gradlew :app:lintDebug`: Executa análise estática de código.
*   `./gradlew :app:testDebugUnitTest`: Executa os testes unitários.

## 4. Problemas de Identidade / Contexto

1.  **Inconsistência com o README:** O README afirma que o "Contextual classifier", "Freemium tier" e "Batch delete flow" estão fora de escopo (Roadmap). No entanto, o código já contém implementações parciais ou completas nestas áreas (`data/classify`, `data/billing`, `data/delete`).
2.  **Widgets Presentes:** O README lista UI polish (thumbnails, widgets) no roadmap, mas a implementação do Glance já existe em `com.miaclean.app.widget`.
3.  **Modelos de ML:** O README menciona a necessidade manual do `image_embedder.tflite`, enquanto o `build.gradle.kts` possui uma task para baixar automaticamente o `face_detector.tflite`.
4.  **Localização:** O projeto já possui suporte a PT-BR e ES, embora o README foque na estrutura inicial.

## 5. Riscos para uso com Agentes de IA

1.  **Alucinação de Escopo:** Um agente pode tentar implementar o que já existe se ler apenas o README.
2.  **Configuração de Ambiente:** A ausência do `local.properties` (esperada, mas crítica) e a dependência de modelos `.tflite` externos podem causar falhas de build/runtime se não forem tratadas.
3.  **SKUs Hardcoded:** Identificadores de faturamento estão fixos no `build.gradle.kts`, o que pode dificultar testes de integração se não forem parametrizados.
4.  **Permissões SAF:** A lógica de fallback do WhatsApp via SAF (Storage Access Framework) é complexa e difícil de testar em ambientes CI sem emuladores configurados.

## 6. Próximos Passos Recomendados

1.  **Sincronizar README:** Atualizar o documento para refletir o estado real do projeto e o que realmente falta.
2.  **Implementar Harness de Testes:** Criar um conjunto de mídias de teste (imagens/vídeos dummy) para validar o pipeline de hashing e classificação sem depender do filesystem do usuário.
3.  **Habilitar Contexto para Agentes:** Criar um `AGENTS.md` com diretrizes claras sobre a arquitetura e padrões de codificação adotados.
4.  **Documentar Pipeline de ML:** Esclarecer como novos modelos de classificação devem ser integrados.

## 7. Lista de Arquivos para Ajuste / Criação

*   `README.md`: Atualizar seção de Stack e Roadmap. (Ajustar)
*   `AGENTS.md`: Criar diretrizes para desenvolvimento assistido por IA. (Novo)
*   `docs/TEST_PLAN.md`: Definir estratégia de testes para o pipeline de mídia. (Novo)
*   `app/src/test/java/...`: Expandir cobertura de testes para os novos módulos de classificação e billing. (Ajustar)
