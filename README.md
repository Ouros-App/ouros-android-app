# Ouros Android App

<!-- REPO-METADATA:START -->
<div align="center">

[![Repo Size](https://img.shields.io/github/repo-size/Ouros-App/ouros-android-app?style=flat-square&label=REPO%20SIZE)](https://github.com/Ouros-App/ouros-android-app)
[![Android SDK](https://img.shields.io/badge/Android%20SDK-33%20..%2036-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.1-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org/)
[![Material Design](https://img.shields.io/badge/Material-Design%203-795548?style=flat-square&logo=materialdesign&logoColor=white)](https://m3.material.io/)
[![License](https://img.shields.io/github/license/Ouros-App/ouros-android-app?style=flat-square&label=LICENSE)](LICENSE)

</div>
<!-- REPO-METADATA:END -->

Aplicativo móvel nativo Android do ecossistema **Ouros**, desenvolvido em **Kotlin**. Projetado especificamente para produtores rurais, granjeiros e cooperados no manejo avícola diário em campo, o app combina arquitetura **offline-first** resiliente, suporte adaptativo a tablets e smartphones, apontamentos zootécnicos guiados, telemetria em tempo real, assistente virtual inteligente Midas AI e gamificação por ligas de produtividade.

---

## 📑 Sumário

- [Visão Geral e Diferenciais](#-visão-geral-e-diferenciais)
- [Arquitetura e Padrões Técnicos](#-arquitetura-e-padrões-técnicos)
- [Módulos e Funcionalidades](#-módulos-e-funcionalidades)
- [Tecnologias e Dependências](#-tecnologias-e-dependências)
- [Estrutura do Projeto](#-estrutura-do-projeto)
- [Requisitos e Configuração de Ambiente](#-requisitos-e-configuração-de-ambiente)
- [Gerenciamento de Segredos (Infisical)](#-gerenciamento-de-segredos-infisical)
- [Compilação, Testes e Qualidade](#-compilação-testes-e-qualidade)
- [Integração no Ecossistema Ouros](#-integração-no-ecossistema-ouros)
- [Contribuidores e Licença](#-contribuidores-e-licença)

---

## 🌾 Visão Geral e Diferenciais

O **Ouros Android App** foi desenvolvido para enfrentar a realidade do campo, onde a conectividade de internet frequentemente é instável ou inexistente nos galpões avícolas:

- 📶 **Operação 100% Offline-First:** Coleta e registro contínuo de dados zootécnicos, pesagens e consumo com sincronização automática e transparente assim que a conexão de rede for restabelecida.
- 📱 **Interface Adaptativa (Mobile & Tablet):** Telas otimizadas com layouts dedicados para smartphones e tablets (`layout-sw600dp` e `layout-w600dp-land`), aproveitando melhor o espaço visual em dispositivos maiores no escritório da granja.
- 📐 **Design Moderno Material 3 & Edge-to-Edge:** Implementação com componentes Material You, navegação fluida e tratamento nativo de insets de sistema e teclado virtual (`WindowInsetsCompat.Type.ime()`), impedindo a sobreposição de formulários.
- 🤖 **Assistente Inteligente Midas AI:** Chatbot integrado com renderização rica de Markdown para suporte instantâneo a dúvidas de manejo, ambiência e nutrição avícola.
- 🏆 **Gamificação Zootécnica:** Acompanhamento dinâmico da posição do produtor em ligas de eficiência (Ferro, Cobre, Bronze, Prata e Ouro).

---

## 🏗️ Arquitetura e Padrões Técnicos

O projeto adota o padrão arquitetural **MVVM (Model-View-ViewModel)** orientado pelos princípios do **Android Jetpack** e **Clean Architecture**:

```text
┌────────────────────────────────────────────────────────┐
│                      UI Layer                          │
│     Activities, Fragments, Adapters, ViewBinding       │
│     Layouts responsivos (mobile + tablets sw600dp)     │
└──────────────────────────┬─────────────────────────────┘
                           │ Observa StateFlow / LiveData
┌──────────────────────────▼─────────────────────────────┐
│                   ViewModel Layer                      │
│      Coroutines, Dispatchers.Main / Dispatchers.IO      │
└──────────────────────────┬─────────────────────────────┘
                           │ Invoca Repositórios
┌──────────────────────────▼─────────────────────────────┐
│                  Repository Layer                      │
│     Coordenação entre dados locais e microsserviços    │
└───────────────┬────────────────────────┬───────────────┘
                │                        │
┌───────────────▼──────────────┐  ┌──────▼───────────────┐
│     Offline Local Store      │  │    Network (REST)    │
│  - OurosOfflineDatabase      │  │  - Retrofit 2 + Gson │
│  - OfflineStartupPolicy      │  │  - OkHttp 4          │
│  - OfflineSyncRegistry       │  │  - TokenAuthenticator│
│  - Pending Operations Queue  │  │  - Cloudinary API    │
└──────────────────────────────┘  └──────────────────────┘
```

### Principais Pilares Arquiteturais:
1. **ViewBinding & UI Moderna:** Ausência total de synthetics ou DataBinding legados, garantindo segurança de tipos em tempo de compilação.
2. **Camada Offline-First Nativa:**
   - `OurosOfflineDatabase`: Banco de dados SQLite nativo estruturado com tabelas de snapshots de cache (`cache_entries`), fila durável de operações pendentes (`pending_operations`) com contagem de tentativas e mapeamento de IDs locais para remotos (`id_mappings`).
   - `OfflineStartupPolicy`: Permite que produtores previamente autenticados iniciem e utilizem o aplicativo no campo sem conexão ativa, validando a integridade das credenciais salvas.
   - `OfflineSyncRegistry`: Monitora eventos de conectividade via `ConnectivityManager.NetworkCallback` e orquestra a sincronização em segundo plano das operações pendentes com locks e corrotinas.
   - `ProfileOfflineCoordinator`: Gerenciamento resiliente de dados cadastrais e fila de upload de fotos de perfil.
3. **Resiliência de Rede e Autenticação:**
   - `TokenAuthenticator` & `SecureRefreshTokenStore`: Interceptador OkHttp para renovação silenciosa e automática de tokens JWT expirados (Keycloak OAuth2).
4. **Mídia e Imagens:**
   - Integração com a API do **Cloudinary** para upload unsigned direto, corte e proporção de avatar com a biblioteca **uCrop** e carregamento eficiente com **Coil**.

---

## 📱 Módulos e Funcionalidades

### 1. 🚀 Onboarding e Boas-Vindas
- **Splash (`SplashActivity`):** Análise da política de sessão offline (`OfflineStartupPolicy`) e roteamento seguro conforme o estado de autenticação.
- **Boas-Vindas (`WelcomeActivity`):** Tela de apresentação com layouts adaptados para smartphones e tablets (`layout-sw600dp` e `layout-w600dp-land`).
- **Tutorial (`TutorialActivity`):** Carrossel interativo em `ViewPager2` apresentando os benefícios e o fluxo de trabalho do ecossistema Ouros.

### 2. 🔐 Autenticação e Primeiro Acesso
- **Login (`LoginActivity`):** Entrada rápida por e-mail e senha corporativa ou através de código temporário OTP.
- **Primeiro Acesso (`FirstAccessActivity`):** Assistente passo a passo em 4 etapas:
  1. Identificação e dados pessoais do produtor;
  2. Credenciais de acesso;
  3. Definição de senha forte com validação de complexidade;
  4. Tela de boas-vindas e confirmação de vinculação da granja.
- **Recuperação de Senha (`ForgotPasswordActivity`):** Redefinição assistida via código OTP de 6 dígitos.

### 3. 📊 Dashboard Principal (`DashboardFragment`)
- Resumo consolidado do dia para a fazenda e galpões ativos.
- **Medidores de Telemetria:** Consumo de água (m³), relógio de energia (kWh), pesagens recentes e mortalidade diária.
- **Carrossel de Dicas de Manejo (`TipRepository` & `TipDetailActivity`):** Boas práticas agronômicas, conforto térmico e nutrição avícola com leitura enriquecida.

### 4. 📝 Apontamentos e Registros de Campo (`RecordFragment` & `RecordWizardActivity`)
- Assistente em etapas (`RecordStepFragment`) projetado para digitação rápida em galpão:
  - Registro de mortalidade diária;
  - Apontamento do consumo de hidrômetro e padrão elétrico;
  - Pesagem amostral de aves e cálculo de peso médio.
- **Persistência de Rascunhos:** Salvamento automático do identificador do lote (`lotId`) e dos dados em rascunho local para evitar perda de dados durante a jornada do produtor.

### 5. 🎯 Gestão de Metas (`GoalsFragment`)
- Painel visual de acompanhamento de metas operacionais e produtivas.
- Criação de novas metas com seleção dinâmica de tipo (Conversão Alimentar, Mortalidade, Consumo Energético) e validação de parâmetros obrigatórios.
- Barras de progresso com status de conclusão.

### 6. 🏆 Ranking e Ligas Gamificadas (`RankFragment`)
- Posicionamento da granja em ligas competitivas: **Ferro**, **Cobre**, **Bronze**, **Prata** e **Ouro**.
- Cálculo de pontuação zootécnica e exibição de pódio regional de melhores produtores.

### 7. 💡 Simulador Zootécnico (`SimulatorActivity`)
- Calculadora de estimativas zootécnicas para projeção de rendimento de carcaça, custo de conversão alimentar e viabilidade financeira do lote.

### 8. 💬 Assistente Inteligente Midas AI (`ChatbotActivity`)
- Chat interativo com a inteligência artificial agronômica Ouros.
- Renderização de texto formatado e listas em Markdown via biblioteca **Markwon**.
- Tratamento visual de status offline com aviso claro quando a rede estiver indisponível.

### 9. 👤 Perfil do Produtor (`ProfileActivity`)
- Consulta e edição de dados do responsável e fazendas associadas.
- Captura de foto de perfil via câmera ou galeria, corte em proporção 1:1 via **uCrop** e upload para **Cloudinary**.
- Suporte a edição offline com sincronização em fila de fundo via `ProfileOfflineCoordinator`.

### 10. 🔔 Notificações (`NotificationActivity`)
- Central de notificações push recebidas via **Firebase Cloud Messaging (FCM)**.
- Histórico persistido localmente e filtro por mensagens lidas/não lidas.

---

## 🛠️ Tecnologias e Dependências

| Categoria | Biblioteca / Ferramenta | Finalidade |
|---|---|---|
| **Linguagem & Plataforma** | [Kotlin 2.x](https://kotlinlang.org/) / Android SDK 36 (minSdk 33) | Desenvolvimento nativo moderno e performático |
| **Arquitetura & Jetpack** | Lifecycle, ViewModel, Activity KTX, ViewBinding | MVVM reativo e desacoplado |
| **Componentes de UI** | Google Material Design 3 (Material You), ConstraintLayout, ViewPager2, RecyclerView, SwipeRefreshLayout | Interface moderna, responsiva e acessível |
| **Comunicação Assíncrona** | [Kotlinx Coroutines](https://github.com/Kotlin/kotlinx.coroutines) & StateFlow | Concorrência assíncrona não bloqueante |
| **Rede & APIs** | [Retrofit 2.11](https://square.github.io/retrofit/) & [OkHttp 4](https://square.github.io/okhttp/) + Gson | Comunicação REST com microsserviços |
| **Persistência Local** | SQLite Nativo (`OurosOfflineDatabase`) | Cache offline, rascunhos e fila de sincronização |
| **Processamento de Imagem** | [Coil 2.7](https://coil-kt.github.io/coil/) & [uCrop 2.2](https://github.com/Yalantis/uCrop) | Carregamento assíncrono e recorte de avatares |
| **Notificações Push** | [Firebase Cloud Messaging (FCM)](https://firebase.google.com/docs/cloud-messaging) via Firebase BoM 33.7 | Alertas críticos de manejo e avisos |
| **Renderização Markdown** | [Markwon 4.6](https://noties.io/Markwon/) | Formatação de respostas ricas no Chatbot Midas AI |
| **Testes Automatizados** | [JUnit 4](https://junit.org/), [Robolectric 4.14](https://robolectric.org/), Coroutines Test | Testes de unidade e ciclo de vida Android |

---

## 📂 Estrutura do Projeto

```text
ouros-android-app/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ouros/app/
│   │   │   │   ├── OurosApplication.kt       # Inicialização global e registro de sync
│   │   │   │   ├── core/                     # Utilitários globais, formatação e SkeletonLoaderView
│   │   │   │   ├── data/                     # Camada de dados e repositórios
│   │   │   │   │   ├── auth/                 # Sessão, tokens, AuthRepository e TokenAuthenticator
│   │   │   │   │   ├── cloudinary/           # Upload unsigned de fotos de perfil
│   │   │   │   │   ├── dashboard/            # Dados de telemetria e resumo diário
│   │   │   │   │   ├── farmowner/            # Dados do produtor rural e vínculo de granja
│   │   │   │   │   ├── fcm/                  # OurosFirebaseMessagingService
│   │   │   │   │   ├── goals/                # GoalsRepository e regras de criação
│   │   │   │   │   ├── local/                # OurosOfflineDatabase, OfflineStore, OfflineSyncRegistry
│   │   │   │   │   ├── notification/         # NotificationRepository e cache local
│   │   │   │   │   ├── rank/                 # RankRepository e pontuação de ligas
│   │   │   │   │   ├── record/               # RecordRepository e rascunhos de pesagem
│   │   │   │   │   ├── session/              # OfflineStartupPolicy
│   │   │   │   │   ├── simulator/            # SimulatorRepository e projeções zootécnicas
│   │   │   │   │   └── tip/                  # TipRepository e dicas de manejo
│   │   │   │   └── feature/                  # Camada de apresentação por feature
│   │   │   │       ├── auth/                 # Login, Primeiro Acesso, Recuperação de Senha
│   │   │   │       ├── main/                 # MainActivity, Dashboard, Goals, Rank, Record
│   │   │   │       ├── onboarding/           # Splash, Welcome e Tutorial
│   │   │   │       └── secondary/            # Chatbot, Perfil, Notificações, Simulador, Dicas
│   │   │   ├── res/
│   │   │   │   ├── layout/                   # Layouts padrão para smartphones
│   │   │   │   ├── layout-sw600dp/           # Layouts adaptados para tablets (portrait)
│   │   │   │   ├── layout-w600dp-land/       # Layouts adaptados para tablets (landscape)
│   │   │   │   ├── values/                   # Cores, dimensões, estilos e temas Material 3
│   │   │   │   └── values-sw600dp/           # Dimensões e tokens para telas grandes
│   │   │   └── AndroidManifest.xml           # Manifesto com permissões e componentes
│   │   └── test/                             # Suíte de testes unitários com Robolectric
│   └── build.gradle.kts                      # Configuração do módulo Android e plugins
├── gradle/
│   ├── libs.versions.toml                    # Version Catalog centralizado de dependências
│   └── wrapper/                              # Binários do Gradle Wrapper (9.1)
├── build.gradle.kts                          # Configuração raiz do Gradle
├── local.properties.example                  # Template com variáveis de configuração
├── settings.gradle.kts                       # Configuração de repositórios e módulos
└── test-coverage-wsl.sh                      # Script utilitário para relatório de cobertura
```

---

## ⚙️ Requisitos e Configuração de Ambiente

### Pré-requisitos
- **Java Development Kit (JDK):** JDK 17 LTS (obrigatório para o Gradle Daemon e AGP).
- **Android Studio:** Ladybug, Koala, Jellyfish, Hedgehog ou superior.
- **Android SDK:**
  - `compileSdk`: **36**
  - `targetSdk`: **36**
  - `minSdk`: **33** (Android 13 Tiramisu ou superior)

---

## 🔑 Gerenciamento de Segredos (Infisical)

O projeto conta com suporte integrado ao **Infisical** para provisionamento seguro de segredos sem versionar credenciais no Git.

### Opção A: Sincronização Automática via Gradle Task

Preencha as credenciais da Machine Identity no seu arquivo `local.properties`:

```properties
INFISICAL_CLIENT_ID=seu_client_id
INFISICAL_CLIENT_SECRET=seu_client_secret
INFISICAL_PROJECT_ID=seu_project_id
INFISICAL_ENVIRONMENT=dev
INFISICAL_SECRET_PATH=/
```

Em seguida, execute a tarefa personalizada:

```powershell
# No Windows PowerShell:
.\gradlew.bat syncInfisical

# No Linux ou macOS:
./gradlew syncInfisical
```

A tarefa fará login via Universal Auth ou CLI do Infisical e preencherá automaticamente as chaves do aplicativo no `local.properties`.

### Opção B: Configuração Manual em `local.properties`

Caso prefira configurar manualmente, copie o modelo `local.properties.example` para `local.properties` e preencha:

```properties
## Cloudinary (Upload Unsigned de Fotos)
CLOUDINARY_CLOUD_NAME=mpyey2bi
CLOUDINARY_UPLOAD_PRESET=ouros_profile_photos
CLOUDINARY_FOLDER=ouros/profiles

## URLs Base dos Microsserviços
OUROS_API_BASE_URL=https://ms-spring-api.discloud.app/
AUTH_API_BASE_URL=https://ms-auth-service.discloud.app/
DASHBOARD_BASE_URL=https://ouros-dashboard.discloud.app/
```

---

## 🧪 Compilação, Testes e Qualidade

Todos os comandos de build e validação devem ser executados a partir da raiz do repositório:

### Compilação do APK de Debug
```powershell
# Windows
.\gradlew.bat :app:assembleDebug

# Linux / macOS
./gradlew :app:assembleDebug
```

### Execução da Suíte de Testes Unitários
```powershell
# Windows
.\gradlew.bat :app:testDebugUnitTest

# Linux / macOS
./gradlew :app:testDebugUnitTest
```

### Análise Estática de Código (Lint)
```powershell
# Windows
.\gradlew.bat :app:lintDebug

# Linux / macOS
./gradlew :app:lintDebug
```

### Relatório de Cobertura de Testes (WSL / Linux)
```bash
bash test-coverage-wsl.sh
```

---

## 🔗 Integração no Ecossistema Ouros

O aplicativo comunica-se diretamente com os seguintes microsserviços da plataforma Ouros:

- **`ms-spring-api`:** API central de negócios (granjas, lotes avícolas, pesagens, apontamentos diários e metas zootécnicas).
- **`ms-auth-service`:** Microsserviço de autenticação e identidade integrado ao Keycloak (emissão e validação de tokens JWT RS256).
- **`ms-telemetry-dashboard-service`:** Serviço de consolidação de telemetria operacional e observabilidade de IA Midas.
- **`Cloudinary API`:** Armazenamento seguro de fotos de perfil com distribuição global via CDN.
- **`Firebase Cloud Messaging`:** Notificações em tempo real sobre desvios de mortalidade, metas atingidas e comunicados técnicos.

---

## 👥 Contribuidores e Licença

### Principais Contribuidores

<!-- CONTRIBUTORS:START -->
- [@Lucas-Cayres-Porto](https://github.com/Lucas-Cayres-Porto) — Arquitetura de software, offline-first e desenvolvimento móvel
- [@Nicolas25vlad](https://github.com/Nicolas25vlad) — Contribuições em integrações e funcionalidades
<!-- CONTRIBUTORS:END -->

> Atualizado automaticamente pelo workflow de metadados do repositório.

### Licença

Este projeto é distribuído sob a licença **MIT**. Para maiores detalhes, consulte o arquivo [LICENSE](LICENSE).
