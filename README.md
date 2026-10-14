# Ouros Android App

Aplicativo Android nativo do Ouros, desenvolvido em Kotlin.

## Requisitos

- Android Studio e Android SDK 36.
- JDK 17 para executar o Gradle Wrapper.

## Build e testes

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Abra a raiz do repositório no Android Studio para executar o app em um dispositivo ou emulador.

## Estrutura

- `app/src/main`: código Kotlin, recursos e manifesto Android.
- `app/src/test`: testes locais.
- `app/src/androidTest`: testes instrumentados.
- `gradle/`: catálogo de dependências e configuração do Gradle Wrapper.

## Licença

Este projeto está sob a licença MIT. Consulte [LICENSE](LICENSE).

## Principais contribuidores

<!-- CONTRIBUTORS:START -->
<!-- CONTRIBUTORS:END -->

> Atualizado automaticamente semanalmente pelo workflow de metadados do README.
