# Mobile Template

<!-- REPO-METADATA:START -->
<div align="center">

[![Repo Size](https://img.shields.io/github/repo-size/Ouros-App/mobile-template?style=flat-square&label=REPO%20SIZE)](https://github.com/Ouros-App/mobile-template)
[![Languages](https://img.shields.io/github/languages/count/Ouros-App/mobile-template?style=flat-square&label=LANGUAGES)](https://github.com/Ouros-App/mobile-template/languages)
[![Forks](https://img.shields.io/github/forks/Ouros-App/mobile-template?style=flat-square&label=FORKS)](https://github.com/Ouros-App/mobile-template/network/members)
[![Issues](https://img.shields.io/github/issues/Ouros-App/mobile-template?style=flat-square&label=ISSUES)](https://github.com/Ouros-App/mobile-template/issues)
[![Pull Requests](https://img.shields.io/github/issues-pr/Ouros-App/mobile-template?style=flat-square&label=PULL%20REQUESTS)](https://github.com/Ouros-App/mobile-template/pulls)

</div>
<!-- REPO-METADATA:END -->

Template de aplicativo Android nativo em Kotlin e XML, preparado para gerar novos projetos a partir de valores de configuração.

## Status e escopo

Este repositório é um template parametrizado. Os arquivos usam placeholders como ouros-android-app, ouros android app, com.ourosapp.ourosandroidapp, com/ourosapp/ourosandroidapp e OurosAndroidAppApplication; o script de inicialização substitui esses valores em uma cópia de saída.

## Principais componentes

- Módulo Android app com MainActivity, LoginActivity, HomeFragment, MainViewModel, MainRepository, ApiClient, AppTheme e a classe de aplicação parametrizada.
- ViewBinding habilitado.
- Dependências AndroidX Core KTX, AppCompat, Material e ConstraintLayout.
- Layout inicial activity_main.xml e recursos de valores, tema, tema noturno e drawables/mipmap.
- Exemplos de teste local em app/src/test e de teste instrumentado em app/src/androidTest.
- Gradle Wrapper para execução do projeto sem versionar uma instalação local do Gradle.

## Pré-requisitos

- Android Studio e Android SDK.
- O projeto está configurado para compilar e direcionar para SDK 36, com minSdk 33.
- A configuração de compilação usa compatibilidade com Java 11.
- Para executar scripts/init-template.sh, é necessário um shell Bash com rsync e perl disponíveis.

## Configuração do template

Copie o arquivo de configuração de exemplo e edite os valores:

~~~bash
cp template.config.example.json template.config.json
~~~

O arquivo template.config.json deve fornecer:

| Campo | Finalidade |
| --- | --- |
| projectName | Nome do projeto gerado. |
| appLabel | Nome exibido pelo aplicativo. |
| packageName | Nome do pacote Android. |
| applicationClassName | Nome da classe de aplicação. |

Para abrir o projeto no Android Studio, copie também local.properties.example para local.properties e ajuste sdk.dir para o caminho do Android SDK local:

~~~bash
cp local.properties.example local.properties
~~~

local.properties é local e não deve ser versionado.

## Geração e uso

Com template.config.json preenchido, execute:

~~~bash
bash scripts/init-template.sh
~~~

O script aceita opcionalmente o caminho do arquivo de configuração como primeiro argumento e o diretório de saída como segundo argumento:

~~~bash
bash scripts/init-template.sh caminho/para/template.config.json caminho/para/saida
~~~

Sem o segundo argumento, a saída é criada em out/<projectName>. A cópia gerada remove os arquivos e diretórios exclusivos de inicialização do template, substitui os placeholders, reorganiza o diretório do pacote e renomeia a classe de aplicação. Ao executar novamente para o mesmo destino, o diretório de saída é removido e recriado.

Depois da geração, abra o diretório de saída no Android Studio. O repositório fornece os wrappers gradlew e gradlew.bat.

## Testes e qualidade

Há um teste unitário de exemplo em app/src/test e um teste instrumentado de exemplo em app/src/androidTest. Não há workflow de CI configurado no diretório .github/workflows; a pasta .github contém apenas o template de pull request.

## Estrutura do projeto

~~~text
app/
  src/main/
    AndroidManifest.xml
    java/com/ourosapp/ourosandroidapp/
    res/
  src/test/
  src/androidTest/
  build.gradle.kts
gradle/
  libs.versions.toml
scripts/
  init-template.sh
template.config.example.json
local.properties.example
gradlew
gradlew.bat
~~~

## Contribuição

Preserve os placeholders e o fluxo de geração ao alterar o template. Mudanças no projeto gerado devem ser refletidas na documentação e nos exemplos correspondentes.

## Licença

Este projeto está sob a licença MIT. Consulte LICENSE para o texto completo.


## Principais contribuidores

<!-- CONTRIBUTORS:START -->
- [@Nicolas25vlad](https://github.com/Nicolas25vlad) — 12 contribuições
<!-- CONTRIBUTORS:END -->

> Atualizado automaticamente semanalmente pelo workflow de metadados do README.
