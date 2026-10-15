#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${OUROS_ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
SDK_MANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
TOOLS_ZIP_VERSION="15859902"
TOOLS_ZIP_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"

if ! command -v java >/dev/null 2>&1; then
  echo "Java não encontrado no WSL. Instale um JDK e rode o script novamente." >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y curl unzip
fi

if [[ ! -x "$SDK_MANAGER" ]]; then
  tools_tmp="$(mktemp -d)"
  trap 'rm -rf "$tools_tmp"' EXIT
  curl -fL "https://dl.google.com/android/repository/commandlinetools-linux-${TOOLS_ZIP_VERSION}_latest.zip" \
    -o "$tools_tmp/commandlinetools.zip"
  echo "$TOOLS_ZIP_SHA256  $tools_tmp/commandlinetools.zip" | sha256sum -c -
  mkdir -p "$SDK_ROOT/cmdline-tools"
  unzip -q "$tools_tmp/commandlinetools.zip" -d "$tools_tmp"
  if [[ -e "$SDK_ROOT/cmdline-tools/latest" ]]; then
    echo "SDK tools incompletos em $SDK_ROOT/cmdline-tools/latest; remova essa pasta e rode novamente." >&2
    exit 1
  fi
  mv "$tools_tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

sdkmanager --sdk_root="$SDK_ROOT" --licenses </dev/null || yes | sdkmanager --sdk_root="$SDK_ROOT" --licenses
sdkmanager --sdk_root="$SDK_ROOT" \
  "platform-tools" \
  "platforms;android-36" \
  "build-tools;36.0.0"

cd "$(dirname "$(realpath "$0")")"
bash ./gradlew :app:createDebugUnitTestCoverageReport --no-daemon

echo "Cobertura gerada em app/build/reports/coverage/test/debug/report.xml"
