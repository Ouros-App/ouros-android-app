# Guia para apresentar o código do Ouros

Este guia acompanha comentários no código-fonte nos pontos em que a intenção ou uma decisão de arquitetura não é óbvia. Ele explica o percurso dos dados e dá uma ordem de apresentação; não substitui a leitura dos arquivos, porque detalhes de telas e regras continuam documentados junto à implementação.

## Como explicar a arquitetura

O aplicativo é um único módulo Android, escrito em Kotlin, com interface em XML. O pacote base é `com.ouros.app`. O código está organizado principalmente por camada e funcionalidade:

- `feature`: telas, componentes visuais, estado de apresentação e navegação.
- `data`: contratos HTTP, conversão de respostas, persistência local, sessão e sincronização.
- `core`: utilitários visuais e regras reutilizáveis, como formatação monetária e tratamento de insets.
- `res`: layouts XML, imagens, cores, dimensões, textos, temas e regras de backup.

Um caminho comum de dados é: usuário toca na tela → Activity/Fragment interpreta o evento visual → ViewModel inicia uma coroutine e atualiza estado → Repository conversa com API ou armazenamento → resultado volta ao ViewModel → tela renderiza estado. Nem todas as áreas antigas seguem esse desenho com a mesma separação; isso pode ser explicado como evolução gradual do projeto.

`OurosApplication` é criado pelo Android uma vez por processo. Ele inicializa a sessão compartilhada e os serviços de sincronização offline. Activities e Fragments representam telas e têm ciclo de vida curto; por isso, estado de tela deve ficar em ViewModels ou stores quando precisa sobreviver a recriações/navegação.

## Percursos principais para demonstrar

### 1. Abertura e autenticação

1. O Android abre `SplashActivity`, configurada como launcher no manifesto.
2. A splash prepara os repositórios e verifica se existe refresh token.
3. Se houver token, tenta renovar a sessão; conforme o resultado, direciona para a área principal, primeiro acesso ou boas-vindas.
4. `LoginActivity` coleta credenciais e código de verificação. `LoginViewModel` mantém o estado da operação; `AuthRepository` implementa as etapas e converte erros em `AuthResult`.
5. `SecureRefreshTokenStore`, `SessionManager`, `BearerInterceptor` e `TokenAuthenticator` cuidam, respectivamente, do refresh token persistido, access token em memória, inclusão do bearer token e tentativa de renovação quando uma chamada autenticada recebe rejeição.

O primeiro acesso é dividido por etapas (`FirstAccessStep`), com `FirstAccessActivity` coordenando o fluxo, Fragmentos mostrando cada formulário, validadores verificando entradas e ViewModel/repositórios executando ações. A recuperação de senha segue padrão semelhante em `ForgotPassword*`. `ChooseActivity` decide entre os caminhos de autenticação.

### 2. Área principal

`MainActivity` hospeda a navegação por destinos definidos em `MainDestination`. `MainViewModel` carrega os dados comuns do perfil e expõe eventos para a Activity. Cada seção tem sua própria interface e estado:

- **Painel:** `DashboardFragment` + `DashboardViewModel` + `DashboardRepository`. A API fornece dados dos gráficos; o repositório valida a resposta e pode usar cache. O Fragment configura os WebViews e apresenta os gráficos recebidos.
- **Metas:** `GoalsFragment`, `GoalsViewModel`, `GoalsRepository` e DTOs. A tela exibe metas pessoais e do estado; o repositório adapta operações à API e às ações pendentes offline.
- **Registros:** `RecordFragment` apresenta o histórico; `RecordWizardActivity` e `RecordStepFragment` coletam dados por etapas. `RecordWizardViewModel` mantém o rascunho e `RecordRepository` coordena leitura, criação, atualização e fila offline. O backend calcula o saldo atual de aves conforme o plano recebido.
- **Ranking:** `RankFragment` carrega os dados com ViewModel/repositório; `RankAdapter` transforma os itens em linhas visuais e atualiza a lista.

Adapters não são donos dos dados de negócio: eles ligam modelos da lista às Views recicláveis. ViewModels preservam estado e lançam operações assíncronas; repositories escondem detalhes de rede e armazenamento.

### 3. Recursos secundários

- **Chatbot:** `ChatbotActivity` trata entrada, anexos, rolagem e eventos de tela; `ChatbotViewModel` controla conversa, carregamento de histórico, envio, prévia/importação e erros; `ChatbotRepository` escolhe o endpoint/contrato e `ChatbotAdapter` renderiza linhas de mensagem, datas, gráficos e indicador de digitação por atualizações incrementais. `ChatbotSessionStore` persiste o identificador da conversa.
- **Perfil:** `ProfileActivity` coleta alterações e controla foto; `ProfileViewModel` executa a atualização; `ProfilePhotoStore` persiste operações pendentes de foto; `ProfileOfflineCoordinator` reenvia essas operações quando a conexão volta.
- **Notificações:** Activity, ViewModel e Adapter exibem páginas, contagem de não lidas e ações; o serviço FCM recebe notificações e `FcmTokenStore` guarda o token do dispositivo.
- **Simulador:** Activity valida e formata campos; ViewModel controla operação/estado; Repository conversa com o endpoint do simulador.
- **Dica:** `TipDetailActivity` e ViewModel apresentam conteúdo e avaliações por meio de `TipRepository`.
- **Onboarding:** Splash, Welcome e Tutorial conduzem introdução e entrada; `TutorialContent`, `TutorialPage` e Adapter fornecem as páginas do tutorial.
- **Erro:** `ErrorActivity` apresenta uma saída de erro/navegação definida pelo fluxo.

### 4. Fluxo offline

`OurosOfflineDatabase` é a persistência SQLite. Ela separa snapshots de leitura (`cache_entries`), escritas a reenviar (`pending_operations`) e associação de IDs temporários aos IDs remotos (`id_mappings`). `OfflineStore` oferece operações de mais alto nível. Cada Repository decide quando ler cache, guardar uma mutação pendente e como interpretar o retorno do servidor.

`OfflineSyncRegistry` observa a conectividade validada e chama cada `OfflineSynchronizer`. O mutex evita sincronizações concorrentes globais. Cada sincronizador conhece sua própria área e sabe como reenviar/remover operações. Essa separação permite que a interface continue usando dados locais, sem colocar regras de transporte dentro das telas.

## Como as chamadas de API são organizadas

Para cada domínio, procure os arquivos com estes papéis:

1. `*Api.kt` ou `*API.kt`: declara rotas, métodos HTTP, parâmetros e corpos Retrofit.
2. `*Dtos.kt`, `*Models.kt` ou modelos equivalentes: representa JSON enviado/recebido.
3. `*Network.kt`: constrói a instância Retrofit e configura serialização/autenticação.
4. `*Repository.kt`: executa chamadas, interpreta códigos/erros e combina API com cache/fila.
5. `*ViewModel.kt`: transforma resultado de dados em estado/evento que a tela entende.

Domínios de dados presentes: `auth`, `chatbot`, `cloudinary`, `dashboard`, `farmowner`, `fcm`, `goals`, `local`, `notification`, `rank`, `record`, `session`, `simulator` e `tip`. O Cloudinary é usado para upload de foto. `AuthenticatedNetwork` compartilha configuração autenticada; alguns serviços têm configuração própria por usar outro host ou autenticação.

## Arquivos core e configurações relevantes

- `BrazilianMoney`: converte valores entre a forma digitada/apresentada no padrão brasileiro e valores numéricos; o TextWatcher mantém a máscara enquanto o usuário digita.
- `ViewInsetsExtensions`: aplica espaçamento para barras do sistema e teclado virtual, evitando conteúdo coberto em telas edge-to-edge.
- `SkeletonLoaderView`: mostra placeholder animado enquanto o conteúdo ainda está carregando.
- `AndroidManifest.xml`: registra permissões, Application, Activities, serviços, receiver e FileProvider. A Splash é a Activity launcher.
- `backup_rules.xml` e `data_extraction_rules.xml`: controlam o que pode ser levado em backup/transferência de aparelho; dados locais de sessão e banco estão excluídos segundo a decisão de segurança adotada.
- `app/build.gradle.kts`, catálogo `gradle/libs.versions.toml` e `settings.gradle.kts`: configuram SDK, plugins, dependências e build.
- `README.md`: requisitos, comandos e estrutura resumida.

Os 122 XML sob `res` são a composição visual e configuração declarativa. `layout/` contém as telas e itens de lista; variantes como `layout-sw600dp` e `layout-w600dp-land` adaptam onboarding/tablet; `values/` define cores, textos, temas e dimensões; `drawable/` define fundos/ícones; `xml/` contém regras de backup e caminhos seguros do FileProvider. Um layout é inflado pela Activity/Fragment que o referencia via `setContentView` ou `R.layout`.

## Roteiro curto de apresentação

1. Comece pelo `AndroidManifest.xml` e mostre qual Activity abre primeiro.
2. Siga `SplashActivity` até a decisão de autenticação e explique tokens/repositório.
3. Mostre `MainActivity` e como ela alterna destinos sem concentrar as regras de cada tela.
4. Escolha uma feature e percorra View → ViewModel → Repository → API/SQLite → volta do estado.
5. Use registros/metas para explicar a fila offline; mostre o SQLite e `OfflineSyncRegistry`.
6. Feche com chatbot e recursos secundários, depois demonstre a adaptação de layout/tablet em `res`.

## Termos úteis

- **DTO:** formato de dados que espelha o contrato JSON da API.
- **Repository:** ponto de acesso aos dados; esconde se vieram da rede ou do armazenamento local.
- **ViewModel:** mantém estado relacionado à tela e executa trabalho assíncrono respeitando o ciclo de vida.
- **Coroutine:** mecanismo Kotlin para operações assíncronas sem bloquear a interface.
- **Adapter:** converte itens de uma coleção em Views reutilizáveis de uma lista.
- **Cache:** cópia local de dados remotos para leitura rápida/offline.
- **Fila offline:** mutações persistidas para envio posterior, quando existir rede validada.
- **Interceptor/Authenticator:** ganchos Retrofit/OkHttp usados para anexar credenciais e lidar com expiração de sessão.

## Escopo desta documentação

O projeto tem muitos arquivos, então os comentários inline foram concentrados em inicialização, autenticação, sincronização e outras decisões cujo motivo não fica evidente só pela sintaxe. Este guia cobre o mapa do fluxo de produção e o papel das áreas/recursos, mas não descreve linha por linha os 17 mil linhas Kotlin e 7 mil linhas XML. Para explicar uma regra específica em detalhe, a melhor referência é o método correspondente no fonte e o contrato do backend relacionado.
