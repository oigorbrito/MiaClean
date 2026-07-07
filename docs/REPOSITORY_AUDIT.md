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

1.  **[FEITO] Sincronizar README:** Atualizado para refletir o estado real do projeto, as features de IA, Billing, Widgets, i18n e a automação de download dos modelos de ML.
2.  **[FEITO] Implementar Harness de Testes:** Criado `ClassificationPipelineIntegrationTest.kt` que valida o pipeline completo (hashing e classificação por heuristics + ML fallback) mockando o `ContentResolver` e simulando recursos falsos, sem depender de arquivos físicos no emulador/dispositivo.
3.  **Habilitar Contexto para Agentes:** Criar um `AGENTS.md` com diretrizes claras sobre a arquitetura e padrões de codificação adotados.
4.  **Documentar Pipeline de ML:** Esclarecer como novos modelos de classificação devem ser integrados.

## 7. Lista de Arquivos para Ajuste / Criação

*   `README.md`: Atualizar seção de Stack e Roadmap. (Ajustar)
*   `AGENTS.md`: Criar diretrizes para desenvolvimento assistido por IA. (Novo)
*   `docs/TEST_PLAN.md`: Definir estratégia de testes para o pipeline de mídia. (Novo)
*   `app/src/test/java/...`: Expandir cobertura de testes para os novos módulos de classificação e billing. (Ajustar)
